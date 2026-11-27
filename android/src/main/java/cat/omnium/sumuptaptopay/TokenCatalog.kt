package cat.omnium.sumuptaptopay

/** Un token de SumUp amb el títol definit a la variable d'entorn. */
data class SumUpToken(val title: String, val token: String)

/**
 * Catàleg de tokens de prova definit a `TOKENS_SUMUP_SECRET_KEY`.
 *
 * Format: `títol:token|títol:token`. Els separadors (`:` i `|`) no formen
 * part del joc de caràcters d'un token de SumUp, així que no poden aparèixer
 * dins del token. Les entrades mal formades s'ignoren.
 */
object TokenCatalog {
    private const val ENTRY_SEPARATOR = '|'
    private const val TITLE_SEPARATOR = ':'
    private val TOKEN_PATTERN = Regex("^sup_sk_[A-Za-z0-9]+$")

    fun parse(raw: String?): List<SumUpToken> =
        raw.orEmpty()
            .split(ENTRY_SEPARATOR)
            .mapNotNull { entry ->
                val trimmed = entry.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                val separator = trimmed.indexOf(TITLE_SEPARATOR)
                if (separator <= 0) return@mapNotNull null
                val title = trimmed.substring(0, separator).trim()
                val token = trimmed.substring(separator + 1).trim()
                if (title.isEmpty() || !TOKEN_PATTERN.matches(token)) return@mapNotNull null
                SumUpToken(title, token)
            }
}
