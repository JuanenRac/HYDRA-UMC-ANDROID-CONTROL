// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/util/ViewerUrlTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The 3D viewer address carries the token in the fragment, never in the query. */
class ViewerUrlTest {
    @Test fun `the token goes in the fragment and not in the query`() {
        val url = ViewerUrl.build("http", "192.168.0.180", "3000", "2", "abc.def.ghi")
        assertEquals("http://192.168.0.180:3000/?hideUI=true&robotId=2#token=abc.def.ghi", url)
        assertFalse(url.substringBefore('#').contains("token"))
    }

    @Test fun `without a token there is no fragment`() {
        val url = ViewerUrl.build("https", "robot.local", "443", "1", "")
        assertFalse(url.contains('#'))
        assertTrue(url.startsWith("https://robot.local:443/"))
    }

    @Test fun `special characters in the token are encoded`() {
        val url = ViewerUrl.build("http", "10.0.0.5", "3000", "1", "a b&c=d#e")
        assertEquals("http://10.0.0.5:3000/?hideUI=true&robotId=1#token=a+b%26c%3Dd%23e", url)
    }
}
