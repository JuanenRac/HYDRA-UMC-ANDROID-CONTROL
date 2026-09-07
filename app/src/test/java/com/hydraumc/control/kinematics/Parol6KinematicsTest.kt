// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - Parol6 kinematics port unit tests
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
//
// Expected values are a real oracle: HYDRA-UMC-STUDIO's own
// src/examples/parol6Kinematics.ts, run with the real three.js library
// (node + require('three'), not a hand-derived formula) against the same
// 5 (x,y,z,a,b,c) inputs below - not values this Kotlin port produced
// itself, which would only prove it's internally consistent, not that it
// matches the algorithm it's meant to faithfully mirror.
// =============================================================================
package com.hydraumc.control.kinematics

import org.junit.Assert.assertEquals
import org.junit.Test

class Parol6KinematicsTest {
    private fun assertJoints(expected: DoubleArray, actual: DoubleArray, tolerance: Double = 1e-3) {
        for (i in expected.indices) {
            assertEquals("j${i + 1}", expected[i], actual[i], tolerance)
        }
    }

    @Test
    fun `matches the real three-js oracle for a straight-ahead reach`() {
        val result = parol6CartesianToJoints(200.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        assertJoints(doubleArrayOf(0.0, 0.072990, -0.072819, 0.0, 0.0, 0.0), result)
    }

    @Test
    fun `matches the real three-js oracle for a diagonal reach with wrist roll`() {
        val result = parol6CartesianToJoints(150.0, 100.0, 20.0, 0.0, 0.0, 90.0)
        assertJoints(doubleArrayOf(33.690068, -4.185567, -2.582369, 0.0, 0.0, 90.0), result)
    }

    @Test
    fun `matches the real three-js oracle for a side reach with wrist pitch and yaw`() {
        val result = parol6CartesianToJoints(0.0, 200.0, -50.0, 10.0, -10.0, 0.0)
        assertJoints(doubleArrayOf(90.0, -1.573483, 17.459544, 10.0, -10.0, 0.0), result)
    }

    @Test
    fun `matches the real three-js oracle at the j1 limit`() {
        val result = parol6CartesianToJoints(-150.0, 50.0, 100.0, 0.0, 0.0, 0.0)
        assertJoints(doubleArrayOf(97.4, 8.232120, -47.529770, 0.0, 0.0, 0.0), result)
    }

    @Test
    fun `matches the real three-js oracle for a full 6-axis target`() {
        val result = parol6CartesianToJoints(180.0, -80.0, -30.0, 5.0, 5.0, 5.0)
        assertJoints(doubleArrayOf(-23.962489, -2.408749, 11.952691, 5.0, 5.0, 5.0), result)
    }
}
