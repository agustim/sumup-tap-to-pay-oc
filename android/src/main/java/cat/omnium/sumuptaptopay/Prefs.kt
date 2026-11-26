package cat.omnium.sumuptaptopay

object Prefs {
    const val FILE = "sumup_tap_to_pay_oc"
    const val KEY_TOKEN = "sumup_token"
    const val KEY_AFFILIATE = "sumup_affiliate_key"

    // L'affiliate key identifica la integració (no és un secret). El token
    // de SumUp NO es versiona: es desa al dispositiu des de la pantalla de
    // configuració.
    const val DEFAULT_AFFILIATE = "sup_afk_HkJwRDUwJeMWBzVQI64sNkMSOpPL9U6a"
}
