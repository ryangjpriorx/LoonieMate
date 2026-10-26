package looniemate.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class AccountsTest {
    private fun settings() = Json.parse("""{"accounts":[
        {"id":"a1","name":"TD Chequing","type":"chequing","purposes":["main","rent"],"note":"from a newer web version"},
        {"id":"a2","name":"Koho","type":"prepaid","purposes":["everyday"]},
        {"id":"a3","name":"EQ Savings","type":"savings","purposes":[],"kind":"tfsa"}],
        "funds":[{"key":"emergency","account":"a3"},{"key":"vet","name":"Vet fund","account":"a3"}],
        "incomes":[{"id":"i1","name":"ODSP","account":"a1"},{"id":"i2","name":"Work","account":"a2"}],
        "credits":[{"id":"c1","type":"ccb","account":"a1"}]}""") as Obj

    private fun purposes(list: List<J>, id: String) = list.first { it.s("id") == id }["purposes"].arr().map { str(it) }

    @Test fun tickingAPurposeMovesItOffOtherAccounts() {
        val list = Accounts.saved(settings(), "a2", "Koho", "prepaid", "", listOf("everyday", "main"))
        assertEquals(listOf("main", "everyday"), purposes(list, "a2"))
        assertEquals(listOf("rent"), purposes(list, "a1"))
    }

    @Test fun editingKeepsUnknownFieldsAndOrder() {
        val list = Accounts.saved(settings(), "a1", "  TD Main  ", "chequing", "", listOf("main", "rent"))
        assertEquals(listOf("a1", "a2", "a3"), list.map { it.s("id") })
        assertEquals("TD Main", list[0].s("name")); assertEquals("from a newer web version", list[0].s("note"))
    }

    @Test fun newAccountsGoAtTheEnd() {
        val list = Accounts.saved(settings(), "anew", "Cash jar", "cash", "", emptyList())
        assertEquals("anew", list.last().s("id")); assertEquals(emptyList(), purposes(list, "anew"))
        assertEquals(listOf("main", "rent"), purposes(list, "a1"))
    }

    @Test fun savingsAccountsKeepTheirKind() {
        val list = Accounts.saved(settings(), "a3", "EQ Savings", "savings", "rdsp", emptyList())
        assertEquals("rdsp", list[2].s("kind")); assertEquals("Savings · RDSP", Accounts.summary(list[2]))
        assertEquals("Chequing · Main account · Pays rent", Accounts.summary(list[0]))
    }

    @Test fun removingClearsFundsIncomeAndBenefits() {
        val s = settings()
        val patch = Accounts.removed(s, "a1")
        assertEquals(listOf("a2", "a3"), patch["accounts"].arr().map { it.s("id") })
        assertEquals(listOf("", "a2"), patch["incomes"].arr().map { it.s("account") })
        assertEquals(listOf(""), patch["credits"].arr().map { it.s("account") })
        val p3 = Accounts.removed(s, "a3")
        assertEquals(listOf("", "", ""), p3["funds"].arr().map { it.s("account") })
    }

    @Test fun countsBillsUsingAnAccount() {
        val bills = listOf(J.obj("account" to "a1"), J.obj("account" to "TD Chequing"), J.obj("account" to "a2"))
        assertEquals(2, Accounts.billsUsing(settings(), bills, "a1"))
    }
}
