package mg.flybox.usage

import android.app.Application
import mg.flybox.usage.data.work.Notifications
import mg.flybox.usage.data.work.WorkScheduler

class FlyboxApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.ensureChannel(this)
        WorkScheduler.scheduleCollection(this)
    }
}
