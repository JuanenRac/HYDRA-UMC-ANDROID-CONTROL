// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/main/java/com/hydraumc/control/util/OptimisticRollback.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.util

import org.json.JSONObject

/**
 * Field-level undo for an optimistic edit.
 *
 * A command is applied to the local robot state before the server answers.
 * When it fails, only the fields that command itself changed are put back,
 * and only if nobody else has touched them since (a server delta, another
 * jog that succeeded). Restoring a whole-object snapshot would overwrite
 * those newer values with stale ones.
 */
object OptimisticRollback {

    /** A single leaf change: the path, its value before and its value right after the edit. */
    class Change(val path: List<String>, val before: Any?, val after: Any?)

    /** The leaf-level differences between two copies of the same state (objects are walked, arrays are leaves). */
    fun diff(before: JSONObject, after: JSONObject): List<Change> {
        val out = ArrayList<Change>()
        walk(before, after, emptyList(), out)
        return out
    }

    private fun walk(before: JSONObject?, after: JSONObject?, prefix: List<String>, out: MutableList<Change>) {
        val keys = LinkedHashSet<String>()
        before?.keys()?.forEach { keys.add(it) }
        after?.keys()?.forEach { keys.add(it) }
        for (key in keys) {
            val b = if (before != null && before.has(key)) before.get(key) else null
            val a = if (after != null && after.has(key)) after.get(key) else null
            if (b is JSONObject && a is JSONObject) {
                walk(b, a, prefix + key, out)
            } else if (!same(b, a)) {
                out.add(Change(prefix + key, b, a))
            }
        }
    }

    private fun same(a: Any?, b: Any?): Boolean {
        if (a == null || b == null) return a == null && b == null
        if (a is Number && b is Number) return a.toDouble() == b.toDouble()
        return a.toString() == b.toString()
    }

    /**
     * Puts back every [changes] entry whose current value in [live] is still
     * the value the edit wrote. Returns how many fields were restored.
     */
    fun restore(live: JSONObject, changes: List<Change>): Int {
        var restored = 0
        for (c in changes) {
            var node: JSONObject = live
            var ok = true
            for (i in 0 until c.path.size - 1) {
                val next = node.optJSONObject(c.path[i])
                if (next == null) { ok = false; break }
                node = next
            }
            if (!ok) continue
            val leaf = c.path.last()
            val current = if (node.has(leaf)) node.get(leaf) else null
            if (!same(current, c.after)) continue
            if (c.before == null) node.remove(leaf) else node.put(leaf, c.before)
            restored++
        }
        return restored
    }
}
