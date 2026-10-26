package looniemate.app.data

import looniemate.engine.J
import looniemate.engine.n
import looniemate.engine.round2
import looniemate.engine.truthy

/**
 * Ticking a bill paid or a money move done, in one place, so Today's ticks and the reminder notification's
 * Paid / Moved it buttons always do exactly the same thing (months/<ym>.paid[key], .moves[key], and the
 * savings total in .moved for moves into or out of a savings fund).
 */
object Ticks {
    fun isPaid(repo: Repository, ym: String, key: String) = truthy(repo.get("months/$ym")?.get("paid")?.get(key))
    fun isMoved(repo: Repository, ym: String, key: String) = truthy(repo.get("months/$ym")?.get("moves")?.get(key))

    /** Returns false when it was already that way. */
    fun setPaid(repo: Repository, ym: String, key: String, paid: Boolean): Boolean {
        if (isPaid(repo, ym, key) == paid) return false
        repo.update("months/$ym", J.obj("paid" to J.obj(key to paid))); return true
    }

    /** [sav] is the move's savings code ("emergency", "child-out", a fund key…), or empty for a move between accounts. */
    fun setMoved(repo: Repository, ym: String, key: String, sav: String, amount: Double, done: Boolean): Boolean {
        if (isMoved(repo, ym, key) == done) return false
        val patch = J.obj("moves" to J.obj(key to done))
        if (sav.isNotEmpty()) { val k = sav.replace("-out", ""); val amt = amount * (if (sav.endsWith("-out")) -1 else 1)
            patch["moved"] = J.obj(k to round2(repo.get("months/$ym")?.get("moved").n(k) + if (done) amt else -amt)) }
        repo.update("months/$ym", patch); return true
    }
}
