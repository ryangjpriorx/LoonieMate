package looniemate.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Settings for this phone only (look, effects, updates). They are not part of the budget or its backups. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("device", Context.MODE_PRIVATE)

    data class Look(
        val themeMode: String,        // system, light, dark
        val dynamicColor: Boolean,    // follow the wallpaper (Android 12+)
        val palette: String,          // used when not following the wallpaper
        val effects: Boolean,         // animations and motion
    )

    private val _look = MutableStateFlow(readLook(context))
    val look: StateFlow<Look> = _look

    private fun readLook(context: Context): Look {
        if (!sp.contains("effects")) {
            /* first launch: turn the extra visuals on for capable phones and off for low-end ones */
            sp.edit().putBoolean("effects", DeviceClass.effectsByDefault(context)).putString("effectsReason", DeviceClass.describe(context)).apply()
        }
        return Look(sp.getString("themeMode", "system")!!, sp.getBoolean("dynamicColor", true), sp.getString("palette", "iris")!!, sp.getBoolean("effects", true))
    }

    fun setLook(l: Look) {
        sp.edit().putString("themeMode", l.themeMode).putBoolean("dynamicColor", l.dynamicColor).putString("palette", l.palette).putBoolean("effects", l.effects).apply()
        _look.value = l
    }

    val effectsReason: String get() = sp.getString("effectsReason", "") ?: ""

    var lastBackup: String?
        get() = sp.getString("lastBackup", null)
        set(v) { sp.edit().putString("lastBackup", v).apply(); _lastBackup.value = v }
    private val _lastBackup = MutableStateFlow(sp.getString("lastBackup", null))
    val lastBackupFlow: StateFlow<String?> = _lastBackup

    var autoUpdate: Boolean
        get() = sp.getBoolean("autoUpdate", true)
        set(v) { sp.edit().putBoolean("autoUpdate", v).apply() }

    var lastUpdateCheck: Long
        get() = sp.getLong("lastUpdateCheck", 0)
        set(v) { sp.edit().putLong("lastUpdateCheck", v).apply() }

    var skippedVersion: Int
        get() = sp.getInt("skippedVersion", 0)
        set(v) { sp.edit().putInt("skippedVersion", v).apply() }

    var group: String
        get() = sp.getString("billGroup", "adults")!!
        set(v) { sp.edit().putString("billGroup", v).apply() }
}
