package looniemate.app.ui.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import looniemate.app.MainViewModel
import looniemate.app.ui.LineRow
import looniemate.app.ui.screens.SwitchRow
import looniemate.engine.Core
import looniemate.engine.Fmt
import looniemate.engine.Labels
import looniemate.engine.isDate
import looniemate.engine.isYm
import looniemate.engine.ymAdd
import looniemate.engine.ymOf
import looniemate.engine.*

/**
 * Household, province and plan dates: the web version's Household, Province and bank holidays, and Plan dates cards on
 * one page (docs/plans/polish-and-audit.md W1–W3). The province sets the bank holidays that move paydays and bills, and
 * which assistance programs and benefits can be added.
 */
@Composable
fun HouseholdSheet(vm: MainViewModel, close: () -> Unit) {
    val s = vm.settings(); val today = vm.today.value
    var name by rememberSaveable { mutableStateOf(s.s("householdName")) }
    var people by rememberSaveable { mutableStateOf(s.strList("people").joinToString(", ")) }
    var province by rememberSaveable { mutableStateOf(s.or("province", "ON")) }
    var showAll by rememberSaveable { mutableStateOf(s.t("showAllProv")) }
    var startYm by rememberSaveable { mutableStateOf(if (isYm(s["startYm"])) s.s("startYm") else ymOf(today)) }
    var trackFrom by rememberSaveable { mutableStateOf(if (isDate(s["trackFrom"])) s.s("trackFrom") else today) }
    var babyDue by rememberSaveable { mutableStateOf(s.s("babyDue")) }
    var err by rememberSaveable { mutableStateOf<String?>(null) }

    SheetTitle("Household and province")
    Text("Household", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    TextFieldRow(name, { name = it.take(40); err = null }, "Household name", hint = "Shown at the top of Today.", placeholder = "The Smiths, or Sam & Alex",
        modifier = Modifier.fillMaxWidth().testTag("hh-name"))
    TextFieldRow(people, { people = it.take(120); err = null }, "People who spend money", hint = "Separate names with commas. Lets you pick who paid when you log spending.",
        placeholder = "Sam, Alex", modifier = Modifier.fillMaxWidth().testTag("hh-people"))

    Text("Province", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
    SelectField(province, Labels.PROVINCES, { province = it; err = null }, "Province or territory",
        hint = "Sets the bank holidays that move paydays and bills, and which benefits and assistance programs you can add.")
    SwitchRow("Show programs from all provinces", "For households that live in one province and get help from another.", showAll) { page()?.mark(); showAll = it }
    val year = today.take(4).toInt()
    val hols = (Core.holidaysOf(year, province) + Core.holidaysOf(year + 1, province)).filter { it.bank && it.date >= today }.take(4)
    if (hols.isNotEmpty()) Column {
        Text("Next bank holidays (payments due then arrive the business day before)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        hols.forEach { h -> LineRow(h.name, Fmt.dLabel(h.date)) }
    }

    Text("Plan dates", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
    val months = (-12..12).map { ymAdd(ymOf(today), it) }.let { if (startYm in it) it else (it + startYm).sorted() }
    SelectField(startYm, months.map { it to Fmt.monthName(it) }, { startYm = it; err = null }, "First month of the plan", hint = "Savings targets count from this month.")
    DateField(trackFrom, { trackFrom = it; err = null }, "Start tracking bills from", hint = "Bills before this date never show as past due.")
    DateField(babyDue, { babyDue = it; err = null }, "Baby's due date (optional)", hint = "Expecting? Kids bills without their own start date begin on this date.", clearable = true)

    ErrorText(err)
    Button(onClick = { err = vm.saveHousehold(name, people, province, showAll, startYm, trackFrom, babyDue); if (err == null) close() },
        modifier = Modifier.fillMaxWidth().testTag("hh-save")) { Text("Save") }
}

@Composable
private fun page() = LocalPage.current
