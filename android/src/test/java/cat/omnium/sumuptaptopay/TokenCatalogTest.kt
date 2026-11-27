package cat.omnium.sumuptaptopay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenCatalogTest {
    @Test
    fun parsejaTitolsITokens() {
        val tokens = TokenCatalog.parse(" prod : sup_sk_aaa | dev:sup_sk_bbb ")

        assertEquals(
            listOf(
                SumUpToken("prod", "sup_sk_aaa"),
                SumUpToken("dev", "sup_sk_bbb"),
            ),
            tokens,
        )
    }

    @Test
    fun ignoraEntradesMalFormades() {
        val tokens = TokenCatalog.parse(
            "sense-separador|:sup_sk_bbb|prod:no-és-un-token|dev:sup_sk_ccc|",
        )

        assertEquals(listOf(SumUpToken("dev", "sup_sk_ccc")), tokens)
    }

    @Test
    fun elsSeparadorsNoPodenAnarDinsDelToken() {
        val tokens = TokenCatalog.parse("prod:sup_sk_aaa|dev:sup_sk_bbb:extra")

        assertEquals(listOf(SumUpToken("prod", "sup_sk_aaa")), tokens)
    }

    @Test
    fun unaVariableBuidaNoTeTokens() {
        assertTrue(TokenCatalog.parse(null).isEmpty())
        assertTrue(TokenCatalog.parse("").isEmpty())
        assertTrue(TokenCatalog.parse("  |  ").isEmpty())
    }
}
