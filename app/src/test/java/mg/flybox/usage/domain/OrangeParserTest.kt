package mg.flybox.usage.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrangeParserTest {
    private val page = """
        <html><head><style>.a{color:red}</style><script>var x = "validité Fake 1 Go sur 2 Go valable jusqu'au 01/01/2030";</script></head>
        <body><div class="c"><h2>Votre consommation</h2>
        <p>Offre en cours validit&eacute; <b>Forfait Mega</b> Internet 12,5 Go sur 60 Go valable jusqu'au 14/12/2026 &agrave; 23:59:59</p>
        <p>Le solde de votre compte de rechargement est de 12 500 Ar</p></div></body></html>
    """.trimIndent()

    @Test fun parsesSinglePlan() {
        val p = OrangeParser.parse(page)!!
        assertEquals(12.5, p.remainingGo, 1e-9)
        assertEquals(60.0, p.totalGo, 1e-9)
        assertEquals(LocalDate.of(2026, 12, 14), p.expires)
        assertEquals("Forfait Mega", p.plan)
        assertEquals(12_500L, p.balanceAr)
    }

    @Test fun sumsSeveralPlansAndUsesEarliestExpiry() {
        val html = """
            <p>validité Forfait A 10,0 Go sur 20 Go valable jusqu'au 05/12/2026</p>
            <p>validité Bonus 3.5 Go sur 5 Go valable jusqu'au 20/11/2026</p>
        """.trimIndent()
        val p = OrangeParser.parse(html)!!
        assertEquals(13.5, p.remainingGo, 1e-9)
        assertEquals(25.0, p.totalGo, 1e-9)
        assertEquals(LocalDate.of(2026, 11, 20), p.expires)
        assertEquals("Forfait A + Bonus", p.plan)
        assertNull(p.balanceAr)
    }

    @Test fun acceptsTypographicApostrophe() {
        val html = "validité Pass 1 Go sur 2 Go valable jusqu’au 02/03/2027"
        assertEquals(LocalDate.of(2027, 3, 2), OrangeParser.parse(html)!!.expires)
    }

    @Test fun layoutChangeReturnsNull() {
        assertNull(OrangeParser.parse("<html><body>Bienvenue. Aucun forfait ici.</body></html>"))
        assertNull(OrangeParser.parse(""))
    }

    @Test fun scriptContentIsIgnored() {
        val html = "<script>validité Fake 1 Go sur 2 Go valable jusqu'au 01/01/2030</script><p>rien</p>"
        assertNull(OrangeParser.parse(html))
    }

    @Test fun decodesEntities() {
        assertEquals("validité & é <", OrangeParser.decodeEntities("validit&eacute; &amp; &#233; &lt;"))
    }

    @Test fun parsesRealPageExcerpt() {
        // Extrait réel de la page 123.orange.mg/info-conso (texte relevé le 01/11/2026).
        val html = "<div>Vous êtes sur l’offre WiFiber . Le solde de votre compte de rechargement est de 0 Ar .</div>" +
            "<h3>Forfaits en cours de validité</h3><p>Wifiber Go+ Internet 248.56 Go sur 250 Go valable jusqu'au 30/11/2026 à 23h59min59s</p>"
        val p = OrangeParser.parse(html)!!
        assertEquals(248.56, p.remainingGo, 1e-9)
        assertEquals(250.0, p.totalGo, 1e-9)
        assertEquals(LocalDate.of(2026, 11, 30), p.expires)
        assertEquals("Wifiber Go+", p.plan)
        assertEquals(0L, p.balanceAr)
    }

    @Test fun headingIsNotPartOfPlanName() {
        val html = "<h3>Forfaits en cours de validité</h3><p>Wifiber Go+ Internet 249.3 Go sur 250 Go valable jusqu'au 30/11/2026 à 23h59min59s</p>"
        val p = OrangeParser.parse(html)!!
        assertEquals("Wifiber Go+", p.plan)
        assertEquals(249.3, p.remainingGo, 1e-9)
    }

    @Test fun plainPatternWithoutPrefixOrTime() {
        val p = OrangeParser.parse("<div>Pass Mega 12,5 Go sur 60 Go valable jusqu'au 14/12/2026</div>")!!
        assertEquals("Pass Mega", p.plan)
        assertEquals(LocalDate.of(2026, 12, 14), p.expires)
    }

    @Test fun severalPlansWithoutPrefix() {
        val html = "<p>Forfait A 10 Go sur 20 Go valable jusqu'au 05/12/2026 à 23:59:59</p><p>Bonus 3,5 Go sur 5 Go valable jusqu'au 20/11/2026 à 23:59:59</p>"
        val p = OrangeParser.parse(html)!!
        assertEquals("Forfait A + Bonus", p.plan)
        assertEquals(13.5, p.remainingGo, 1e-9)
        assertEquals(LocalDate.of(2026, 11, 20), p.expires)
    }
}
