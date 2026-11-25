package cat.omnium.sumuptaptopay

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResult
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.ActivityCallback
import com.getcapacitor.annotation.CapacitorPlugin
import com.sumup.taptopay.TapToPay
import com.sumup.taptopay.TapToPayApiProvider
import com.sumup.taptopay.auth.AuthTokenProvider
import com.sumup.taptopay.exception.api.TapToPayException
import com.sumup.taptopay.payment.domain.model.api.AffiliateModel
import com.sumup.taptopay.payment.domain.model.api.CheckoutData
import com.sumup.taptopay.payment.domain.model.api.PaymentEvent
import com.sumup.taptopay.payment.domain.model.api.PaymentOutput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

@CapacitorPlugin(name = "SumUpTapToPay")
class SumUpTapToPayPlugin : Plugin() {

    private var tapToPay: TapToPay? = null
    private var initialized = false
    private var initJob: Job? = null
    private var paymentJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val prefs
        get() = getContext().getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)

    @PluginMethod
    fun isConfigured(call: PluginCall) {
        val token = prefs.getString(Prefs.KEY_TOKEN, null)
        call.resolve(
            JSObject()
                .put("configured", !token.isNullOrBlank())
                .put("affiliateKey", prefs.getString(Prefs.KEY_AFFILIATE, Prefs.DEFAULT_AFFILIATE)),
        )
    }

    @PluginMethod
    fun openConfiguration(call: PluginCall) {
        startActivityForResult(
            call,
            Intent(getContext(), TapToPaySettingsActivity::class.java),
            "handleSettingsResult",
        )
    }

    @ActivityCallback
    fun handleSettingsResult(call: PluginCall?, result: ActivityResult?) {
        if (call == null) return
        val saved = result?.resultCode == Activity.RESULT_OK
        if (!saved) {
            call.resolve(JSObject().put("saved", false))
            return
        }
        // El token pot haver canviat: cal tancar l'SDK abans de tornar-lo a inicialitzar.
        paymentJob?.cancel()
        val sdk = tapToPay
        if (sdk == null) {
            initialized = false
            call.resolve(JSObject().put("saved", true))
            return
        }
        scope.launch {
            sdk.tearDown()
            initialized = false
            call.resolve(JSObject().put("saved", true))
        }
    }

    @PluginMethod
    fun initialize(call: PluginCall) {
        val token = prefs.getString(Prefs.KEY_TOKEN, null)?.takeIf { it.isNotBlank() }
            ?: return call.reject("Cal configurar el token de SumUp", "NOT_CONFIGURED")
        if (initialized) return call.resolve(JSObject().put("ready", true))
        val sdk = tapToPay ?: TapToPayApiProvider.provide(getContext().applicationContext).also {
            tapToPay = it
        }
        initJob = scope.launch {
            sdk.init(
                object : AuthTokenProvider {
                    override fun getAccessToken(): String = token
                },
            )
                .onSuccess {
                    initialized = true
                    call.resolve(JSObject().put("ready", true))
                }
                .onFailure {
                    initialized = false
                    call.reject(
                        it.message ?: "No s'ha pogut inicialitzar el Tap to Pay",
                        "INIT_FAILED",
                        errorJs(it),
                    )
                }
        }
    }

    @PluginMethod
    fun startPayment(call: PluginCall) {
        val sdk = tapToPay
        if (sdk == null || !initialized) {
            return call.reject("Cal cridar initialize() abans", "SDK_NOT_INITIALIZED")
        }
        if (paymentJob?.isActive == true) {
            return call.reject("Ja hi ha un pagament en curs", "PAYMENT_IN_PROGRESS")
        }
        val total = call.getLong("total") ?: return call.reject("Falta el total", "INVALID_AMOUNT")
        if (total <= 0L) {
            return call.reject("El total ha de ser positiu (cèntims)", "INVALID_AMOUNT")
        }
        val affiliate = prefs.getString(Prefs.KEY_AFFILIATE, Prefs.DEFAULT_AFFILIATE)
            ?.takeIf { it.isNotBlank() } ?: Prefs.DEFAULT_AFFILIATE
        val ftid = call.getString("foreignTransactionId")?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()
        val checkout = CheckoutData(
            totalAmount = total,
            tipsAmount = null,
            vatAmount = null,
            clientUniqueTransactionId = UUID.randomUUID().toString(),
            customItems = null,
            priceItems = null,
            products = null,
            processCardAs = null,
            affiliateData = AffiliateModel(affiliate, ftid, null),
        )
        val skipSuccess = call.getBoolean("skipSuccessScreen", true) ?: true
        val timeout = call.getInt("timeoutCardWaitSeconds")

        paymentJob = scope.launch {
            var done = false
            try {
                sdk.startPayment(checkout, skipSuccess, timeout).collect { event ->
                    notifyListeners("paymentEvent", eventJs(event))
                    if (done) return@collect
                    when (event) {
                        is PaymentEvent.TransactionDone -> {
                            done = true
                            call.resolve(resultJs(1, "Pagament completat", ftid, event.paymentOutput, null))
                        }
                        is PaymentEvent.TransactionFailed -> {
                            done = true
                            val ex: TapToPayException? = event.tapToPayException
                            call.resolve(
                                resultJs(
                                    2,
                                    ex?.message ?: "Pagament rebutjat",
                                    ftid,
                                    event.paymentOutput,
                                    ex?.code,
                                ),
                            )
                        }
                        is PaymentEvent.TransactionCanceled -> {
                            done = true
                            call.resolve(resultJs(3, "Pagament cancel·lat", ftid, event.paymentOutput, null))
                        }
                        is PaymentEvent.TransactionResultUnknown -> {
                            done = true
                            call.resolve(
                                resultJs(15, "Resultat desconegut de la transacció", ftid, event.paymentOutput, null),
                            )
                        }
                        else -> Unit
                    }
                }
                if (!done) {
                    call.reject("El flux de pagament s'ha tancat sense resultat", "NO_RESULT")
                }
            } catch (e: Throwable) {
                if (!done) {
                    call.reject(e.message ?: "Error de pagament", "PAYMENT_ERROR", errorJs(e))
                }
            } finally {
                paymentJob = null
            }
        }
    }

    @PluginMethod
    fun tearDown(call: PluginCall) {
        val sdk = tapToPay
        paymentJob?.cancel()
        if (sdk == null) {
            initialized = false
            return call.resolve()
        }
        scope.launch {
            sdk.tearDown()
            initialized = false
            call.resolve()
        }
    }

    override fun handleOnDestroy() {
        paymentJob?.cancel()
        initJob?.cancel()
        scope.cancel()
    }

    private fun errorJs(e: Throwable): JSObject =
        JSObject()
            .put("message", e.message ?: "")
            .put("tapToPayCode", (e as? TapToPayException)?.code ?: 0)

    private fun resultJs(
        code: Int,
        message: String,
        ftid: String,
        output: PaymentOutput?,
        tapToPayCode: Int?,
    ): JSObject =
        JSObject()
            .put("code", code)
            .put("message", message)
            .put("foreignTransactionId", ftid)
            .put("tapToPayCode", tapToPayCode ?: 0)
            .putNullable("txCode", output?.txCode)
            .putNullable("serverTransactionId", output?.serverTransactionId)
            .putNullable("cardType", output?.cardType)
            .putNullable("lastFour", output?.lastFour)
            .putNullable("merchantCode", output?.merchantCode)
            .putNullable("cardScheme", output?.cardScheme)

    private fun eventJs(event: PaymentEvent): JSObject {
        val js = JSObject().put("type", event::class.java.simpleName)
        when (event) {
            is PaymentEvent.TransactionDone -> js.put("output", outputJs(event.paymentOutput))
            is PaymentEvent.TransactionFailed -> {
                val ex: TapToPayException? = event.tapToPayException
                js.put("output", outputJs(event.paymentOutput))
                js.put("tapToPayCode", ex?.code ?: 0)
                js.put("message", ex?.message ?: "")
            }
            is PaymentEvent.TransactionCanceled -> js.put("output", outputJs(event.paymentOutput))
            is PaymentEvent.TransactionResultUnknown -> js.put("output", outputJs(event.paymentOutput))
            else -> Unit
        }
        return js
    }

    private fun outputJs(output: PaymentOutput?): JSObject? {
        if (output == null) return null
        return JSObject()
            .put("txCode", output.txCode)
            .put("serverTransactionId", output.serverTransactionId)
            .putNullable("cardType", output.cardType)
            .putNullable("lastFour", output.lastFour)
            .putNullable("merchantCode", output.merchantCode)
            .putNullable("cardScheme", output.cardScheme)
    }

    private fun JSObject.putNullable(key: String, value: Any?): JSObject =
        put(key, value ?: JSONObject.NULL)
}
