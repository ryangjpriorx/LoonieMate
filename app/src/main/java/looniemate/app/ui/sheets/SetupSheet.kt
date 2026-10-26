package looniemate.app.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import looniemate.app.MainViewModel
import looniemate.app.ui.ClickRow
import looniemate.app.ui.EmptyNote
import looniemate.app.ui.Sheet
import looniemate.engine.Fmt
import looniemate.engine.*

/** How many steps first-time setup has; the last one is the getting-on-track steps. */
const val SETUP_STEPS = 7

/**
 * First-time setup (docs/plans/overhaul.md, Stage 7): one thing per page, each one the same form Settings uses, with
 * "Step N of 7", Skip and Next. Where someone got to is saved, so Welcome can pick up from there. The last step is
 * getting on track from today.
 */
@Composable
fun SetupSheet(vm: MainViewModel, step: Int, open: (Sheet) -> Unit, replace: (Sheet) -> Unit, close: () -> Unit) {
    LaunchedEffect(step) { vm.prefs.setupStep = step }
    val next: () -> Unit = { replace(if (step + 1 >= SETUP_STEPS) Sheet.CatchUp(first = true) else Sheet.Setup(step + 1)) }
    val title = when (step) { 1 -> "Your household"; 2 -> "Your bank accounts"; 3 -> "Money coming in"; 4 -> "Your bills"; 5 -> "Food money"; else -> "Money for yourself" }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Step $step of $SETUP_STEPS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            TextButton(onClick = next, modifier = Modifier.testTag("setup-skip")) { Text(if (step == 1) "Skip" else "Skip this step") }
        }
        LinearProgressIndicator(progress = { step / SETUP_STEPS.toFloat() }, modifier = Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(4.dp))
    @Composable fun Lead(t: String) = Text(t, style = MaterialTheme.typography.bodyMedium)
    @Composable fun NextButton(label: String = "Next") = Button(onClick = next, modifier = Modifier.fillMaxWidth().testTag("setup-next")) { Text(label) }
    when (step) {
        1 -> {
            Lead("A few things about your household, so dates and programs match where you live.")
            ClickRow({ open(Sheet.Import) }) {
                Column(Modifier.weight(1f).testTag("setup-import")) { Text("Moving from another phone or the web version?", fontWeight = FontWeight.SemiBold)
                    Text("Open your backup instead, and everything comes across.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            HouseholdSheet(vm, next)
        }
        2 -> { Lead("Add the accounts your money goes through: chequing, savings, and any credit cards. Balances come later.")
            looniemate.app.ui.screens.AccountsCard(vm, open, bare = true); NextButton() }
        3 -> { Lead("Paycheques, social assistance, EI and benefits. LoonieMate plans each one as it comes in.")
            looniemate.app.ui.screens.IncomeCard(vm, open, bare = true); NextButton() }
        4 -> { Lead("Rent, phone, insurance, anything that comes back regularly. You can add more any time on the Bills tab.")
            val bm by vm.billsModel.collectAsStateWithLifecycle()
            val rows = bm?.rows?.values?.flatten().orEmpty().sortedBy { it.bill.s("name").lowercase() }
            if (rows.isEmpty()) EmptyNote("No bills yet.")
            rows.forEach { r -> ClickRow({ open(Sheet.Bill(r.id, r.bill.or("group", "adults"))) }) {
                Text(r.bill.s("name"), modifier = Modifier.weight(1f)); Text(Fmt.money(r.bill.n("amount")), style = MaterialTheme.typography.bodyMedium) } }
            FilledTonalButton(onClick = { open(Sheet.Bill(null, "adults")) }, modifier = Modifier.testTag("setup-add-bill")) { Text("+ Add a bill") }
            NextButton() }
        5 -> { Lead("How much goes to groceries each pay period.")
            FoodSettings(vm, vm.today.value.take(7), vm.settings(), bare = true); NextButton() }
        else -> SelfPlanEditor(vm, "Next", onSaved = next)
    }
    SheetTitle(title)
}
