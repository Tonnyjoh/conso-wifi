package mg.flybox.usage.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouterParsersTest {
    // Réponse réelle de system_status relevée sur le routeur (valeurs tronquées au nécessaire).
    private val status = """{"network_type":"LTE","plmn":"Orange","rsrp":"-100","rssi":"-73","sinr":"10.80","uplink_rate":"2076","downlink_rate":"3900","uplink_traffic":"1479608225124","downlink_traffic":"276361652061","online_time":"10191"}"""

    @Test fun parsesSystemStatus() {
        val s = RouterParsers.systemStatus(status)
        assertEquals(276361652061L, s.downBytes)
        assertEquals(1479608225124L, s.upBytes)
        assertEquals(3900L, s.downRate)
        assertEquals(2076L, s.upRate)
        assertEquals(10191L, s.onlineTime)
        assertEquals("LTE", s.networkType)
        assertEquals(-100, s.rsrp)
        assertEquals(-73, s.rssi)
        assertEquals(10.8, s.sinr!!, 1e-9)
        assertTrue(s.hasCounters)
    }

    @Test fun emptyValuesAreUnavailableNotZero() {
        val s = RouterParsers.systemStatus("""{"downlink_traffic":"","uplink_traffic":"","rsrp":""}""")
        assertNull(s.downBytes)
        assertNull(s.rsrp)
        assertFalse(s.hasCounters)
    }

    @Test fun parsesStationList() {
        val d = RouterParsers.stationList("""{"station_list":[{"hostname":"Phone","ip_addr":"192.168.1.20","mac_addr":"aa:bb:cc:dd:ee:ff","connect_time":"3725","dev_type":"wifi"}]}""")
        assertEquals(1, d.size)
        assertEquals("AA:BB:CC:DD:EE:FF", d[0].mac)
        assertEquals(3725L, d[0].connectedSeconds)
    }

    @Test fun connectTimeFormats() {
        assertEquals(3725L, RouterParsers.parseConnectTime("01:02:05"))
        assertEquals(90061L, RouterParsers.parseConnectTime("1:01:01:01"))
        assertNull(RouterParsers.parseConnectTime("abc"))
    }

    @Test fun emptyStationListIsEmpty() {
        assertTrue(RouterParsers.stationList("""{"station_list":[]}""").isEmpty())
        assertTrue(RouterParsers.stationList("not json").isEmpty())
        assertTrue(RouterParsers.stationList("""{"station_list":""}""").isEmpty())
    }

    @Test fun mergesOnlineAndOfflineDevices() {
        val online = RouterParsers.stationList("""{"station_list":[{"hostname":"Phone","ip_addr":"1.2.3.4","mac_addr":"AA:AA:AA:AA:AA:AA","connect_time":"10"}]}""")
        val known = RouterParsers.deviceListManage("""{"deviceList_manage":[
            {"deviceName":"Phone","macAddress":"aa:aa:aa:aa:aa:aa","isOnline":"1","ip":"1.2.3.4"},
            {"deviceName":"TV","macAddress":"BB:BB:BB:BB:BB:BB","isOnline":"0","ip":""}]}""")
        val m = RouterParsers.mergeDevices(online, known)
        assertEquals(1, m.online.size)
        assertEquals(listOf("TV"), m.offline.map { it.name })
    }

    @Test fun trafficEmptyMeansNone() {
        assertTrue(RouterParsers.deviceTraffic("""{"deviceList":[]}""").isEmpty())
        assertTrue(RouterParsers.deviceTraffic("""{"deviceList":[{"index":"0"}]}""").isEmpty())
        assertTrue(RouterParsers.deviceTraffic("""{}""").isEmpty())
        assertTrue(RouterParsers.deviceTraffic("garbage").isEmpty())
        // Une entrée sans total exploitable n'est pas affichée comme zéro (AC-18).
        assertTrue(RouterParsers.deviceTraffic("""{"deviceList":[{"index":"1","mac":"AA"}]}""").isEmpty())
    }

    @Test fun trafficWithTotalOrDirections() {
        val t = RouterParsers.deviceTraffic("""{"deviceList":[
            {"index":"1","mac":"aa:aa","hostname":"A","total_traffic":"5000"},
            {"index":"2","macAddress":"bb:bb","down_bytes":"300","up_bytes":"200"}]}""")
        assertEquals(listOf(5000L, 500L), t.map { it.totalBytes })
        assertEquals("AA:AA", t[0].mac)
    }

    @Test fun loginReplies() {
        assertEquals("0", RouterParsers.loginReply("""{"result":"0","cookie":"x","power":"3"}""").result)
        assertNull(RouterParsers.loginReply("oops").result)
        assertEquals("6516650", RouterParsers.randomLogin("""{"random_login":"6516650"}"""))
        assertNull(RouterParsers.randomLogin("""{"get_random_login":""}"""))
        assertEquals("FR0296", RouterParsers.customerCode("""{"tz_customer_code":"FR0296"}"""))
        assertEquals(3 to 300, RouterParsers.loginAttempts("""{"psw_fail_num_str":"3","login_lock_time":"300"}"""))
    }
}
