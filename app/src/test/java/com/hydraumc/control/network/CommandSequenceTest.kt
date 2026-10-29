package com.hydraumc.control.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CommandSequenceTest {
    @Before
    fun fresh() = CommandSequence.reset()

    @Test
    fun `motion commands and the stop are numbered, nothing else`() {
        for (c in listOf("jog", "table", "joints", "stop")) assertTrue(c, CommandSequence.isNumbered(c))
        for (c in listOf("home", "speed", "points", "")) assertFalse(c, CommandSequence.isNumbered(c))
    }

    @Test
    fun `the number follows the clock and grows even when the clock stands still or goes back`() {
        assertEquals(1000L, CommandSequence.next { 1000L })
        assertEquals(1001L, CommandSequence.next { 1000L })
        assertEquals(1002L, CommandSequence.next { 500L })
        assertEquals(5000L, CommandSequence.next { 5000L })
    }
}
