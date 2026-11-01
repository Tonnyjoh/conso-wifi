package mg.flybox.usage.data.net

import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mg.flybox.usage.domain.DeviceLists
import mg.flybox.usage.domain.DeviceTraffic
import mg.flybox.usage.domain.RouterLoginCodec
import mg.flybox.usage.domain.RouterParsers
import mg.flybox.usage.domain.RouterStatus
import mg.flybox.usage.domain.needsSession
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Le routeur est injoignable (hors du Wi-Fi Flybox, éteint, délai dépassé). */
class RouterUnreachableException(cause: Throwable) : IOException("Routeur injoignable", cause)

/** Une session routeur est nécessaire mais absente ou expirée (EC-3). */
class RouterSessionRequiredException : IOException("Session routeur requise")

sealed interface LoginOutcome {
    data object Success : LoginOutcome
    data class WrongPassword(val attemptsLeft: Int?) : LoginOutcome
    /** Un autre utilisateur est connecté (EC-6). */
    data object AccountInUse : LoginOutcome
    data class Locked(val seconds: Int?) : LoginOutcome
    data class Rejected(val code: String?) : LoginOutcome
}

/** Cookies de session du routeur, en mémoire seulement. */
class MemoryCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, List<Cookie>>()
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isNotEmpty()) store[url.host] = cookies
    }
    override fun loadForRequest(url: HttpUrl): List<Cookie> = store[url.host].orEmpty()
    fun clear() = store.clear()
}

/**
 * Client du routeur Flybox (CP02). Lecture seule hormis l'ouverture et la fermeture de session :
 * aucun goformId qui modifie des réglages n'est jamais envoyé.
 * Un mot de passe n'apparaît jamais dans un message d'erreur ni un journal.
 */
class RouterClient(
    baseUrl: String = DEFAULT_BASE,
    private val http: OkHttpClient,
    private val cookies: MemoryCookieJar? = null,
) {
    private val base: HttpUrl = baseUrl.toHttpUrl()

    private fun getUrl(cmd: String, multi: Boolean = false): HttpUrl =
        base.newBuilder().addPathSegments("reqproc/proc_get")
            .addQueryParameter("cmd", cmd)
            .apply { if (multi) addQueryParameter("multi_data", "1") }
            .addQueryParameter("isTest", "false").build()

    private suspend fun get(cmd: String, multi: Boolean = false): String = execute(Request.Builder().url(getUrl(cmd, multi)).get().build())

    private suspend fun post(form: FormBody): String =
        execute(
            Request.Builder().url(base.newBuilder().addPathSegments("reqproc/proc_post").build())
                .header("Content-Type", "application/x-www-form-urlencoded;")
                .post(form).build(),
        )

    private suspend fun execute(req: Request): String = withContext(Dispatchers.IO) {
        try {
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                r.body?.string().orEmpty()
            }
        } catch (e: RouterSessionRequiredException) {
            throw e
        } catch (e: IOException) {
            throw RouterUnreachableException(e)
        }
    }

    /** system_status : sans authentification. */
    suspend fun systemStatus(): RouterStatus = RouterParsers.systemStatus(get("system_status"))

    /**
     * Ouvre une session : 1) code client (variante d'encodage), 2) POST GET_RANDOM_LOGIN,
     * 3) POST LOGIN avec compte et mot de passe encodés. Ne réessaie jamais seul.
     */
    suspend fun login(account: String, password: String): LoginOutcome {
        val customer = RouterParsers.customerCode(get("tz_customer_code"))
        val newVariant = RouterLoginCodec.usesNewVariant(customer)
        var random = RouterParsers.randomLogin(post(FormBody.Builder().add("goformId", "GET_RANDOM_LOGIN").add("isTest", "false").build()))
        if (random.isNullOrEmpty()) {
            // Repli du client web si le POST échoue : lecture par cmd.
            random = RouterParsers.randomLogin(get("get_random_login"))
        }
        if (random.isNullOrEmpty()) return LoginOutcome.Rejected("random_login_vide")
        val body = FormBody.Builder()
            .add("isTest", "false")
            .add("goformId", "LOGIN")
            .add("username", RouterLoginCodec.encodeUsername(account))
            .add("password", RouterLoginCodec.encodePassword(random, password, newVariant))
            .build()
        val reply = RouterParsers.loginReply(post(body))
        return when {
            reply.result == "0" -> LoginOutcome.Success
            looksLikeAccountInUse(reply.raw, reply.result) -> LoginOutcome.AccountInUse
            else -> classifyFailure(reply.result)
        }
    }

    private suspend fun classifyFailure(code: String?): LoginOutcome {
        // Lecture seule : le nombre d'essais restants et la durée de blocage.
        val info = try {
            RouterParsers.loginAttempts(get("modem_main_state,puknumber,pinnumber,psw_fail_num_str,login_lock_time", multi = true))
        } catch (_: IOException) {
            null to null
        }
        val left = info.first
        val lock = info.second
        return when {
            left == 0 && lock != null && lock > 0 -> LoginOutcome.Locked(lock)
            code == null -> LoginOutcome.Rejected(null)
            else -> LoginOutcome.WrongPassword(left)
        }
    }

    private fun looksLikeAccountInUse(raw: String, result: String?): Boolean {
        val l = raw.lowercase()
        return l.contains("already") || l.contains("in use") || l.contains("repeat")
    }

    /** Ferme la session pour libérer le compte (EC-6). Échec ignoré. */
    suspend fun logout() {
        try {
            post(FormBody.Builder().add("isTest", "false").add("goformId", "LOGOUT").build())
        } catch (_: IOException) {
        }
        cookies?.clear()
    }

    /** Appareils en ligne et connus. Lève [RouterSessionRequiredException] sans session. */
    suspend fun deviceLists(): DeviceLists {
        val stations = get("station_list")
        if (RouterParsers.needsSession(stations, "station_list")) throw RouterSessionRequiredException()
        val known = get("deviceList_manage")
        return RouterParsers.mergeDevices(RouterParsers.stationList(stations), RouterParsers.deviceListManage(known))
    }

    /** Trafic par appareil. Vide si le routeur ne le fournit pas (AC-18). Lecture seule. */
    suspend fun deviceTraffic(): List<DeviceTraffic> = RouterParsers.deviceTraffic(get("AIRTEL_GET_DEVICE_INFO_TRAFFIC"))

    companion object {
        const val DEFAULT_BASE = "http://192.168.1.1"
    }
}
