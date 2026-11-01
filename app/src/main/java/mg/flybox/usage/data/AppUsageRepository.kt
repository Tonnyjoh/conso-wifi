package mg.flybox.usage.data

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Process
import android.provider.Settings
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId

enum class UsagePeriod(val label: String, val days: Int) {
    TODAY("Aujourd'hui", 1),
    WEEK("7 jours", 7),
    MONTH("30 jours", 30),
}

data class AppUsageRow(
    val packageName: String,
    val label: String,
    val bytes: Long,
    val icon: Bitmap?,
)

/** Usage réseau des applications de ce téléphone (AC-15, AC-16) via NetworkStatsManager. */
class AppUsageRepository(private val context: Context) {

    fun hasPermission(): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Écran des réglages spéciaux « Accès aux données d'utilisation », ouvert directement sur cette application. */
    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    suspend fun load(period: UsagePeriod, zone: ZoneId = ZoneId.systemDefault()): List<AppUsageRow> = withContext(Dispatchers.IO) {
        val nsm = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        val end = System.currentTimeMillis()
        val start = java.time.LocalDate.now(zone).minusDays((period.days - 1).toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        val perUid = HashMap<Int, Long>()
        for (type in listOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)) {
            try {
                val stats = nsm.querySummary(type, null, start, end)
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    perUid[bucket.uid] = (perUid[bucket.uid] ?: 0L) + bucket.rxBytes + bucket.txBytes
                }
                stats.close()
            } catch (_: Exception) {
                // Type de réseau indisponible sur cet appareil : on ignore.
            }
        }
        val pm = context.packageManager
        val merged = LinkedHashMap<String, AppUsageRow>()
        for ((uid, bytes) in perUid) {
            if (bytes < MIN_BYTES) continue
            val pkg = pm.getPackagesForUid(uid)?.firstOrNull() ?: continue
            val row = describe(pm, pkg, bytes)
            val prev = merged[pkg]
            merged[pkg] = if (prev == null) row else prev.copy(bytes = prev.bytes + bytes)
        }
        merged.values.sortedByDescending { it.bytes }.take(MAX_ROWS)
    }

    private fun describe(pm: PackageManager, pkg: String, bytes: Long): AppUsageRow = try {
        val info = pm.getApplicationInfo(pkg, 0)
        val icon = runCatching { pm.getApplicationIcon(info).toBitmap(96, 96, Bitmap.Config.ARGB_8888) }.getOrNull()
        AppUsageRow(pkg, pm.getApplicationLabel(info).toString(), bytes, icon)
    } catch (_: PackageManager.NameNotFoundException) {
        AppUsageRow(pkg, pkg.substringAfterLast('.'), bytes, null)
    }

    private companion object {
        const val MAX_ROWS = 40
        const val MIN_BYTES = 100_000L
    }
}
