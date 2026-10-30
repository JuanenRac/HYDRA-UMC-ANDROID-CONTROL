// =============================================================================
// HYDRA-UMC CONTROL - Reuse of already converted display rows
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.util

/**
 * Builds the list of display rows for [views], converting only the rows whose
 * id is in [changedIds] (or that have no previous row) and reusing the
 * previous row object for every other one.
 *
 * `changedIds == null` means "anything may have changed" (a full state
 * replace): every row is converted. An empty set means nothing changed.
 * The result keeps the order of [views].
 */
fun <V, D> reuseUnchangedRows(
    previous: List<D>,
    views: List<V>,
    changedIds: Set<Int>?,
    viewId: (V) -> Int,
    rowId: (D) -> Int,
    convert: (V) -> D,
): List<D> {
    if (changedIds == null) return views.map(convert)
    val byId = HashMap<Int, D>(previous.size * 2)
    for (row in previous) byId[rowId(row)] = row
    return views.map { view ->
        val id = viewId(view)
        val old = byId[id]
        if (old == null || id in changedIds) convert(view) else old
    }
}
