package mg.flybox.usage.data.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import mg.flybox.usage.FlyboxApp
import mg.flybox.usage.MainActivity
import mg.flybox.usage.R
import mg.flybox.usage.data.SettingsStore
import mg.flybox.usage.data.UsageRepository
import mg.flybox.usage.domain.ReminderText

object Notifications {
    const val CHANNEL_ID = "daily_usage"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Rappel quotidien", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Usage du jour et solde du forfait"
                },
            )
        }
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun post(context: Context, title: String, text: String) {
        if (!canPost(context)) return
        val open = android.app.PendingIntent.getActivity(
            context, 0, android.content.Intent(context, MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_usage)
            .setContentTitle(title)
            .setContentText(text.lines().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setColor(0xFF2459D6.toInt())
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ID, n)
    }

    private const val NOTIF_ID = 1001
}

/** Poste le rappel du jour au plus une fois (BR-8, AC-12). */
class ReminderPoster(
    private val context: Context,
    private val usage: UsageRepository,
    private val settings: SettingsStore,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    /** Retourne vrai si une notification a été postée. */
    suspend fun postIfDue(): Boolean {
        val s = settings.current()
        if (!s.reminderEnabled) return false
        val now = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(clock()), zone())
        val day = now.toLocalDate().toString()
        if (s.lastReminderDay == day || now.hour < s.reminderHour) return false
        if (!Notifications.canPost(context)) return false
        val data = usage.snapshot()
        val text = ReminderText.build(data.todayBytes, data.plan, data.prediction, s.alwaysGo)
        Notifications.post(context, ReminderText.TITLE, text)
        settings.setLastReminderDay(day)
        return true
    }
}

object WorkScheduler {
    private const val COLLECT = "collect"
    private const val REMINDER = "reminder"

    /** Collecte périodique, 15 minutes au minimum (AC-11). */
    fun scheduleCollection(context: Context) {
        val req = PeriodicWorkRequestBuilder<CollectWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(COLLECT, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Délai en millisecondes jusqu'à la prochaine occurrence de [hour]:00 heure locale. */
    fun delayUntilNext(hour: Int, nowMillis: Long, zone: ZoneId): Long {
        val now = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone)
        var next = now.toLocalDate().atTime(hour, 0).atZone(zone)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return next.toInstant().toEpochMilli() - nowMillis
    }

    fun scheduleReminder(context: Context, hour: Int) {
        val delay = delayUntilNext(hour, System.currentTimeMillis(), ZoneId.systemDefault())
        val req = OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork(REMINDER, ExistingWorkPolicy.REPLACE, req)
    }

    fun cancelReminder(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(REMINDER)
    }
}

class CollectWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as FlyboxApp).container
        // Routeur injoignable : rien n'est stocké, le prochain passage réessaiera (BR-1).
        c.usage.refresh(withOrange = false)
        c.reminder.postIfDue()
        return Result.success()
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as FlyboxApp).container
        val s = c.settings.current()
        if (!s.reminderEnabled) return Result.success()
        c.usage.refresh(withOrange = true)
        c.reminder.postIfDue()
        WorkScheduler.scheduleReminder(applicationContext, s.reminderHour)
        return Result.success()
    }
}
