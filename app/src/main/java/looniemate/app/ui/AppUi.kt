@file:OptIn(ExperimentalMaterial3Api::class)

package looniemate.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import looniemate.app.MainViewModel
import looniemate.app.ui.screens.BillsScreen
import looniemate.app.ui.screens.MonthScreen
import looniemate.app.ui.screens.MoreScreen
import looniemate.app.ui.screens.SpendingScreen
import looniemate.app.ui.screens.SavingsScreen
import looniemate.app.ui.screens.TodayScreen
import looniemate.app.ui.sheets.Sheets
import looniemate.app.ui.theme.LocalExtra
import looniemate.app.ui.theme.LocalFx
import looniemate.engine.Fmt
import looniemate.engine.diffDays
import looniemate.engine.ymAdd
import looniemate.engine.*

enum class Tab(val label: String, val icon: ImageVector) {
    Today("Today", Icons.Rounded.Today), Month("Month", Icons.Rounded.CalendarMonth), Bills("Bills", Icons.Rounded.Receipt),
    Spending("Spending", Icons.Rounded.ShoppingCart), Savings("Savings", Icons.Rounded.Savings), More("Settings", Icons.Rounded.Settings)
}

/**
 * Which page (form) is open, if any. Data classes and data objects, so the same page compares equal after Android
 * closes LoonieMate in the background and it's rebuilt from saved state ([SheetSaver]); that keeps each page's own saved
 * fields matched up with it.
 */
sealed class Sheet {
    data class Log(val oneTime: Boolean = false, val category: String = "") : Sheet()
    data class Food(val ym: String) : Sheet()
    data class Bill(val id: String?, val group: String) : Sheet()
    data class Adjust(val ym: String, val id: String, val defer: Boolean) : Sheet()
    data class Transfer(val kind: String, val amount: Double?) : Sheet()
    data class Account(val id: String?) : Sheet()
    data object Checkin : Sheet()
    data class Fund(val key: String?) : Sheet()
    data class UseFund(val key: String) : Sheet()
    data class Ccb(val ym: String) : Sheet()
    data class Income(val id: String?, val assist: String? = null) : Sheet()
    data class Credit(val id: String?, val type: String? = null) : Sheet()
    data class MonthIncome(val ym: String) : Sheet()
    data object Update : Sheet()
    data class CatchUp(val first: Boolean) : Sheet()
    /** Make LoonieMate easier to use? [next] opens afterwards (first-time setup goes on to the getting-on-track steps). */
    data class Access(val next: Sheet? = null) : Sheet()
    /** Personal Expenses: this month and the plan (docs/plans/personal-expenses.md). */
    data object Self : Sheet()
    /** Household name and people, province, and plan dates (Settings). */
    data object Household : Sheet()
    data object Export : Sheet()
    /** Settings → Reset LoonieMate: type RESET to erase everything on this phone. */
    data object Reset : Sheet()
    data object Import : Sheet()
}

/** Saves which page is open as plain values (polish-and-audit S1). */
private fun sheetToList(s: Sheet): ArrayList<Any?> = when (s) {
    is Sheet.Log -> arrayListOf("Log", s.oneTime, s.category); is Sheet.Food -> arrayListOf("Food", s.ym); is Sheet.Bill -> arrayListOf("Bill", s.id, s.group)
    is Sheet.Adjust -> arrayListOf("Adjust", s.ym, s.id, s.defer); is Sheet.Transfer -> arrayListOf("Transfer", s.kind, s.amount); is Sheet.Account -> arrayListOf("Account", s.id)
    Sheet.Checkin -> arrayListOf("Checkin"); is Sheet.Fund -> arrayListOf("Fund", s.key); is Sheet.UseFund -> arrayListOf("UseFund", s.key); is Sheet.Ccb -> arrayListOf("Ccb", s.ym)
    is Sheet.Income -> arrayListOf("Income", s.id, s.assist); is Sheet.Credit -> arrayListOf("Credit", s.id, s.type); is Sheet.MonthIncome -> arrayListOf("MonthIncome", s.ym)
    Sheet.Update -> arrayListOf("Update"); is Sheet.CatchUp -> arrayListOf("CatchUp", s.first); is Sheet.Access -> arrayListOf("Access", s.next?.let { sheetToList(it) })
    Sheet.Self -> arrayListOf("Self"); Sheet.Export -> arrayListOf("Export"); Sheet.Import -> arrayListOf("Import"); Sheet.Reset -> arrayListOf("Reset"); Sheet.Household -> arrayListOf("Household")
}
private fun listToSheet(l: List<Any?>): Sheet? = try { when (l[0]) {
    "Log" -> Sheet.Log(l[1] as Boolean, l[2] as String); "Food" -> Sheet.Food(l[1] as String); "Bill" -> Sheet.Bill(l[1] as String?, l[2] as String)
    "Adjust" -> Sheet.Adjust(l[1] as String, l[2] as String, l[3] as Boolean); "Transfer" -> Sheet.Transfer(l[1] as String, l[2] as Double?); "Account" -> Sheet.Account(l[1] as String?)
    "Checkin" -> Sheet.Checkin; "Fund" -> Sheet.Fund(l[1] as String?); "UseFund" -> Sheet.UseFund(l[1] as String); "Ccb" -> Sheet.Ccb(l[1] as String)
    "Income" -> Sheet.Income(l[1] as String?, l[2] as String?); "Credit" -> Sheet.Credit(l[1] as String?, l[2] as String?); "MonthIncome" -> Sheet.MonthIncome(l[1] as String)
    "Update" -> Sheet.Update; "CatchUp" -> Sheet.CatchUp(l[1] as Boolean); "Access" -> Sheet.Access((l[1] as List<*>?)?.let { listToSheet(it) })
    "Self" -> Sheet.Self; "Export" -> Sheet.Export; "Import" -> Sheet.Import; "Reset" -> Sheet.Reset; "Household" -> Sheet.Household; else -> null
} } catch (_: Exception) { null }
private val SheetSaver = androidx.compose.runtime.saveable.Saver<Sheet?, ArrayList<Any?>>(save = { it?.let(::sheetToList) }, restore = { listToSheet(it) })

@Composable
fun LoonieMateApp(vm: MainViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.Today) }
    var sheet by rememberSaveable(stateSaver = SheetSaver) { mutableStateOf<Sheet?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val today by vm.today.collectAsStateWithLifecycle()
    val ymSel by vm.ym.collectAsStateWithLifecycle()
    val todayModel by vm.todayModel.collectAsStateWithLifecycle()
    val lastBackup by vm.prefs.lastBackupFlow.collectAsStateWithLifecycle()

    /* More time: messages (and their Undo) stay until closed */
    val moreTime by androidx.compose.runtime.rememberUpdatedState(looniemate.app.ui.theme.LocalAccess.current.moreTime)
    LaunchedEffect(Unit) {
        vm.snack.collect { u ->
            val r = snackbar.showSnackbar(u.message, actionLabel = if (u.paths.isNotEmpty()) "Undo" else null, withDismissAction = moreTime,
                duration = if (moreTime) SnackbarDuration.Indefinite else SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) vm.undo(u)
        }
    }
    BackHandler(enabled = tab != Tab.Today) { tab = Tab.Today }
    val openUpdate by vm.openUpdate.collectAsStateWithLifecycle()
    /* first launch with an empty budget: the accessibility choices come first (docs/plans/accessibility.md) */
    LaunchedEffect(todayModel?.isNew) { if (todayModel?.isNew == true && !vm.prefs.a11yAsked && sheet == null) sheet = Sheet.Access() }
    /* after Reset LoonieMate: back to Today, where first-time setup starts again */
    val resets by vm.resets.collectAsStateWithLifecycle()
    LaunchedEffect(resets) { if (resets > 0) tab = Tab.Today }
    LaunchedEffect(openUpdate) { openUpdate?.let { w -> vm.openUpdate.value = null; sheet = Sheet.Update; vm.openedForUpdate(w == "install") } }

    val open: (Sheet) -> Unit = { sheet = it }
    val monthTabs = tab == Tab.Month || tab == Tab.Bills || tab == Tab.Spending || tab == Tab.Savings
    val shownYm = ymSel ?: vm.shownYm()
    val big = bigText()
    val syncState by vm.sync.state.collectAsStateWithLifecycle()
    val syncLine = when (val s = syncState) { is looniemate.app.sync.SyncManager.State.Syncing -> "Syncing…"
        is looniemate.app.sync.SyncManager.State.Error -> "Sync needs attention · see Settings"
        is looniemate.app.sync.SyncManager.State.Ok -> looniemate.app.ui.screens.syncedAgo(s.at); else -> "" }

    /* an open form is a full-screen page over the app; the app underneath is hidden from screen readers meanwhile */
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        modifier = if (sheet != null) Modifier.clearAndSetSemantics {} else Modifier,
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = {
                        if (monthTabs) Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { vm.ym.value = ymAdd(shownYm, -1) }) { Icon(Icons.Rounded.ChevronLeft, "Previous month") }
                            TextButton(onClick = { vm.ym.value = null }) { Text(Fmt.monthName(shownYm), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                            IconButton(onClick = { vm.ym.value = ymAdd(shownYm, 1) }) { Icon(Icons.Rounded.ChevronRight, "Next month") }
                        } else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (tab == Tab.More) "Settings" else todayModel?.title ?: "LoonieMate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            if (tab == Tab.Today && !big) { val days = lastBackup?.let { diffDays(it.take(10), today) }
                                Text(if (vm.sync.on) syncLine else "Saved on this phone · " + when (days) { null -> "not backed up yet"; 0 -> "backed up today"; 1 -> "backed up yesterday"; else -> "backed up $days days ago" },
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    },
                    navigationIcon = { if (tab == Tab.More) IconButton(onClick = { tab = Tab.Today }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                    actions = { if (tab != Tab.More) IconButton(onClick = { tab = Tab.More }, modifier = Modifier.testTag("tab-More")) { Icon(Icons.Rounded.Settings, "Settings") } },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
                AccentLine()
            }
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.filter { it != Tab.More }.forEach { t ->
                    /* at large text sizes the names don't fit five across: icons only, each read out by its name */
                    NavigationBarItem(selected = tab == t, onClick = { tab = t }, icon = { Icon(t.icon, if (big) t.label else null) },
                        label = if (big) null else ({ Text(t.label, maxLines = 1, softWrap = false) }), modifier = Modifier.testTag("tab-${t.name}"))
                }
            }
        },
        floatingActionButton = {
            when (tab) {
                Tab.Today, Tab.Month, Tab.Spending -> ExtendedFloatingActionButton(onClick = { open(Sheet.Log()) }, modifier = Modifier.testTag("fab"), icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Log spending") })
                Tab.Bills -> ExtendedFloatingActionButton(onClick = { open(Sheet.Bill(null, vm.prefs.group)) }, modifier = Modifier.testTag("fab"), icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Add bill") })
                else -> {}
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        val content = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 12.dp, bottom = pad.calculateBottomPadding() + 96.dp)
        Box(Modifier.fillMaxSize()) {
            if (LocalFx.current) AnimatedContent(tab, transitionSpec = {
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (fadeIn() + slideInHorizontally { it / 12 * dir }) togetherWith (fadeOut() + slideOutHorizontally { -it / 12 * dir })
            }, label = "tab") { t -> Screen(t, vm, content, open) { tab = it } }
            else Screen(tab, vm, content, open) { tab = it }
        }
    }
    /* the page slides up a little and fades in (just appears with Less motion); "last" keeps it drawn while it fades out */
    val last = remember { arrayOfNulls<Sheet>(1) }; sheet?.let { last[0] = it }
    if (LocalFx.current) AnimatedVisibility(sheet != null, enter = fadeIn() + slideInVertically { it / 16 }, exit = fadeOut()) {
        last[0]?.let { s -> Sheets(s, vm, onDismiss = { sheet = null }, open = open) } }
    else sheet?.let { s -> Sheets(s, vm, onDismiss = { sheet = null }, open = open) }
    }
}

@Composable
private fun Screen(t: Tab, vm: MainViewModel, pad: PaddingValues, open: (Sheet) -> Unit, go: (Tab) -> Unit) {
    when (t) {
        Tab.Today -> TodayScreen(vm, pad, open, go)
        Tab.Month -> MonthScreen(vm, pad, open)
        Tab.Bills -> BillsScreen(vm, pad, open)
        Tab.Spending -> SpendingScreen(vm, pad, open)
        Tab.Savings -> SavingsScreen(vm, pad, open)
        Tab.More -> MoreScreen(vm, pad, open)
    }
}

/** The thin colour line under the top bar; it slowly shifts colour when Visual effects is on. */
@Composable
private fun AccentLine() {
    val cs = MaterialTheme.colorScheme; val x = LocalExtra.current
    val colors = listOf(cs.primary, cs.tertiary, x.baby, x.pets, cs.primary)
    if (LocalFx.current) {
        val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "line")
        val shift by t.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(14000, easing = androidx.compose.animation.core.LinearEasing)), label = "shift")
        Box(Modifier.fillMaxWidth().height(3.dp).background(Brush.horizontalGradient(colors, startX = -2000f * shift, endX = 3000f - 2000f * shift, tileMode = androidx.compose.ui.graphics.TileMode.Mirror)))
    } else Box(Modifier.fillMaxWidth().height(3.dp).background(Brush.horizontalGradient(colors)))
}
