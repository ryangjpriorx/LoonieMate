package looniemate.app.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import looniemate.app.LoonieApp

/** Hears back from Android's installer: asks the person to confirm, or reports how it went. */
class InstallReceiver : BroadcastReceiver() {
    companion object { const val ACTION = "looniemate.app.INSTALL_RESULT" }

    override fun onReceive(context: Context, intent: Intent) {
        val updater = (context.applicationContext as LoonieApp).updater
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm != null) context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                else updater.onInstallResult(false, null)
            }
            PackageInstaller.STATUS_SUCCESS -> updater.onInstallResult(true, null)
            PackageInstaller.STATUS_FAILURE_ABORTED -> updater.onInstallResult(false, "it was cancelled")
            else -> updater.onInstallResult(false, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        }
    }
}
