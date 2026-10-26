package looniemate.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import looniemate.app.ui.LoonieMateApp
import kotlinx.coroutines.launch
import looniemate.app.ui.theme.LoonieTheme
import looniemate.engine.*

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val look by vm.prefs.look.collectAsStateWithLifecycle()
            val access by vm.prefs.access.collectAsStateWithLifecycle()
            val still by vm.prefs.phoneNoMotion.collectAsStateWithLifecycle()
            LoonieTheme(look.themeMode, look.dynamicColor, look.palette, look.effects && !still, access) { LoonieMateApp(vm) }
        }
        handleOpen(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) { super.onNewIntent(intent); setIntent(intent); handleOpen(intent) }

    /** opened from the "update available" notification: go straight to the update */
    private fun handleOpen(intent: android.content.Intent?) {
        val what = intent?.getStringExtra(looniemate.app.notify.UpdateCheck.EXTRA_OPEN) ?: return
        if (what == looniemate.app.notify.UpdateCheck.OPEN_UPDATE || what == looniemate.app.notify.UpdateCheck.OPEN_INSTALL) {
            intent.removeExtra(looniemate.app.notify.UpdateCheck.EXTRA_OPEN); vm.openUpdate.value = what
            looniemate.app.notify.UpdateCheck.clear(this)
        }
    }

    override fun onPause() { super.onPause(); val app = application as LoonieApp; app.foreground = false
        /* send this phone's changes before the person leaves */
        if (app.sync.on) app.scope.launch { app.sync.sync(force = true) } }

    override fun onResume() {
        super.onResume()
        (application as LoonieApp).foreground = true
        vm.prefs.phoneNoMotion.value = try { android.provider.Settings.Global.getFloat(contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f } catch (_: Exception) { false }
        vm.refreshDay()
        vm.refreshNotif()
        vm.checkUpdates(false)
        /* pick up the other phone's changes (at most once every 20 seconds) */
        (application as LoonieApp).let { app -> if (app.sync.on) app.scope.launch { app.sync.sync() } }
        vm.updater.onPermissionMaybeGranted()
    }
}
