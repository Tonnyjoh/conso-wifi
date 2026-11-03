package mg.flybox.usage.domain

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Analyse défensive des réponses JSON du routeur. Aucun champ n'est inventé (BR-1, EC-1). */
object RouterParsers {

    private fun JSONObject.str(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val v = opt(key)?.toString()?.trim()
        return if (v.isNullOrEmpty()) null else v
    }

    private fun JSONObject.long(key: String): Long? = str(key)?.toDoubleOrNull()?.toLong()
    private fun JSONObject.int(key: String): Int? = str(key)?.toDoubleOrNull()?.toInt()

    /** system_status. Lève [JSONException] si le texte n'est pas du JSON. */
    fun systemStatus(body: String): RouterStatus {
        val o = JSONObject(body)
        return RouterStatus(
            downBytes = o.long("downlink_traffic"),
            upBytes = o.long("uplink_traffic"),
            downRate = o.long("downlink_rate"),
            upRate = o.long("uplink_rate"),
            onlineTime = o.long("online_time"),
            networkType = o.str("network_type"),
            rsrp = o.int("rsrp"),
            rssi = o.int("rssi"),
            sinr = o.str("sinr")?.toDoubleOrNull(),
            plmn = o.str("plmn"),
        )
    }

    private fun array(body: String, key: String): JSONArray? {
        val o = try { JSONObject(body) } catch (_: JSONException) { return null }
        return o.optJSONArray(key)
    }

    /** station_list : appareils actuellement connectés. */
    fun stationList(body: String): List<OnlineDevice> {
        val a = array(body, "station_list") ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { d ->
            OnlineDevice(
                name = d.str("hostname") ?: "Appareil inconnu",
                ip = d.str("ip_addr") ?: "",
                mac = (d.str("mac_addr") ?: "").uppercase(),
                connectedSeconds = d.str("connect_time")?.let { parseConnectTime(it) },
            )
        }
    }

    /** connect_time est soit un nombre de secondes, soit "hh:mm:ss" ou "jj:hh:mm:ss". */
    fun parseConnectTime(raw: String): Long? {
        raw.toDoubleOrNull()?.let { return it.toLong() }
        val parts = raw.split(":").map { it.trim().toLongOrNull() ?: return null }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            4 -> parts[0] * 86400 + parts[1] * 3600 + parts[2] * 60 + parts[3]
            else -> null
        }
    }

    /** deviceList_manage : appareils connus, en ligne ou non. */
    fun deviceListManage(body: String): List<KnownDevice> {
        val a = array(body, "deviceList_manage") ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { d ->
            KnownDevice(
                name = d.str("deviceName") ?: "Appareil inconnu",
                mac = (d.str("macAddress") ?: "").uppercase(),
                ip = d.str("ip") ?: "",
                online = d.str("isOnline") == "1",
                internetEnabled = d.str("enable") != "0",
            )
        }
    }

    /** Fusionne les deux listes : en ligne depuis station_list, hors ligne depuis les connus non présents. */
    fun mergeDevices(online: List<OnlineDevice>, known: List<KnownDevice>): DeviceLists {
        val onlineMacs = online.map { it.mac }.toSet()
        val offline = known.filter { !it.online && it.mac !in onlineMacs }
        val blockedMacs = known.filter { !it.internetEnabled }.map { it.mac }.toSet()
        return DeviceLists(online.map { it.copy(internetEnabled = it.mac !in blockedMacs) }, offline)
    }

    private val macKeys = listOf("mac", "macAddress", "mac_addr", "MacAddress", "MACAddress", "mac_address")
    private val nameKeys = listOf("hostname", "hostName", "deviceName", "name", "HostName")
    private val ipKeys = listOf("ip", "ip_addr", "ipAddress", "IPAddress")
    private val totalKeys = listOf(
        "total_traffic", "totalTraffic", "traffic_total", "traffic", "total", "data_usage", "usage",
        "total_bytes", "bytes",
    )

    /**
     * AIRTEL_GET_DEVICE_INFO_TRAFFIC : {deviceList:[{index, ...}]}. Les noms de champs sont inconnus
     * (aucune donnée vue sur ce firmware), l'analyse est donc heuristique. Les entrées d'index 0
     * sont ignorées comme dans le client web. Un résultat vide ou sans total exploitable donne
     * une liste vide, jamais des zéros (AC-18).
     */
    fun deviceTraffic(body: String): List<DeviceTraffic> {
        val a = array(body, "deviceList") ?: return emptyList()
        val out = ArrayList<DeviceTraffic>()
        for (i in 0 until a.length()) {
            val d = a.optJSONObject(i) ?: continue
            if (d.str("index") == "0") continue
            val total = trafficTotal(d) ?: continue
            out.add(
                DeviceTraffic(
                    mac = macKeys.firstNotNullOfOrNull { d.str(it) }?.uppercase(),
                    name = nameKeys.firstNotNullOfOrNull { d.str(it) },
                    ip = ipKeys.firstNotNullOfOrNull { d.str(it) },
                    totalBytes = total,
                ),
            )
        }
        return out
    }

    private fun trafficTotal(d: JSONObject): Long? {
        for (k in totalKeys) d.long(k)?.let { if (it >= 0) return it }
        var sum = 0L
        var found = false
        val keys = d.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val lk = k.lowercase()
            val directional = listOf("down", "up", "rx", "tx", "recv", "receive", "send", "sent")
            if (directional.any { lk.contains(it) } && (lk.contains("byte") || lk.contains("traffic") || lk.contains("flow"))) {
                d.long(k)?.let { if (it >= 0) { sum += it; found = true } }
            }
        }
        return if (found) sum else null
    }

    /** Adresse MAC telle que deviceList_manage la renvoie (casse d'origine), ou null si l'appareil n'y figure pas. */
    fun rawMacFor(body: String, mac: String): String? {
        val a = array(body, "deviceList_manage") ?: return null
        for (i in 0 until a.length()) {
            val raw = a.optJSONObject(i)?.str("macAddress") ?: continue
            if (raw.equals(mac, ignoreCase = true)) return raw
        }
        return null
    }

    /** get_token : {"token":"..."}. Le client web envoie ensuite CSRFToken = sha256(token) avec chaque écriture. */
    fun token(body: String): String? {
        val o = try { JSONObject(body) } catch (_: JSONException) { return null }
        return o.str("token")
    }

    /** SHA-256 en hexadécimal minuscule, comme la bibliothèque du client web. */
    fun csrfToken(token: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** Réponse d'une commande d'écriture : {"result":"success"}. */
    fun isSuccess(body: String): Boolean {
        val o = try { JSONObject(body) } catch (_: JSONException) { return false }
        return o.str("result") == "success"
    }

    /** Résultat d'un LOGIN : {"result":"0","cookie":"...","power":"3"}. */
    data class LoginReply(val result: String?, val raw: String)

    fun loginReply(body: String): LoginReply {
        val o = try { JSONObject(body) } catch (_: JSONException) { return LoginReply(null, body) }
        return LoginReply(o.str("result"), body)
    }

    /** random_login : {"random_login":"6516650"}. */
    fun randomLogin(body: String): String? {
        val o = try { JSONObject(body) } catch (_: JSONException) { return null }
        return o.str("random_login") ?: o.str("get_random_login")
    }

    fun customerCode(body: String): String? {
        val o = try { JSONObject(body) } catch (_: JSONException) { return null }
        return o.str("tz_customer_code")
    }

    /** Champs psw_fail_num_str et login_lock_time lus après un échec. */
    fun loginAttempts(body: String): Pair<Int?, Int?> {
        val o = try { JSONObject(body) } catch (_: JSONException) { return null to null }
        return o.int("psw_fail_num_str") to o.int("login_lock_time")
    }
}

/** Vrai si la réponse indique qu'une session est nécessaire : la clé existe avec une chaîne vide. */
fun RouterParsers.needsSession(body: String, key: String): Boolean {
    val o = try { JSONObject(body) } catch (_: JSONException) { return false }
    return o.has(key) && o.opt(key) is String && (o.opt(key) as String).isEmpty()
}
