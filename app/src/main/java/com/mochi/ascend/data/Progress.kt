package com.mochi.ascend.data

/**
 * Pure helpers with no state.
 *
 * Anything that touches [AppData] lives in [Snapshot], which indexes and caches
 * it. Keeping the two apart is deliberate: the old code recomputed progress by
 * linear scans on every recomposition, which made the app crawl once a plan
 * had been materialised.
 */
object Progress {

    fun value(s: Status?): Float = when (s) {
        Status.DONE -> 1f
        Status.PARTIAL -> 0.5f
        Status.SKIPPED -> 0f
        null -> 0f
    }

    /** Level grows on a soft curve so early wins feel good. */
    fun level(xp: Int): Int {
        var lvl = 1
        var need = 100
        var rest = xp
        while (rest >= need) {
            rest -= need
            lvl++
            need = (need * 1.35f).toInt()
        }
        return lvl
    }

    fun levelProgress(xp: Int): Float {
        var need = 100
        var rest = xp
        while (rest >= need) {
            rest -= need
            need = (need * 1.35f).toInt()
        }
        return (rest.toFloat() / need).coerceIn(0f, 1f)
    }
}