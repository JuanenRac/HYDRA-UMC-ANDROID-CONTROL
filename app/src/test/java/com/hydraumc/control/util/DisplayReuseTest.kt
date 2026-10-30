package com.hydraumc.control.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class DisplayReuseTest {
    private data class View(val id: Int, val value: Int)
    private data class Row(val id: Int, val value: Int)

    private var conversions = 0
    private val convert: (View) -> Row = { conversions++; Row(it.id, it.value) }

    private fun run(prev: List<Row>, views: List<View>, changed: Set<Int>?) =
        reuseUnchangedRows(prev, views, changed, { it.id }, { it.id }, convert)

    @Test
    fun nullChangedSetConvertsEveryRow() {
        val prev = listOf(Row(1, 1), Row(2, 2))
        val out = run(prev, listOf(View(1, 1), View(2, 2)), null)
        assertEquals(2, conversions)
        assertNotSame(prev[0], out[0])
    }

    @Test
    fun onlyTheChangedRowIsConverted() {
        val prev = listOf(Row(1, 1), Row(2, 2), Row(3, 3))
        val out = run(prev, listOf(View(1, 1), View(2, 20), View(3, 3)), setOf(2))
        assertEquals(1, conversions)
        assertSame(prev[0], out[0])
        assertEquals(Row(2, 20), out[1])
        assertSame(prev[2], out[2])
    }

    @Test
    fun aNewRobotIsConvertedEvenWhenNotListed() {
        val prev = listOf(Row(1, 1))
        val out = run(prev, listOf(View(1, 1), View(5, 9)), emptySet())
        assertEquals(1, conversions)
        assertEquals(listOf(Row(1, 1), Row(5, 9)), out)
    }

    @Test
    fun aRemovedRobotDisappearsAndOrderFollowsTheViews() {
        val prev = listOf(Row(1, 1), Row(2, 2))
        val out = run(prev, listOf(View(2, 2)), emptySet())
        assertEquals(0, conversions)
        assertEquals(listOf(Row(2, 2)), out)
    }
}
