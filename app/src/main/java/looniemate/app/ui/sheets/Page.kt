@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package looniemate.app.ui.sheets

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/**
 * What a page knows about itself: its title (set by [SheetTitle]) and whether anything has been typed or changed that
 * hasn't been saved yet ([edited], set by the shared fields in Fields.kt). docs/plans/pages-and-blanks.md.
 */
class PageState {
    var title by mutableStateOf("")
    var edited by mutableStateOf(false)
    /** Something on the page changed. */
    fun mark() { edited = true }
    /** What was typed so far is saved (a multi-step flow moved on), so leaving now loses nothing. */
    fun saved() { edited = false }

    companion object {
        /** Keeps "something was typed" if Android closes LoonieMate in the background (the title is set again on return). */
        val Saver = androidx.compose.runtime.saveable.Saver<PageState, Boolean>(save = { it.edited }, restore = { PageState().apply { edited = it } })
    }
}

val LocalPage = staticCompositionLocalOf<PageState?> { null }

/**
 * A full-screen page for a form. It never closes by itself: no swipe and no tap outside. The ✕ in the top bar and
 * Android's back gesture close it, and if anything was typed, they ask first. Inner back handlers (a multi-step
 * flow going back a step) come first because they're registered after this one.
 */
@Composable
fun Page(onClose: () -> Unit, back: Boolean = false, content: @Composable () -> Unit) {
    val page = rememberSaveable(saver = PageState.Saver) { PageState() }
    var asking by rememberSaveable { mutableStateOf(false) }
    val tryClose: () -> Unit = { if (page.edited) asking = true else onClose() }
    BackHandler(onBack = tryClose)
    CompositionLocalProvider(LocalPage provides page) {
        Scaffold(
            modifier = Modifier.fillMaxSize().testTag("page"),
            topBar = {
                TopAppBar(
                    title = { Text(page.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() }) },
                    navigationIcon = { IconButton(onClick = tryClose, modifier = Modifier.testTag("page-close")) { if (back) Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") else Icon(Icons.Rounded.Close, "Close") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            },
        ) { pad ->
            Column(Modifier.fillMaxWidth().padding(pad).consumeWindowInsets(pad).imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                content()
            }
        }
    }
    if (asking) LeaveDialog(stay = { asking = false }, leave = { asking = false; onClose() })
}

/** "Leave without saving?" Only a button answers it; tapping outside does nothing. */
@Composable
fun LeaveDialog(stay: () -> Unit, leave: () -> Unit) = AlertDialog(
    onDismissRequest = stay,
    properties = DialogProperties(dismissOnClickOutside = false),
    title = { Text("Leave without saving?") },
    text = { Text("What you typed on this page won't be kept.") },
    confirmButton = { TextButton(onClick = stay, modifier = Modifier.testTag("leave-stay")) { Text("Keep going") } },
    dismissButton = { TextButton(onClick = leave, modifier = Modifier.testTag("leave-go")) { Text("Leave") } },
)

/** The page's title, shown in the top bar. Kept as a composable so every form names itself the same way. */
@Composable
fun SheetTitle(t: String) { val page = LocalPage.current
    if (page != null) SideEffect { page.title = t } else Text(t, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() }) }

/** Wraps a field's change so the page knows something was typed. */
@Composable
fun <T> marking(onChange: (T) -> Unit): (T) -> Unit { val page = LocalPage.current; return { v -> page?.mark(); onChange(v) } }

/* ---------- saved state, so a form survives Android closing LoonieMate in the background (polish-and-audit S1) ---------- */

/** Saves a map of typed values (text or true/false) with the page. */
fun <V : Any> stateMapSaver() = androidx.compose.runtime.saveable.Saver<androidx.compose.runtime.snapshots.SnapshotStateMap<String, V>, ArrayList<Any>>(
    save = { m -> ArrayList<Any>().apply { m.forEach { (k, v) -> add(k); add(v) } } },
    restore = { l -> androidx.compose.runtime.mutableStateMapOf<String, V>().apply { l.chunked(2).forEach { @Suppress("UNCHECKED_CAST") put(it[0] as String, it[1] as V) } } })

/** Saves a list of text values with the page. */
fun stringListSaver() = androidx.compose.runtime.saveable.Saver<androidx.compose.runtime.snapshots.SnapshotStateList<String>, ArrayList<String>>(
    save = { ArrayList(it) }, restore = { androidx.compose.runtime.mutableStateListOf<String>().apply { addAll(it) } })
