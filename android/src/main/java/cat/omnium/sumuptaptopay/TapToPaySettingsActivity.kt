package cat.omnium.sumuptaptopay

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Pantalla nativa per desar el token de SumUp i l'affiliate key.
 *
 * El token no surt mai del dispositiu: no viatja al bundle web ni a la capa JS.
 */
class TapToPaySettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tap_to_pay_settings)

        val prefs = getSharedPreferences(Prefs.FILE, MODE_PRIVATE)
        val tokenInput = findViewById<EditText>(R.id.token_input)
        val affiliateInput = findViewById<EditText>(R.id.affiliate_input)

        tokenInput.setText(prefs.getString(Prefs.KEY_TOKEN, null).orEmpty())
        affiliateInput.setText(prefs.getString(Prefs.KEY_AFFILIATE, Prefs.DEFAULT_AFFILIATE))

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
}
