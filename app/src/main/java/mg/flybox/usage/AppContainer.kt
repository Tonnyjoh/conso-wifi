package mg.flybox.usage

import android.content.Context
import androidx.room.Room
import mg.flybox.usage.data.KeystoreSecretStore
import mg.flybox.usage.data.RouterSession
import mg.flybox.usage.data.SecretStore
import mg.flybox.usage.data.SettingsStore
import mg.flybox.usage.data.UsageRepository
import mg.flybox.usage.data.AppUsageRepository
import mg.flybox.usage.data.db.AppDatabase
import mg.flybox.usage.data.net.HttpClients
import mg.flybox.usage.data.net.MemoryCookieJar
import mg.flybox.usage.data.net.OrangeClient
import mg.flybox.usage.data.net.RouterClient
import mg.flybox.usage.data.work.ReminderPoster

/** Assemblage manuel des dépendances (pas de framework d'injection). */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    val settings = SettingsStore(app)
    val secrets: SecretStore = KeystoreSecretStore(app)
    private val db = Room.databaseBuilder(app, AppDatabase::class.java, "flybox.db").build()
    private val cookies = MemoryCookieJar()
    private val http = HttpClients.create(app, cookies)

    val routerClient = RouterClient(http = http, cookies = cookies)
    val orangeClient = OrangeClient(http = HttpClients.create(app))
    val usage = UsageRepository(db, routerClient, orangeClient, settings)
    val routerSession = RouterSession(routerClient, secrets, settings)
    val appUsage = AppUsageRepository(app)
    val reminder = ReminderPoster(app, usage, settings)
}
