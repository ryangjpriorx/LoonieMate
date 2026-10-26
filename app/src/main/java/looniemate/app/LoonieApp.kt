package looniemate.app

import android.app.Application
import looniemate.app.data.Prefs
import looniemate.app.data.Repository
import looniemate.app.update.Updater
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import looniemate.engine.*

class LoonieApp : Application() {
    lateinit var repo: Repository; private set
    lateinit var prefs: Prefs; private set
    lateinit var updater: Updater; private set
    lateinit var sync: looniemate.app.sync.SyncManager; private set
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
        sync = looniemate.app.sync.SyncManager(this, repo, prefs)
        if (sync.on) looniemate.app.sync.SyncManager.schedule(this)
        /* 15 seconds after a change while the app is open (not the changes a sync itself applied) */
        @OptIn(kotlinx.coroutines.FlowPreview::class)
        scope.launch { repo.docs.drop(1).debounce(15_000).collect { d -> if (sync.on && foreground && d !== sync.lastApplied) sync.sync(force = true) } }
    }
}
