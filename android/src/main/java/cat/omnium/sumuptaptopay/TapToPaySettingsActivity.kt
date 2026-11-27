package cat.omnium.sumuptaptopay

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Pantalla nativa per desar el token de SumUp i l'affiliate key.
 *
 * Els tokens definits a `TOKENS_SUMUP_SECRET_KEY` es mostren com una llista;
 * també es pot escriure el token manualment. El token no surt mai del
 * dispositiu: no viatja al bundle web ni a la capa JS.
 */
class TapToPaySettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tap_to_pay_settings)

        val prefs = getSharedPreferences(Prefs.FILE, MODE_PRIVATE)
        val tokenInput = findViewById<EditText>(R.id.token_input)
        val affiliateInput = findViewById<EditText>(R.id.affiliate_input)
        val tokensTitle = findViewById<TextView>(R.id.tokens_title)
        val tokensGroup = findViewById<RadioGroup>(R.id.tokens_group)

        val savedToken = prefs.getString(Prefs.KEY_TOKEN, null).orEmpty()
        tokenInput.setText(savedToken)
        affiliateInput.setText(prefs.getString(Prefs.KEY_AFFILIATE, Prefs.DEFAULT_AFFILIATE))
        configureTokenChoices(tokensTitle, tokensGroup, tokenInput, savedToken)

        findViewById<Button>(R.id.cancel_button).setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        findViewById<Button>(R.id.save_button).setOnClickListener {
            val token = tokenInput.text.toString().trim()
            val affiliate = affiliateInput.text.toString().trim()
            if (token.isEmpty()) {
                Toast.makeText(this, "El token de SumUp no pot estar buit", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            prefs.edit()
                .putString(Prefs.KEY_TOKEN, token)
                .putString(
                    Prefs.KEY_AFFILIATE,
                    if (affiliate.isEmpty()) Prefs.DEFAULT_AFFILIATE else affiliate,
                )
                .apply()
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    /**
     * Mostra els tokens del catàleg i una opció per escriure'n un a mà.
     * Si el token desat coincideix amb el catàleg, se selecciona la seva
     * entrada; si no, s'activa l'opció manual.
     */
    private fun configureTokenChoices(
        tokensTitle: TextView,
        tokensGroup: RadioGroup,
        tokenInput: EditText,
        savedToken: String,
    ) {
        val choices = TokenCatalog.parse(BuildConfig.TOKENS_SUMUP_SECRET_KEY)
        if (choices.isEmpty()) return

        tokensTitle.visibility = View.VISIBLE
        tokensGroup.visibility = View.VISIBLE

        var selectedId: Int? = null
        choices.forEach { choice ->
            val radio = RadioButton(this).apply {
                id = View.generateViewId()
                text = choice.title
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        tokenInput.setText(choice.token)
                        tokenInput.isEnabled = false
                    }
                }
            }
            tokensGroup.addView(radio)
            if (choice.token == savedToken) selectedId = radio.id
        }

        val manual = RadioButton(this).apply {
            id = View.generateViewId()
            text = getString(R.string.token_manual)
            setOnCheckedChangeListener { _, checked ->
                if (checked) tokenInput.isEnabled = true
            }
        }
        tokensGroup.addView(manual)

        tokensGroup.check(selectedId ?: manual.id)
    }
}
