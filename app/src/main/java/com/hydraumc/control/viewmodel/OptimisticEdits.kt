// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - viewmodel/OptimisticEdits.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import com.hydraumc.control.util.OptimisticRollback
import org.json.JSONObject

/**
 * The change a command made on screen before the server answered, kept so that a failed send can undo exactly that change.
 * Only the fields the command wrote are put back, and only while they still hold the value it wrote, so a delta that arrived
 * in the meantime keeps its newer value.
 */
class OptimisticEdits private constructor(private val edits: List<Pair<Int, List<OptimisticRollback.Change>>>) {
    /** The robots whose state was edited. */
    val ids: Set<Int> get() = edits.map { it.first }.toSet()

    /** Puts back the fields of every edited robot that [rawOf] still finds. */
    fun rollback(rawOf: (Int) -> JSONObject?) {
        for ((id, changes) in edits) {
            val raw = rawOf(id) ?: continue
            OptimisticRollback.restore(raw, changes)
        }
    }

    companion object {
        /** Copies the state of every id in [ids], runs [mutate] on each and records what it changed. */
        fun apply(ids: List<Int>, rawOf: (Int) -> JSONObject?, mutate: (Int) -> Unit): OptimisticEdits {
            val snapshots = ids.mapNotNull { id -> rawOf(id)?.let { id to JSONObject(it.toString()) } }
            ids.forEach { id -> if (rawOf(id) != null) mutate(id) }
            return OptimisticEdits(
                snapshots.map { (id, before) -> id to OptimisticRollback.diff(before, JSONObject(rawOf(id)?.toString() ?: "{}")) },
            )
        }
    }
}
