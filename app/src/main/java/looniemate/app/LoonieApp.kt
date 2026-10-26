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

    override fun onCreate() {
        super.onCreate()
        repo = Repository(filesDir)
        prefs = Prefs(this)
        updater = Updater(this, prefs)
    }
}
