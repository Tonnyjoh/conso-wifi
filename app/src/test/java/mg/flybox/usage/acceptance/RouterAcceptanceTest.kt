package mg.flybox.usage.acceptance

import kotlinx.coroutines.runBlocking
import mg.flybox.usage.data.net.LoginOutcome
import mg.flybox.usage.data.net.MemoryCookieJar
import mg.flybox.usage.data.net.RouterClient
import mg.flybox.usage.data.net.RouterSessionRequiredException
import mg.flybox.usage.data.net.RouterUnreachableException
import mg.flybox.usage.domain.RouterParsers
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Mocked router only (no real credentials). AC-3, AC-9, AC-10, AC-17/18, EC-1, EC-3, EC-6, BR-1, BR-7, Q-4. */
class RouterAcceptanceTest {
    private lateinit var server: MockWebServer
    private lateinit var client: RouterClient
    private val secret = "S3cret-Fictif-Pw"

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        val jar = MemoryCookieJar()
        client = RouterClient(server.url("/").toString().trimEnd('/'), OkHttpClient.Builder().cookieJar(jar).build(), jar)
    }

    @After fun tearDown() = server.shutdown()

    private fun body(s: String) = MockResponse().setBody(s)

    private fun allBodiesAndPaths(): String {
        val sb = StringBuilder()
        while (server.requestCount > 0) {
            val r = server.takeRequest(10, java.util.concurrent.TimeUnit.MILLISECONDS) ?: break
            sb.append(r.method).append(' ').append(r.path).append(' ').append(r.body.readUtf8()).append('\n')
            if (sb.length > 100_000) break
        }
        return sb.toString()
    }

    @Test fun `AC-4 system_status uses the documented URL and parses string fields`() {
        server.enqueue(body("""{"uplink_traffic":"50","downlink_traffic":"100","uplink_rate":"1","downlink_rate":"2","online_time":"3600","network_type":"LTE","rsrp":"-90","rssi":"-60","sinr":"12","plmn":"64602"}"""))
        val st = runBlocking { client.systemStatus() }
        assertEquals(100L, st.downBytes); assertEquals(50L, st.upBytes)
        assertEquals("LTE", st.networkType); assertEquals(-90, st.rsrp)
        assertEquals("/reqproc/proc_get?cmd=system_status&isTest=false", server.takeRequest().path)
    }

    @Test fun `EC-1 empty values are unavailable, not zero`() {
        server.enqueue(body("""{"uplink_traffic":"","downlink_traffic":"","network_type":"","rsrp":""}"""))
        val st = runBlocking { client.systemStatus() }
        assertNull(st.downBytes); assertNull(st.upBytes); assertNull(st.networkType); assertNull(st.rsrp)
        assertFalse(st.hasCounters)
    }

    @Test fun `BR-1 unreachable router raises a dedicated exception`() {
        server.shutdown()
        try { runBlocking { client.systemStatus() }; fail() } catch (e: RouterUnreachableException) { }
    }

    @Test fun `AC-3 correct login gives Success`() {
        server.enqueue(body("""{"tz_customer_code":"FR0296"}"""))
        server.enqueue(body("""{"random_login":"123456"}"""))
        server.enqueue(body("""{"result":"0"}"""))
        assertEquals(LoginOutcome.Success, runBlocking { client.login("admin", secret) })
    }

    @Test fun `AC-3 and BR-7 wrong password gives explicit failure, password never sent in clear`() {
        server.enqueue(body("""{"tz_customer_code":"FR0296"}"""))
        server.enqueue(body("""{"random_login":"123456"}"""))
        server.enqueue(body("""{"result":"1"}"""))
        server.enqueue(body("""{"psw_fail_num_str":"4","login_lock_time":"0"}"""))
        val out = runBlocking { client.login("admin", secret) }
        assertTrue("got $out", out is LoginOutcome.WrongPassword)
        val traffic = allBodiesAndPaths()
        assertFalse("clear password sent to router", traffic.contains(secret))
        assertFalse("clear account sent", traffic.contains("username=admin"))
    }

    @Test fun `AC-3 login is a POST to proc_post with goformId LOGIN`() {
        server.enqueue(body("""{"tz_customer_code":"FR0296"}"""))
        server.enqueue(body("""{"random_login":"123456"}"""))
        server.enqueue(body("""{"result":"0"}"""))
        runBlocking { client.login("admin", secret) }
        val traffic = allBodiesAndPaths()
        assertTrue(traffic, traffic.contains("POST /reqproc/proc_post") && traffic.contains("goformId=LOGIN"))
    }

    @Test fun `EC-6 account already in use is reported distinctly`() {
        server.enqueue(body("""{"tz_customer_code":"FR0296"}"""))
        server.enqueue(body("""{"random_login":"123456"}"""))
        server.enqueue(body("""{"result":"3","message":"account already in use"}"""))
        server.enqueue(body("""{"psw_fail_num_str":"5","login_lock_time":"0"}"""))
        assertEquals(LoginOutcome.AccountInUse, runBlocking { client.login("admin", secret) })
    }

    @Test fun `EC-6 login attempt count not exhausted by in-use refusal (a single LOGIN POST)`() {
        server.enqueue(body("""{"tz_customer_code":"FR0296"}"""))
        server.enqueue(body("""{"random_login":"123456"}"""))
        server.enqueue(body("""{"result":"3","message":"account already in use"}"""))
        server.enqueue(body("""{}"""))
        runBlocking { client.login("admin", secret) }
        val logins = allBodiesAndPaths().lines().count { it.contains("goformId=LOGIN") }
        assertEquals(1, logins)
    }

    @Test fun `AC-9 device lists online and offline separated`() {
        server.enqueue(body("""{"station_list":[{"hostname":"iPhone","ip_addr":"192.168.1.10","mac_addr":"aa:bb:cc:dd:ee:01","connect_time":"3600","dev_type":"wifi"}]}"""))
        server.enqueue(body("""{"deviceList_manage":[{"deviceName":"iPhone","macAddress":"AA:BB:CC:DD:EE:01","isOnline":"1","ip":"192.168.1.10","enable":"1","type":"0"},{"deviceName":"TV","macAddress":"AA:BB:CC:DD:EE:02","isOnline":"0","ip":"","enable":"1","type":"0"}]}"""))
        val l = runBlocking { client.deviceLists() }
        assertEquals(1, l.online.size)
        assertEquals("iPhone", l.online[0].name); assertEquals("192.168.1.10", l.online[0].ip)
        assertEquals("AA:BB:CC:DD:EE:01", l.online[0].mac.uppercase()); assertEquals(3600L, l.online[0].connectedSeconds)
        assertEquals(listOf("TV"), l.offline.map { it.name })
    }

    @Test fun `AC-9 online device is not also listed offline`() {
        server.enqueue(body("""{"station_list":[{"hostname":"A","ip_addr":"1.1.1.1","mac_addr":"AA:AA:AA:AA:AA:AA","connect_time":"10"}]}"""))
        server.enqueue(body("""{"deviceList_manage":[{"deviceName":"A","macAddress":"AA:AA:AA:AA:AA:AA","isOnline":"0","ip":""}]}"""))
        assertTrue(runBlocking { client.deviceLists() }.offline.isEmpty())
    }

    @Test fun `AC-10 and EC-3 no session signals session required`() {
        server.enqueue(body("""{"station_list":""}"""))
        try { runBlocking { client.deviceLists() }; fail("expected RouterSessionRequiredException") }
        catch (e: RouterSessionRequiredException) { }
    }

    @Test fun `AC-18 empty per-device traffic gives empty list, no zeros`() {
        assertTrue(RouterParsers.deviceTraffic("""{"deviceList":[]}""").isEmpty())
        assertTrue(RouterParsers.deviceTraffic("""{"deviceList":""}""").isEmpty())
        assertTrue(RouterParsers.deviceTraffic("""{}""").isEmpty())
        assertTrue(RouterParsers.deviceTraffic("not json").isEmpty())
    }

    @Test fun `AC-18 entries without any usable figure are not shown as zero`() {
        assertTrue(RouterParsers.deviceTraffic("""{"deviceList":[{"index":"1","mac":"AA:AA:AA:AA:AA:AA"}]}""").isEmpty())
    }

    @Test fun `Q-4 reading device traffic never sends a settings-changing request`() {
        server.enqueue(body("""{"deviceList":[]}"""))
        runBlocking { client.deviceTraffic() }
        val t = allBodiesAndPaths()
        assertTrue(t, t.startsWith("GET "))
        assertFalse(t.contains("AIRTEL_SET_UPDATE_TRAFFIC"))
    }

    @Test fun `BR-7 error and exception messages never contain the password`() {
        server.shutdown()
        try { runBlocking { client.login("admin", secret) }; fail() } catch (e: Exception) {
            assertFalse(e.toString().contains(secret))
            assertFalse((e.cause?.toString() ?: "").contains(secret))
        }
    }
}
