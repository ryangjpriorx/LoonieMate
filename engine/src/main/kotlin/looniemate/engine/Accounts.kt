package looniemate.engine

/*
 * Adding, changing and removing accounts, the same way the web version's Accounts tab does it.
 * Accounts live in settings.accounts as {id, name, type, purposes, kind}; any other fields on an
 * account (from a newer web version, say) are kept as they are.
 */
object Accounts {
    /** Keep in step with ACCT_TYPES in web/index.html. */
    val TYPES = listOf("chequing" to "Chequing", "savings" to "Savings", "prepaid" to "Prepaid card", "cash" to "Cash on hand")

    class Purpose(val key: String, val label: String, val desc: String)
    /** Keep in step with PURPOSES in web/index.html. */
    val PURPOSES = listOf(
        Purpose("main", "Main account", "Paycheques are held here and automatic bill payments come out. Money moves start from here."),
        Purpose("rent", "Pays rent", "New rent and housing bills are paid from here."),
        Purpose("everyday", "Everyday spending", "Food money and in-store purchases go here."),
        Purpose("online", "Online and subscriptions", "New subscriptions and online purchases go on this."),
    )

    /** Keep in step with SAV_KINDS in web/index.html. */
    val SAV_KINDS = listOf("hisa" to "Regular savings", "tfsa" to "TFSA", "rdsp" to "RDSP", "resp" to "RESP", "fhsa" to "FHSA", "rrsp" to "RRSP")

    fun typeLabel(t: String) = TYPES.find { it.first == t }?.second ?: ""
    fun kindLabel(k: String) = SAV_KINDS.find { it.first == k.ifEmpty { "hisa" } }?.second ?: "Regular savings"
    fun purposeLabel(p: String) = PURPOSES.find { it.key == p }?.label ?: ""

    /** The line under an account's name, like the web version: "Chequing · Main account · Pays rent" or "Savings · TFSA". */
    fun summary(a: J): String {
        val type = a.s("type")
        val rest = if (type == "savings") listOf(kindLabel(a.s("kind"))) else a["purposes"].arr().map { purposeLabel(str(it)) }.filter { it.isNotEmpty() }
        return (listOf(typeLabel(type)) + rest).filter { it.isNotEmpty() }.joinToString(" · ")
    }

    /** How many bills are paid from this account. */
    fun billsUsing(s: J?, bills: Collection<J>, id: String): Int = bills.count { Core.acctId(s, it.s("account")) == id }

    /**
     * The new account list after saving one account (a new one when [id] isn't in the list).
     * Each purpose can be on one account only, so ticking one here takes it off any other account.
     * A savings account keeps whatever purposes it had (the web version hides them for savings).
     */
    fun saved(s: J?, id: String, name: String, type: String, kind: String, purposes: List<String>): List<J> {
        val list = s?.get("accounts").arr().map { it.copy() }.toMutableList()
        val i = list.indexOfFirst { it is Obj && it.s("id") == id }
        val a = (if (i >= 0) list[i] as Obj else J.obj("id" to id))
        val before = a["purposes"].arr().map { str(it) }
        val keep = if (type == "savings") before else PURPOSES.map { it.key }.filter { it in purposes } + before.filter { p -> PURPOSES.none { it.key == p } }
        a["name"] = J.S(name.trim().take(40)); a["type"] = J.S(type); a["purposes"] = J.of(keep)
        if (type == "savings") a["kind"] = J.S(kind.ifEmpty { "hisa" })
        if (i >= 0) list[i] = a else list.add(a)
        val taken = keep.filter { p -> p != "savings" && p !in before }
        if (taken.isNotEmpty()) list.forEach { o -> if (o is Obj && o !== a) {
            val ps = o["purposes"].arr().map { str(it) }
            if (ps.any { it in taken }) o["purposes"] = J.of(ps.filter { it !in taken })
        } }
        return list
    }

    /**
     * The settings to save after removing an account: the list without it, and savings funds,
     * income and benefits that pointed at it left with no account. Bills keep their old
     * reference until they're given a new account, the same as the web version.
     */
    fun removed(s: J?, id: String): Obj {
        val clear: (Obj) -> J = { x -> (x.copy() as Obj).also { if (it.s("account") == id) it["account"] = J.S("") } }
        return J.obj(
            "accounts" to s?.get("accounts").arr().filter { !(it is Obj && it.s("id") == id) }.map { it.copy() },
            "funds" to Core.fundsOf(s).map(clear),
            "incomes" to Core.incomesOf(s).map(clear),
            "credits" to Core.creditsOf(s).map(clear),
        )
    }
}
