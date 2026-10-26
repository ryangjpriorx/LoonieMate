@file:OptIn(ExperimentalLayoutApi::class)

package looniemate.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import looniemate.app.FundView
import looniemate.app.MainViewModel
import looniemate.app.SavingsModel
import looniemate.app.ui.ClickRow
import looniemate.app.ui.GrowBar
import looniemate.app.ui.IconBadge
import looniemate.app.ui.LineRow
import looniemate.app.ui.Money
import looniemate.app.ui.Pill
import looniemate.app.ui.SectionCard
import looniemate.app.ui.Sheet
import looniemate.app.ui.sheets.SelectField
import looniemate.app.ui.sheets.TextFieldRow
import looniemate.app.ui.theme.LocalExtra
import looniemate.app.ui.toneColors
import looniemate.engine.Core
import looniemate.engine.Fmt
import looniemate.engine.SavingsViews
import looniemate.engine.*

@Composable
fun SavingsScreen(vm: MainViewModel, pad: PaddingValues, open: (Sheet) -> Unit, state: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState()) {
    val sm by vm.savingsModel.collectAsStateWithLifecycle()
    val m = sm ?: return
    LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = pad, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SavingsCard(m, open) }
        if (m.kids && !m.s["clb"].t("hide")) item { ClbCard(m, vm) }
        if (!m.programs.isEmpty) item { ProgramsCard(m.programs) }
        item { ProjectionCard(m) }
        item { FundsCard(m, vm, open) }
    }
}

private fun fmt0(v: Double) = Fmt.money(v).removeSuffix(".00")

@Composable
private fun Muted(t: String) = Text(t, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

private fun keptIn(s: J, f: Obj): String = Core.acctById(s, f.s("account"))?.let { "In ${it.s("name")}" } ?: "No account set"

@Composable
private fun SavingsCard(m: SavingsModel, open: (Sheet) -> Unit) {
    val x = LocalExtra.current; val s = m.s; val c = m.c; val mo = Fmt.monthOnly(m.ym)
    SectionCard("Savings", Icons.Rounded.Savings, "rose", index = 0, trailingContent = {
        TextButton(onClick = { open(Sheet.Transfer("emergency", null)) }) { Text("Record a transfer") } }) {
        /* Emergency Savings */
        val em = m.funds.first { it.f.s("key") == "emergency" }; val goal = s.n("emergencyGoal")
        val scale = maxOf(goal, m.emergency, m.thr.emergency, 1.0)
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Emergency Savings", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Muted(keptIn(s, em.f)) }
        Money(m.emergency, MaterialTheme.typography.headlineMedium, countUp = true)
        GrowBar((maxOf(0.0, m.emergency) / scale).toFloat(), MaterialTheme.colorScheme.primary, marker = (maxOf(0.0, m.thr.emergency) / scale).toFloat())
        Spacer(Modifier.height(4.dp))
        Muted("Should have by end of $mo: ${Fmt.money(m.thr.emergency)}" + (if (goal > 0) " · Goal ${fmt0(goal)}" else ""))

        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        /* Child Reserve */
        val ch = m.funds.first { it.f.s("key") == "child" }; val cScale = maxOf(m.thr.child, m.child, 1.0)
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Child Reserve · protected", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Pill("Last resort only", "baby") }
        Money(m.child, MaterialTheme.typography.headlineMedium, countUp = true)
        Muted(keptIn(s, ch.f))
        Spacer(Modifier.height(4.dp))
        GrowBar((maxOf(0.0, m.child) / cScale).toFloat(), x.baby, marker = minOf(1.0, m.thr.child / cScale).toFloat())
        Spacer(Modifier.height(4.dp))
        Muted("Child Benefit kept through $mo: ${Fmt.money(m.thr.child)}")
        if (c.n("ccb") > 0) {
            Spacer(Modifier.height(6.dp))
            LineRow("$mo Child Benefit", Fmt.money(c.n("ccb"))); LineRow("To Child Reserve", Fmt.money(c.n("ccbChild"))); LineRow("To Emergency Savings", Fmt.money(c.n("ccbToEm")))
            if (!c.t("closed")) OutlinedButton(onClick = { open(Sheet.Ccb(m.ym)) }) { Text("Change the split") }
        }
        Muted("The Child Benefit never counts as spending money. If regular income ever falls short, use Emergency Savings first.")

        /* other funds */
        m.funds.filter { it.f.s("key") != "emergency" && it.f.s("key") != "child" }.forEach { fv ->
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            OtherFund(m, fv, open)
        }
    }
}

@Composable
private fun OtherFund(m: SavingsModel, fv: FundView, open: (Sheet) -> Unit) {
    val x = LocalExtra.current; val f = fv.f; val g = f.n("goal"); val isVet = Regex("vet", RegexOption.IGNORE_CASE).containsMatchIn(f.s("name"))
    Row(verticalAlignment = Alignment.CenterVertically) { Text(f.s("name"), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Muted(keptIn(m.s, f)) }
    Money(fv.balance, MaterialTheme.typography.headlineSmall)
    if (g > 0) { GrowBar((maxOf(0.0, fv.balance) / maxOf(g, fv.balance, 1.0)).toFloat(), x.pets); Spacer(Modifier.height(4.dp)); Muted("Goal ${fmt0(g)}") }
    Muted(if (fv.linked.isNotEmpty()) "Adding ${Fmt.money(fv.adding)} in ${Fmt.monthOnly(m.ym)} from ${fv.linked.joinToString(", ")}." else "No bill feeds this fund yet. On the Bills tab, set a bill to put money into this fund.")
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = { open(Sheet.UseFund(f.s("key"))) }) { Text(if (isVet) "Cash out a vet visit" else "Use money from this fund") }
        OutlinedButton(onClick = { open(Sheet.Transfer(f.s("key"), null)) }) { Text("Record a transfer") }
    }
}

@Composable
private fun ClbCard(m: SavingsModel, vm: MainViewModel) {
    val c = m.s["clb"]; val done = SavingsViews.CLB_STEPS.count { c.t(it.first) }; val ctx = LocalContext.current
    SectionCard("Canada Learning Bond · free money", Icons.Rounded.School, "baby", index = 1, trailingContent = { Pill(if (done == 3) "Set up" else "$done of 3 done", if (done == 3) "good" else "warn") }) {
        if (done == 3 && !c.t("expand")) {
            Muted("All three steps are done.")
            Row { TextButton(onClick = { vm.toggleClb("expand") }) { Text("Show") }; TextButton(onClick = { vm.toggleClb("hide") }) { Text("Hide") } }
            return@SectionCard
        }
        Text("Families with low income can get \$500 (plus \$25 toward opening the plan) put into a child's education savings, then \$100 a year up to age 15, up to \$2,000. You don't have to put in any money of your own.",
            style = MaterialTheme.typography.bodyMedium)
        SavingsViews.CLB_STEPS.forEach { (k, l) ->
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(c.t(k), { vm.toggleClb(k) }); Text(l, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f)) } }
        Row {
            TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SavingsViews.CLB_LINK))) }) { Text("Details on canada.ca") }
            TextButton(onClick = { vm.toggleClb("hide") }) { Text("Hide this") }
            if (done == 3) TextButton(onClick = { vm.toggleClb("expand") }) { Text("Fold up") }
        }
    }
}

@Composable
private fun ProgramsCard(p: SavingsViews.Programs) {
    val ctx = LocalContext.current; val x = LocalExtra.current
    SectionCard("Savings programs", Icons.Rounded.CardGiftcard, "gold", index = 2) {
        Muted("Free government money and tax-free savings")
        Spacer(Modifier.height(8.dp))
        p.rows.forEach { r ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text(r.name, fontWeight = FontWeight.SemiBold); Pill(looniemate.engine.Accounts.kindLabel(r.kind)) }
            Text(r.text, style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(8.dp))
        }
        if (p.ideas.isNotEmpty()) {
            Text("Worth looking into", fontWeight = FontWeight.SemiBold)
            p.ideas.forEach { i ->
                Text("${i.title}: ${i.text}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                if (i.link != null) TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(i.link))) }) { Text(i.linkText ?: "Details") }
            }
        }
        p.asset?.let { a ->
            Spacer(Modifier.height(10.dp))
            Text("${a.short} asset limit", fontWeight = FontWeight.SemiBold)
            val counted = a.counted
            if (counted == null) Muted("Do a balance check on the Today screen to see how close you are to the ${a.short} asset limit.")
            else {
                Muted("From your balance check on ${Fmt.dShort(a.checkDate ?: "")}")
                Spacer(Modifier.height(4.dp))
                GrowBar((a.pct / 100).toFloat(), if (a.pct >= 90) x.bad else if (a.pct >= 75) x.warn else x.good)
                Spacer(Modifier.height(4.dp))
                Muted("Counted: ${Fmt.money(counted)} of ${fmt0(a.limit)} · " + (if (a.short == "ODSP") "RDSPs and RESPs aren't counted" else "RDSPs and RESPs left out; check with your worker"))
                if (a.pct >= 75) Text("Getting close to the limit. Money in an RDSP doesn't count, if someone qualifies.", style = MaterialTheme.typography.bodySmall, color = toneColors("bad").first)
            }
        }
        if (p.rows.isNotEmpty()) { Spacer(Modifier.height(6.dp)); Muted("Estimates use money moved into savings funds kept in each account this year. Set an account's kind in Settings → Accounts.") }
    }
}

@Composable
private fun ProjectionCard(m: SavingsModel) {
    val goal = m.s.n("emergencyGoal"); val max = maxOf(goal, m.projection.maxOfOrNull { maxOf(0.0, it.n("em")) } ?: 0.0, 1.0)
    SectionCard("Next 6 months", Icons.Rounded.ShowChart, "accent", trailing = "If you stick to the plan", index = 3) {
        Muted("Emergency Savings at each month's end" + (if (goal > 0) ", against the ${fmt0(goal)} goal." else "."))
        Spacer(Modifier.height(8.dp))
        m.projection.forEach { r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Fmt.monthOnly(r.s("ym")), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (r.t("bonus")) { Pill("3 pays", "good"); Spacer(Modifier.width(6.dp)) }
                Text(Fmt.money(r.n("em")), color = if (r.n("em") < 0) toneColors("bad").first else Color.Unspecified, fontWeight = FontWeight.SemiBold)
            }
            GrowBar((maxOf(0.0, r.n("em")) / max).toFloat(), MaterialTheme.colorScheme.primary, marker = if (goal > 0) (goal / max).toFloat() else null, height = 8.dp)
            Muted("In ${Fmt.money(r.n("income"))} · bills and food ${Fmt.money(r.n("bills") + r.n("food"))} · savings ${if (r.n("toSavings") >= 0) "+" else "−"}${Fmt.money(Math.abs(r.n("toSavings")))} · Child Reserve ${Fmt.money(r.n("ch"))}")
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FundsCard(m: SavingsModel, vm: MainViewModel, open: (Sheet) -> Unit) {
    SectionCard("Savings funds", Icons.Rounded.Tune, "rose", index = 4) {
        m.funds.forEachIndexed { i, fv -> val f = fv.f; val k = f.s("key"); val goal = if (k == "emergency") m.s.n("emergencyGoal") else f.n("goal")
            androidx.compose.foundation.layout.Box(Modifier.testTag("fund-$i")) {
                looniemate.app.ui.MenuRow(listOf(looniemate.app.ui.MenuItem("Record a transfer") { open(Sheet.Transfer(k, null)) },
                    looniemate.app.ui.MenuItem(if (k == "child") "Take money out" else "Use money from it") { if (k == "child") open(Sheet.Transfer(k, null)) else open(Sheet.UseFund(k)) },
                    looniemate.app.ui.MenuItem("Change") { open(Sheet.Fund(k)) }), onClick = { open(Sheet.Fund(k)) }, clickLabel = "Change", vertical = 8.dp) {
                    IconBadge(Icons.Rounded.Savings, if (k == "child") "baby" else if (k == "emergency") "accent" else "pets", 32.dp)
                    Column(Modifier.weight(1f)) {
                        Text(f.s("name"), fontWeight = FontWeight.SemiBold)
                        Muted(keptIn(m.s, f) + (if (goal > 0 && k != "child") " · goal ${fmt0(goal)}" else ""))
                    }
                    Icon(Icons.Rounded.ChevronRight, "Edit ${f.s("name")}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(onClick = { open(Sheet.Fund(null)) }) { Text("+ Add a blank fund") }
        Spacer(Modifier.height(14.dp))
        Text("Add a ready-made fund", fontWeight = FontWeight.SemiBold)
        Muted("For costs that come every year and catch families off guard. Each one adds a monthly savings amount you can change on the Bills tab. It's tagged non-essential, so it can be skipped in a tight month.")
        var pick by remember { mutableStateOf(SavingsViews.READY.first().key) }
        Spacer(Modifier.height(6.dp))
        SelectField(pick, SavingsViews.READY.map { it.key to "${it.name} · about ${fmt0(it.yearly)} a year" }, { pick = it }, "Ready-made fund")
        Button(onClick = { SavingsViews.READY.find { it.key == pick }?.let { vm.addReadyFund(it) } }) { Text("Add") }
    }
}

