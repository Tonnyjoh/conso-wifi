package mg.flybox.usage.acceptance

import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import mg.flybox.usage.data.net.OrangeClient
import mg.flybox.usage.domain.OrangeParser
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

/** BR-3, EC-2, AC-4 (plan data), AC-13 (Orange unreachable). */
class OrangeAcceptanceTest {
    @Test fun `BR-3 single plan, spec text with validite prefix as on the reference page`() {
        val p = OrangeParser.parse("<p>validité Forfait Mega 12,5 Go sur 60 Go valable jusqu'au 14/12/2026 à 23:59:59</p>")!!
        assertEquals(12.5, p.remainingGo, 1e-9)
        assertEquals(60.0, p.totalGo, 1e-9)
        assertEquals(LocalDate.of(2026, 12, 14), p.expires)
    }

    @Test fun `BR-3 plan text exactly as in the API section, without any prefix`() {
        val p = OrangeParser.parse("<div>Pass Mega 12,5 Go sur 60 Go valable jusqu'au 14/12/2026 à 23:59:59</div>")
        assertNotNull("spec pattern '<plan name> X Go sur Y Go valable jusqu'au dd/mm/yyyy' not matched", p)
    }

    @Test fun `BR-3 several plans are summed and earliest expiry used`() {
        val html = "<p>validité A 10 Go sur 20 Go valable jusqu'au 05/12/2026</p><p>validité B 3,5 Go sur 5 Go valable jusqu'au 20/11/2026</p>"
        val p = OrangeParser.parse(html)!!
        assertEquals(13.5, p.remainingGo, 1e-9)
        assertEquals(25.0, p.totalGo, 1e-9)
        assertEquals(LocalDate.of(2026, 11, 20), p.expires)
    }

    @Test fun `AC-4 balance line is parsed`() {
        val html = "<p>validité A 1 Go sur 2 Go valable jusqu'au 05/12/2026</p><p>Le solde de votre compte de rechargement est de 5000 Ar</p>"
        assertEquals(5000L, OrangeParser.parse(html)!!.balanceAr)
    }

    @Test fun `EC-2 layout change gives null, not an exception`() {
        assertNull(OrangeParser.parse("<html><body><h1>Bienvenue</h1><p>Nouvelle mise en page</p></body></html>"))
        assertNull(OrangeParser.parse(""))
    }

    @Test fun `EC-2 partial garbage does not crash`() {
        assertNull(OrangeParser.parse("validité Pass Go sur Go valable jusqu'au //"))
    }

    @Test fun `AC-13 Orange page unreachable raises IOException for the caller to keep last values`() {
        val server = MockWebServer().apply { start() }
        val url = server.url("/info-conso/").toString()
        server.enqueue(MockResponse().setResponseCode(503))
        try {
            runBlocking { OrangeClient(url, OkHttpClient()).fetch() }
            fail("expected IOException")
        } catch (e: IOException) {
            // expected
        } finally {
            server.shutdown()
        }
    }

    @Test fun `EC-2 fetch with unrecognised page returns null`() {
        val server = MockWebServer().apply { start() }
        server.enqueue(MockResponse().setBody("<html>autre chose</html>"))
        val r = runBlocking { OrangeClient(server.url("/info-conso/").toString(), OkHttpClient()).fetch() }
        assertNull(r)
        server.shutdown()
    }

    @Test fun `AC-13 connection refused is IOException`() {
        val server = MockWebServer().apply { start() }
        val url = server.url("/x").toString()
        server.shutdown()
        try {
            runBlocking { OrangeClient(url, OkHttpClient()).fetch() }
            fail("expected IOException")
        } catch (e: IOException) {
        }
    }
}
