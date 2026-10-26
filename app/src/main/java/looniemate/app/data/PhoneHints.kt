package looniemate.app.data

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/**
 * The phone's own accessibility settings, matched to LoonieMate's switches so the setup screen can switch them on in
 * advance ("Your phone uses large text"). Only public settings are read; anything unreadable is skipped.
 * docs/plans/accessibility.md, stage 6.
 */
object PhoneHints {
    /** switch key → why it was matched */
    fun read(ctx: Context): Map<String, String> {
        val out = LinkedHashMap<String, String>(); val cfg = ctx.resources.configuration
        if (cfg.fontScale >= 1.3f) out["text"] = "Your phone uses large text."
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && cfg.fontWeightAdjustment > 0) out["bold"] = "Your phone uses bold text."
        if (Build.VERSION.SDK_INT >= 34) try { val um = ctx.getSystemService(android.app.UiModeManager::class.java); if (um != null && um.contrast > 0f) out["contrast"] = "Your phone uses more contrast." } catch (_: Exception) {}
        try { if (Settings.Secure.getInt(ctx.contentResolver, "accessibility_display_daltonizer_enabled", 0) == 1) out["colorBlind"] = "Your phone uses colour correction." } catch (_: Exception) {}
        try { if (Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f) out["motion"] = "Your phone has animations turned off." } catch (_: Exception) {}
        if (ctx.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true) out["moreTime"] = "TalkBack is on."
        return out
    }

    /** The switches with the phone's matches turned on. */
    fun applied(a: Prefs.Access, hints: Map<String, String>): Prefs.Access = a.copy(
        textSize = if ("text" in hints && a.textSize == 0) 1 else a.textSize, bold = a.bold || "bold" in hints, contrast = a.contrast || "contrast" in hints,
        colorBlind = a.colorBlind || "colorBlind" in hints, moreTime = a.moreTime || "moreTime" in hints)
}
