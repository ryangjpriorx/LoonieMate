package looniemate.app.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import looniemate.engine.*

/**
 * Decides whether a phone gets the extra visuals by default.
 * Flagships (like a Galaxy S24 Ultra) get everything; low-end phones start with them off.
 */
object DeviceClass {
    private fun am(c: Context) = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    fun totalRamGb(c: Context): Double {
        val mi = ActivityManager.MemoryInfo(); am(c).getMemoryInfo(mi)
        return mi.totalMem / 1_000_000_000.0
    }

    /** Google's "media performance class": set by phone makers on capable phones (Android 12 and later). */
    fun performanceClass(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.VERSION.MEDIA_PERFORMANCE_CLASS else 0

    private fun animationsOff(c: Context): Boolean = try {
        Settings.Global.getFloat(c.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    } catch (e: Exception) { false }

    fun effectsByDefault(c: Context): Boolean {
        if (animationsOff(c)) return false                 // the person turned off animations for the whole phone
        if (am(c).isLowRamDevice) return false               // Android Go and similar phones
        if (performanceClass() >= Build.VERSION_CODES.S) return true
        val ram = totalRamGb(c); val cores = Runtime.getRuntime().availableProcessors()
        return ram >= 5.5 && cores >= 8 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    /** Shown under the Visual effects switch: what LoonieMate suggests for this phone, and why. */
    fun describe(c: Context): String {
        val ram = totalRamGb(c); val mem = if (ram >= 0.5) " (${"%.0f".format(ram)} GB of memory)" else ""
        return when {
            animationsOff(c) -> "Suggested: off, because animations are turned off in this phone's settings."
            am(c).isLowRamDevice -> "Suggested: off, to keep this phone quick."
            effectsByDefault(c) -> "Suggested: on. This phone has plenty of power$mem."
            else -> "Suggested: off, to keep this phone quick$mem."
        }
    }
}
