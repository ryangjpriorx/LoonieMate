package looniemate.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import looniemate.engine.*

/** Settings for this phone only (look, effects, updates). They are not part of the budget or its backups. */
class Prefs(context: Context) {
    private val appContext: Context = context.applicationContext ?: context
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
            sp.edit().putBoolean("effects", DeviceClass.effectsByDefault(context)).apply()
        }
        return Look(sp.getString("themeMode", "system")!!, sp.getBoolean("dynamicColor", true), sp.getString("palette", "iris")!!, sp.getBoolean("effects", true))
    }

    fun setLook(l: Look) {
        sp.edit().putString("themeMode", l.themeMode).putBoolean("dynamicColor", l.dynamicColor).putString("palette", l.palette).putBoolean("effects", l.effects).apply()
        _look.value = l
    }

    /** worked out fresh each time, so it stays right after an Android update */
    val effectsReason: String get() = DeviceClass.describe(appContext)

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

    /** whether LoonieMate has asked about notifications yet, and until when the "notifications are off" card stays hidden */
    var notifAsked: Boolean
        get() = sp.getBoolean("notifAsked", false)
        set(v) { sp.edit().putBoolean("notifAsked", v).apply() }
    var notifSnoozeUntil: Long
        get() = sp.getLong("notifSnoozeUntil", 0)
        set(v) { sp.edit().putLong("notifSnoozeUntil", v).apply() }

    /** the newest version an "update available" notification has been shown for (once per version) */
    var notifiedVersion: Int
        get() = sp.getInt("notifiedVersion", 0)
        set(v) { sp.edit().putInt("notifiedVersion", v).apply() }

    /* ---- reminders (Stage 6 adds the settings card; until then these are the defaults) ---- */
    var remindersOn: Boolean
        get() = sp.getBoolean("remindersOn", true)
        set(v) { sp.edit().putBoolean("remindersOn", v).apply() }
    /** minutes after midnight, comma separated, first reminder first */
    var reminderTimes: String
        get() = sp.getString("reminderTimes", "510,1140")!!
        set(v) { sp.edit().putString("reminderTimes", v).apply() }
    /** Saturday and Sunday times, or empty for the same every day */
    var reminderWeekendTimes: String
        get() = sp.getString("reminderWeekendTimes", "")!!
        set(v) { sp.edit().putString("reminderWeekendTimes", v).apply() }
    var padDays: Int
        get() = sp.getInt("padDays", 2)
        set(v) { sp.edit().putInt("padDays", v).apply() }
    var movesEveningBefore: Boolean
        get() = sp.getBoolean("movesEveningBefore", false)
        set(v) { sp.edit().putBoolean("movesEveningBefore", v).apply() }
    private fun times(t: String) = t.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 0..1439 }.take(3)
    fun reminderConfig(): Reminders.Config = if (!remindersOn) Reminders.Config(times = emptyList())
        else Reminders.Config(times(reminderTimes), times(reminderWeekendTimes).ifEmpty { null }, padDays, movesEveningBefore)
    /** when the next reminder should fire and when the last one did (for spotting a phone that delays them, Stage 7) */
    var reminderDueAt: Long
        get() = sp.getLong("reminderDueAt", 0)
        set(v) { sp.edit().putLong("reminderDueAt", v).apply() }
    var reminderFiredAt: Long
        get() = sp.getLong("reminderFiredAt", 0)
        set(v) { sp.edit().putLong("reminderFiredAt", v).apply() }
    /** the waking day and slot of the reminder on screen ("day|slot|slots"), so ticking things off can update it */
    var reminderShown: String?
        get() = sp.getString("reminderShown", null)
        set(v) { sp.edit().putString("reminderShown", v).apply() }

    var group: String
        get() = sp.getString("billGroup", "adults")!!
        set(v) { sp.edit().putString("billGroup", v).apply() }
}
