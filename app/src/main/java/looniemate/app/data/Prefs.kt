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

    /**
     * Accessibility switches (Settings → Accessibility; this phone only, not in the budget or backups).
     * textSize: 0 Normal, 1 Large, 2 Larger, 3 Largest, on top of the phone's own size. Less motion is `Look.effects`
     * shown the other way round. See docs/plans/accessibility.md.
     */
    data class Access(val textSize: Int = 0, val bold: Boolean = false, val font: Boolean = false, val contrast: Boolean = false,
                      val colorBlind: Boolean = false, val moreTime: Boolean = false, val bigButtons: Boolean = false)
    private val _access = MutableStateFlow(Access(sp.getInt("a11yText", 0).coerceIn(0, 3), sp.getBoolean("a11yBold", false), sp.getBoolean("a11yFont", false),
        sp.getBoolean("a11yContrast", false), sp.getBoolean("a11yColorBlind", false), sp.getBoolean("a11yMoreTime", false), sp.getBoolean("a11yBigButtons", false)))
    val access: StateFlow<Access> = _access
    fun setAccess(a: Access) {
        sp.edit().putInt("a11yText", a.textSize).putBoolean("a11yBold", a.bold).putBoolean("a11yFont", a.font).putBoolean("a11yContrast", a.contrast)
            .putBoolean("a11yColorBlind", a.colorBlind).putBoolean("a11yMoreTime", a.moreTime).putBoolean("a11yBigButtons", a.bigButtons).apply()
        _access.value = a
    }
    /** whether the accessibility choices have been offered (first-time setup, or the one-time Today card) */
    var a11yAsked: Boolean
        get() = sp.getBoolean("a11yAsked", false)
        set(v) { sp.edit().putBoolean("a11yAsked", v).apply() }
    /** the phone's own "Remove animations" is on: motion stays off whatever Less motion says (checked on each resume) */
    val phoneNoMotion = MutableStateFlow(false)

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

    /* ---- reminders (the Reminders card in Settings) ---- */
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
    private fun set(k: String): Set<String> = sp.getStringSet(k, emptySet())!!.toSet()
    private fun putSet(k: String, v: Set<String>) { sp.edit().putStringSet(k, HashSet(v)).apply() }
    /** automatic-payment heads-ups dismissed with Got it ("day|key"), rows put off with Remind me later ("key|until ms"),
     *  rows showing "Marked paid · Undo" (key), and the row notifications on screen (ids) */
    var reminderGotIt: Set<String> get() = set("remGotIt"); set(v) = putSet("remGotIt", v)
    var reminderSnoozed: Set<String> get() = set("remSnoozed"); set(v) = putSet("remSnoozed", v)
    var reminderUndo: Set<String> get() = set("remUndo"); set(v) = putSet("remUndo", v)
    var reminderChildren: Set<String> get() = set("remChildren"); set(v) = putSet("remChildren", v)

    /** how many reminders arrived more than an hour late since the "reminders are late" card was last dismissed */
    var reminderLateCount: Int
        get() = sp.getInt("reminderLateCount", 0)
        set(v) { sp.edit().putInt("reminderLateCount", v).apply() }

    /** the waking day and slot of the reminder on screen ("day|slot|slots"), so ticking things off can update it */
    var reminderShown: String?
        get() = sp.getString("reminderShown", null)
        set(v) { sp.edit().putString("reminderShown", v).apply() }

    /** whether the getting-on-track steps have been done on this phone (first-time setup) */
    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) { sp.edit().putBoolean("onboarded", v).apply() }

    var group: String
        get() = sp.getString("billGroup", "adults")!!
        set(v) { sp.edit().putString("billGroup", v).apply() }
}
