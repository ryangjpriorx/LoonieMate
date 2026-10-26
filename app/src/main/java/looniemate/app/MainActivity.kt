package looniemate.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import looniemate.app.ui.LoonieMateApp
import looniemate.app.ui.theme.LoonieTheme
import looniemate.engine.*

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val look by vm.prefs.look.collectAsStateWithLifecycle()
            LoonieTheme(look.themeMode, look.dynamicColor, look.palette, look.effects) { LoonieMateApp(vm) }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshDay()
        vm.refreshNotif()
        vm.checkUpdates(false)
        vm.updater.onPermissionMaybeGranted()
    }
}
