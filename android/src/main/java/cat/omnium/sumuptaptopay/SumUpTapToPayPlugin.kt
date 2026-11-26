package cat.omnium.sumuptaptopay

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResult
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.PermissionState
import com.getcapacitor.annotation.ActivityCallback
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback
import com.sumup.taptopay.TapToPay
import com.sumup.taptopay.TapToPayApiProvider
import com.sumup.taptopay.auth.AuthTokenProvider
import com.sumup.taptopay.exception.api.TapToPayException
import com.sumup.taptopay.payment.domain.model.api.AffiliateModel
import com.sumup.taptopay.payment.domain.model.api.CheckoutData
import com.sumup.taptopay.payment.domain.model.api.PaymentEvent
import com.sumup.taptopay.payment.domain.model.api.PaymentOutput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

/**
 * L'SDK de SumUp exigeix càmera (llegir la targeta) i ubicació (seguretat de la
 * transacció) en temps d'execució. L'SDK només les declara al manifest, així
 * que és el plugin qui les ha de demanar.
 */
@CapacitorPlugin(
    name = "SumUpTapToPay",
    permissions = [
        Permission(
            alias = "camera",
            strings = ["android.permission.CAMERA"],
        ),
        Permission(
            alias = "location",
            strings = ["android.permission.ACCESS_COARSE_LOCATION"],
        ),
    ],
)
class SumUpTapToPayPlugin : Plugin() {
    companion object {
        private const val CAMERA_ALIAS = "camera"
        private const val LOCATION_ALIAS = "location"
    }

    private var tapToPay: TapToPay? = null
    private var initialized = false
    private var initJob: Job? = null
    private var paymentJob: Job? = null
    private var pendingPaymentCall: PluginCall? = null
    private var paymentUiShown = false
    private var cancelWatchdogJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val prefs
        get() = getContext().getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)

    /** Token desat al dispositiu; null si encara no s'ha configurat. */
    private val token: String?
        get() = prefs.getString(Prefs.KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    @PluginMethod
    fun isConfigured(call: PluginCall) {
        call.resolve(
            JSObject()
                .put("configured", !token.isNullOrBlank())
                .put("affiliateKey", prefs.getString(Prefs.KEY_AFFILIATE, Prefs.DEFAULT_AFFILIATE)),
        )
    }

    /** Indica si l'SDK ja pot llegir la targeta (càmera i ubicació concedides). */
    private fun hasPaymentPermissions(): Boolean =
        getPermissionState(CAMERA_ALIAS) == PermissionState.GRANTED &&
            getPermissionState(LOCATION_ALIAS) == PermissionState.GRANTED

    @PluginMethod
    fun ensurePermissions(call: PluginCall) {
        if (hasPaymentPermissions()) return resolvePermissions(call)
        requestPermissionForAliases(
            arrayOf(CAMERA_ALIAS, LOCATION_ALIAS),
            call,
            "checkPaymentPermissions",
        )
    }

    @PermissionCallback
    private fun checkPaymentPermissions(call: PluginCall) {
        if (hasPaymentPermissions()) {
            resolvePermissions(call)
        } else {
            call.reject(
                "Cal permetre la càmera i la ubicació per cobrar amb el mòbil",
                "PERMISSIONS_DENIED",
            )
        }
    }

    private fun resolvePermissions(call: PluginCall) {
        call.resolve(JSObject().put("granted", true))
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
        if (initialized) return call.resolve(JSObject().put("ready", true))
        val accessToken = token
            ?: return call.reject("Cal configurar el token de SumUp", "NOT_CONFIGURED")
        val sdk = tapToPay ?: TapToPayApiProvider.provide(getContext().applicationContext).also {
            tapToPay = it
        }
        initJob = scope.launch {
            sdk.init(
                object : AuthTokenProvider {
                    override fun getAccessToken(): String = accessToken
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
        val total = readCents(call, "total") ?: return call.reject("Falta el total", "INVALID_AMOUNT")
        if (total <= 0L) {
            return call.reject("El total ha de ser positiu (cèntims)", "INVALID_AMOUNT")
        }
        if (!hasPaymentPermissions()) {
            return requestPermissionForAliases(
                arrayOf(CAMERA_ALIAS, LOCATION_ALIAS),
                call,
                "startPaymentWithPermissions",
            )
        }
        launchPayment(sdk, call, total)
    }

    @PermissionCallback
    private fun startPaymentWithPermissions(call: PluginCall) {
        val total = readCents(call, "total")
        val sdk = tapToPay
        if (sdk == null || total == null || !hasPaymentPermissions()) {
            return call.reject(
                "Cal permetre la càmera i la ubicació per cobrar amb el mòbil",
                "PERMISSIONS_DENIED",
            )
        }
        launchPayment(sdk, call, total)
    }

    private fun launchPayment(sdk: TapToPay, call: PluginCall, total: Long) {
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

        paymentUiShown = false
        pendingPaymentCall = call
        paymentJob = scope.launch {
            var done = false
            fun finish(code: Int, message: String, output: PaymentOutput?, tapToPayCode: Int?) {
                if (done) return
                done = true
                pendingPaymentCall = null
                call.resolve(resultJs(code, message, ftid, output, tapToPayCode))
            }
            try {
                sdk.startPayment(checkout, skipSuccess, timeout).collect { event ->
                    notifyListeners("paymentEvent", eventJs(event))
                    if (done) return@collect
                    when (event) {
                        is PaymentEvent.TransactionDone ->
                            finish(1, "Pagament completat", event.paymentOutput, null)
                        is PaymentEvent.TransactionFailed -> {
                            val ex: TapToPayException? = event.tapToPayException
                            finish(2, ex?.message ?: "Pagament rebutjat", event.paymentOutput, ex?.code)
                        }
                        is PaymentEvent.TransactionCanceled ->
                            finish(3, "Pagament cancel·lat", event.paymentOutput, null)
                        is PaymentEvent.TransactionResultUnknown ->
                            finish(15, "Resultat desconegut de la transacció", event.paymentOutput, null)
                        else -> Unit
                    }
                }
                if (!done) {
                    done = true
                    pendingPaymentCall = null
                    call.reject("El flux de pagament s'ha tancat sense resultat", "NO_RESULT")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (!done) {
                    done = true
                    pendingPaymentCall = null
                    call.reject(e.message ?: "Error de pagament", "PAYMENT_ERROR", errorJs(e))
                }
            } finally {
                pendingPaymentCall = null
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

    /**
     * L'SDK obre la seva PaymentActivity en un altre procés. Quan es tanca
     * (cancel·lada o acabada) la nostra activitat reprèn; si en 2,5 s no ha
     * arribat cap esdeveniment terminal, donem el pagament per cancel·lat.
     * Sense això, tancar la pantalla de l'SDK deixa la promesa penjada.
     */
    override fun handleOnPause() {
        if (paymentJob?.isActive == true) paymentUiShown = true
    }

    override fun handleOnResume() {
        if (!paymentUiShown) return
        paymentUiShown = false
        val job = paymentJob ?: return
        cancelWatchdogJob?.cancel()
        cancelWatchdogJob = scope.launch {
            delay(2500)
            val pending = pendingPaymentCall
            if (job.isActive && pending != null) {
                pendingPaymentCall = null
                pending.reject("Pagament cancel·lat", "PAYMENT_CANCELED")
                job.cancel()
            }
        }
    }

    override fun handleOnDestroy() {
        cancelWatchdogJob?.cancel()
        paymentJob?.cancel()
        initJob?.cancel()
        scope.cancel()
    }

    /**
     * Llegeix un import en cèntims enviat des de JS.
     *
     * `PluginCall.getLong` només accepta un `java.lang.Long`, però el pont de
     * Capacitor desserialitza els enters de JSON com a `Integer`, així que
     * rebutja imports vàlids. Aquí s'accepta qualsevol número o cadena numèrica.
     */
    private fun readCents(call: PluginCall, key: String): Long? =
        when (val value = call.data.opt(key)) {
            is Number -> value.toLong()
            is String -> value.trim().toDoubleOrNull()?.toLong()
            else -> null
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
