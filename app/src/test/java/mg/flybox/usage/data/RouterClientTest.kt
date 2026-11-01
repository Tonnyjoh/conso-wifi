package mg.flybox.usage.data

import kotlinx.coroutines.runBlocking
import mg.flybox.usage.data.net.LoginOutcome
import mg.flybox.usage.data.net.MemoryCookieJar
import mg.flybox.usage.data.net.OrangeClient
import mg.flybox.usage.data.net.RouterClient
import mg.flybox.usage.data.net.RouterSessionRequiredException
import mg.flybox.usage.data.net.RouterUnreachableException
import mg.flybox.usage.domain.RouterLoginCodec
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.URLDecoder

/**
 * Le protocole de connexion est testé contre des réponses simulées, reprises de ce que le routeur
 * renvoie sans authentification (formats relevés) : le vrai mot de passe n'est jamais utilisé.
 */
class RouterClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: RouterClient

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        val cookies = MemoryCookieJar()
        client = RouterClient(server.url("/").toString().trimEnd('/'), OkHttpClient.Builder().cookieJar(cookies).build(), cookies)
    }

    @After fun tearDown() = server.shutdown()

    private fun form(body: String): Map<String, String> =
        body.split("&").associate { kv -> kv.substringBefore("=") to URLDecoder.decode(kv.substringAfter("="), "UTF-8") }

    @Test fun systemStatusRequestAndParse() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"downlink_traffic":"100","uplink_traffic":"50","rsrp":"-90","network_type":"LTE"}"""))
        val s = client.systemStatus()
        assertEquals(100L, s.downBytes)
        val r = server.takeRequest()
        assertEquals("GET", r.method)
        assertEquals("/reqproc/proc_get?cmd=system_status&isTest=false", r.path)
    }

    @Test fun loginSuccessUsesOldVariantForFR0296() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"tz_customer_code":"FR0296"}"""))
        server.enqueue(MockResponse().setBody("""{"random_login":"6516650"}"""))
        server.enqueue(MockResponse().setBody("""{"result":"0","cookie":"abc","power":"3"}"""))
        val out = client.login("admin", "motdepasse-fictif")
        assertEquals(LoginOutcome.Success, out)
        server.takeRequest() // tz_customer_code
        val rnd = server.takeRequest()
        assertEquals("POST", rnd.method)
        assertEquals("/reqproc/proc_post", rnd.path)
        assertEquals("GET_RANDOM_LOGIN", form(rnd.body.readUtf8())["goformId"])
        val login = server.takeRequest()
        val f = form(login.body.readUtf8())
        assertEquals("LOGIN", f["goformId"])
        assertEquals("YWRtaW4=", f["username"])
        assertEquals(RouterLoginCodec.encodePassword("6516650", "motdepasse-fictif", false), f["password"])
        assertTrue(login.getHeader("Content-Type")!!.startsWith("application/x-www-form-urlencoded"))
        // Aucun jeton CSRF avant connexion (get_token vide).
        assertNull(f["CSRFToken"])
    }

    @Test fun loginUsesNewVariantForMA0553() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"tz_customer_code":"MA0553"}"""))
        server.enqueue(MockResponse().setBody("""{"random_login":"123"}"""))
        server.enqueue(MockResponse().setBody("""{"result":"0"}"""))
        assertEquals(LoginOutcome.Success, client.login("admin", "x"))
        server.takeRequest(); server.takeRequest()
        assertEquals(RouterLoginCodec.encodePassword("123", "x", true), form(server.takeRequest().body.readUtf8())["password"])
    }

    @Test fun wrongPasswordReportsRemainingAttempts() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"tz_customer_code":"FR0296"}"""))
        server.enqueue(MockResponse().setBody("""{"random_login":"6516650"}"""))
        server.enqueue(MockResponse().setBody("""{"result":"1"}"""))
        server.enqueue(MockResponse().setBody("""{"psw_fail_num_str":"4","login_lock_time":"-1"}"""))
        val out = client.login("admin", "mauvais")
        assertEquals(LoginOutcome.WrongPassword(4), out)
    }

    @Test fun lockedAfterTooManyFailures() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"tz_customer_code":"FR0296"}"""))
        server.enqueue(MockResponse().setBody("""{"random_login":"1"}"""))
        server.enqueue(MockResponse().setBody("""{"result":"1"}"""))
        server.enqueue(MockResponse().setBody("""{"psw_fail_num_str":"0","login_lock_time":"300"}"""))
        assertEquals(LoginOutcome.Locked(300), client.login("admin", "mauvais"))
    }

    @Test fun accountInUseIsRecognised() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"tz_customer_code":"FR0296"}"""))
        server.enqueue(MockResponse().setBody("""{"random_login":"1"}"""))
        server.enqueue(MockResponse().setBody("""{"result":"3","msg":"account already in use"}"""))
        assertEquals(LoginOutcome.AccountInUse, client.login("admin", "x"))
    }

    @Test fun emptyRandomLoginIsRejectedWithoutSendingCredentials() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"tz_customer_code":"FR0296"}"""))
        server.enqueue(MockResponse().setBody("""{"random_login":""}"""))
        server.enqueue(MockResponse().setBody("""{"get_random_login":""}"""))
        val out = client.login("admin", "secret")
        assertTrue(out is LoginOutcome.Rejected)
        assertEquals(3, server.requestCount)
    }

    @Test fun deviceListsNeedSessionWhenValueIsEmptyString() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"station_list":""}"""))
        try {
            client.deviceLists()
            fail("session attendue")
        } catch (_: RouterSessionRequiredException) {
        }
    }

    @Test fun deviceListsParsedWithSession() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"station_list":[{"hostname":"Phone","ip_addr":"192.168.1.5","mac_addr":"aa:bb:cc:00:00:01","connect_time":"60"}]}"""))
        server.enqueue(MockResponse().setBody("""{"deviceList_manage":[{"deviceName":"TV","macAddress":"aa:bb:cc:00:00:02","isOnline":"0","ip":""}]}"""))
        val l = client.deviceLists()
        assertEquals(1, l.online.size)
        assertEquals("TV", l.offline.single().name)
    }

    @Test fun trafficEmptyStringMeansNone() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"AIRTEL_GET_DEVICE_INFO_TRAFFIC":""}"""))
        assertTrue(client.deviceTraffic().isEmpty())
        assertEquals("/reqproc/proc_get?cmd=AIRTEL_GET_DEVICE_INFO_TRAFFIC&isTest=false", server.takeRequest().path)
    }

    @Test fun neverSendsSettingChangingGoformIds() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"tz_customer_code":"FR0296"}"""))
        server.enqueue(MockResponse().setBody("""{"random_login":"1"}"""))
        server.enqueue(MockResponse().setBody("""{"result":"0"}"""))
        client.login("admin", "x")
        server.enqueue(MockResponse().setBody("""{}"""))
        client.logout()
        val ids = (0 until server.requestCount).mapNotNull {
            val r = server.takeRequest()
            if (r.method == "POST") form(r.body.readUtf8())["goformId"] else null
        }
        assertEquals(listOf("GET_RANDOM_LOGIN", "LOGIN", "LOGOUT"), ids)
    }

    @Test fun unreachableRouterIsReported() = runBlocking {
        server.shutdown()
        try {
            client.systemStatus()
            fail("injoignable attendu")
        } catch (_: RouterUnreachableException) {
        }
    }

    @Test fun orangeClientParsesPageAndReportsLayoutChange() = runBlocking {
        server.enqueue(MockResponse().setBody("<p>validité Pass 3 Go sur 10 Go valable jusqu'au 05/12/2026</p>"))
        server.enqueue(MockResponse().setBody("<p>Nouvelle mise en page</p>"))
        val o = OrangeClient(server.url("/info-conso/").toString(), OkHttpClient())
        assertEquals(3.0, o.fetch()!!.remainingGo, 1e-9)
        assertNull(o.fetch())
    }
}
