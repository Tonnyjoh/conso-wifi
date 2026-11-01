package mg.flybox.usage.data.net

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mg.flybox.usage.domain.OrangeParser
import mg.flybox.usage.domain.OrangePlan
import okhttp3.OkHttpClient
import okhttp3.Request

/** Page d'info-conso Orange. */
class OrangeClient(
    private val url: String = DEFAULT_URL,
    private val http: OkHttpClient,
) {
    /**
     * Retourne null si la page est lue mais que le forfait est introuvable (EC-2).
     * Lève [IOException] si la page est injoignable.
     */
    suspend fun fetch(): OrangePlan? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", "conso-flybox-mobile/1.0").get().build()
        val body = http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            r.body?.string().orEmpty()
        }
        OrangeParser.parse(body)
    }

    companion object {
        const val DEFAULT_URL = "http://123.orange.mg/info-conso/"
    }
}
