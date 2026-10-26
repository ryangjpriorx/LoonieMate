package looniemate.app

import android.app.Application
import looniemate.app.data.Prefs
import looniemate.app.data.Repository
import looniemate.app.update.Updater
import looniemate.engine.*

class LoonieApp : Application() {
    lateinit var repo: Repository; private set
    lateinit var prefs: Prefs; private set
    lateinit var updater: Updater; private set
    /** for work that should carry on when the screen is left, like downloading an update */
    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
    /** whether LoonieMate is on screen (set by MainActivity) */
    @Volatile var foreground = false

    override fun onCreate() {
        super.onCreate()
        repo = Repository(filesDir)
        prefs = Prefs(this)
        updater = Updater(this, prefs)
        looniemate.app.notify.Notifs.createChannels(this)
        looniemate.app.notify.UpdateCheck.schedule(this)
        looniemate.app.notify.ReminderAlarm.schedule(this)
    }
}
