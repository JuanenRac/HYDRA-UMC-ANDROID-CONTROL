// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - Parol6-specific kinematics: Parol6Kinematics.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
//
// Faithful Kotlin port of HYDRA-UMC-STUDIO's own
// src/examples/parol6Kinematics.ts (same 6-step PAROL6.urdf transform
// chain, same Newton-Raphson j2/j3 solve, same Z_OFFSET_MM constant) -
// not a redesign, so this app's own jog joystick moves robot A1 (or any
// other Parol6-model robot) the same way STUDIO's own floating Joystick3D
// overlay does, instead of relying on server.ts's generic calculateJoints()
// fallback. See that file's own header comment for the full rationale
// (why Parol6Arm.tsx needs real per-model kinematics instead of the shared
// 2-link planar formula) and RobotViewModel.kt's own jogXYZ() for why this
// exists here specifically.
//
// Ported with plain 4x4 row-major matrices (M*v, column-vector convention)
// instead of a three.js-equivalent Kotlin dependency - the TS original's
// own comments document the exact math (ROOT fix-up rotation, ROS rpy =
// Rz*Ry*Rx = three.js 'ZYX' Euler order, THREE.Matrix4.multiply as
// A = A * B post-multiply) precisely enough to reproduce faithfully
// without needing the library itself.
// =============================================================================
package com.hydraumc.control.kinematics

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

private const val DEG = Math.PI / 180.0

private data class JointStep(val pos: DoubleArray, val rpy: DoubleArray, val axisSign: Double)

// Identical to PAROL6_CHAIN in parol6Kinematics.ts, in meters/radians as authored in the URDF.
private val PAROL6_CHAIN = listOf(
    JointStep(doubleArrayOf(0.0, 0.0, 0.0), doubleArrayOf(0.0, 0.0, 0.0), 1.0),
    JointStep(doubleArrayOf(0.0234207210610375, 0.0, 0.1105), doubleArrayOf(-1.5707963267949, 0.0, 0.0), 1.0),
    JointStep(doubleArrayOf(0.0, -0.18, 0.0), doubleArrayOf(3.1416, 0.0, -1.5708), -1.0),
    JointStep(doubleArrayOf(0.0435, 0.0, 0.0), doubleArrayOf(1.5707963267949, 0.0, 3.14159265358979), -1.0),
    JointStep(doubleArrayOf(0.0, 0.0, -0.17635), doubleArrayOf(-1.5708, 0.0, 0.0), -1.0),
    JointStep(doubleArrayOf(0.0, 0.0, 0.0), doubleArrayOf(1.5708, 0.0, 0.0), -1.0),
)

/** Real limits from PAROL6.urdf's own <limit lower upper>, identical to PAROL6_JOINT_LIMITS_DEG in the TS original. */
val PAROL6_JOINT_LIMITS_DEG: Map<String, Pair<Double, Double>> = mapOf(
    "j1" to (-97.40 to 97.40),
    "j2" to (-56.15 to 57.30),
    "j3" to (-114.59 to 74.48),
    "j4" to (-114.59 to 114.59),
    "j5" to (-120.32 to 120.32),
    "j6" to (-177.62 to 177.62),
)

// Same as parol6Kinematics.ts's own Z_OFFSET_MM - see that file's comment for the full derivation.
private const val Z_OFFSET_MM = 334.0

private fun identity4(): Array<DoubleArray> = Array(4) { r -> DoubleArray(4) { c -> if (r == c) 1.0 else 0.0 } }

private fun matMul(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> {
    val out = Array(4) { DoubleArray(4) }
    for (i in 0..3) for (j in 0..3) {
        var s = 0.0
        for (k in 0..3) s += a[i][k] * b[k][j]
        out[i][j] = s
    }
    return out
}

private fun translationMatrix(x: Double, y: Double, z: Double): Array<DoubleArray> {
    val m = identity4()
    m[0][3] = x; m[1][3] = y; m[2][3] = z
    return m
}

private fun rotationXMatrix(a: Double): Array<DoubleArray> {
    val m = identity4()
    val c = cos(a); val s = sin(a)
    m[1][1] = c; m[1][2] = -s
    m[2][1] = s; m[2][2] = c
    return m
}

private fun rotationYMatrix(a: Double): Array<DoubleArray> {
    val m = identity4()
    val c = cos(a); val s = sin(a)
    m[0][0] = c; m[0][2] = s
    m[2][0] = -s; m[2][2] = c
    return m
}

private fun rotationZMatrix(a: Double): Array<DoubleArray> {
    val m = identity4()
    val c = cos(a); val s = sin(a)
    m[0][0] = c; m[0][1] = -s
    m[1][0] = s; m[1][1] = c
    return m
}

// ROS rpy = Rz(yaw)*Ry(pitch)*Rx(roll) = three.js 'ZYX' Euler order - see
// Parol6Arm.tsx's own rosEuler() header comment (mirrored here) for why.
private fun eulerZYXMatrix(x: Double, y: Double, z: Double): Array<DoubleArray> =
    matMul(matMul(rotationZMatrix(z), rotationYMatrix(y)), rotationXMatrix(x))

// Root: ROS is Z-up, three.js is Y-up - same single fix-up rotation as Parol6Arm.tsx's own root group.
private val ROOT = rotationXMatrix(-Math.PI / 2)

private fun fkPosition(jointsDeg: DoubleArray): DoubleArray {
    var m = ROOT
    for (i in 0..5) {
        val step = PAROL6_CHAIN[i]
        val t = translationMatrix(step.pos[0], step.pos[1], step.pos[2])
        val r = eulerZYXMatrix(step.rpy[0], step.rpy[1], step.rpy[2])
        val jr = rotationZMatrix(step.axisSign * jointsDeg[i] * DEG)
        m = matMul(matMul(matMul(m, t), r), jr)
    }
    return doubleArrayOf(m[0][3], m[1][3], m[2][3])
}

// Radius (horizontal-plane distance) and height for a given (j2,j3), holding j1/j4/j5/j6 at 0 -
// same reduction to a 2-parameter problem parol6Kinematics.ts's own radiusHeight() documents.
private fun radiusHeight(j2: Double, j3: Double): Pair<Double, Double> {
    val p = fkPosition(doubleArrayOf(0.0, j2, j3, 0.0, 0.0, 0.0))
    return Pair(sqrt(p[0] * p[0] + p[2] * p[2]), p[1])
}

private fun clampD(v: Double, lo: Double, hi: Double): Double = v.coerceIn(lo, hi)

// Newton-Raphson on the 2-parameter (j2,j3) -> (radius,height) system, multi-seeded and
// clamped to the real joint limits as a last resort - identical structure and constants to
// solveJ2J3() in parol6Kinematics.ts.
private fun solveJ2J3(targetRadius: Double, targetHeight: Double): Pair<Double, Double> {
    val seeds = listOf(-30.0 to 30.0, 0.0 to 0.0, 30.0 to -30.0, -50.0 to 60.0, 50.0 to -60.0)
    var bestJ2 = 0.0
    var bestJ3 = 0.0
    var bestErr = Double.POSITIVE_INFINITY

    for ((g2, g3) in seeds) {
        var j2 = g2
        var j3 = g3
        var lastErr = Double.POSITIVE_INFINITY
        val h = 0.01
        for (iter in 0 until 80) {
            val (r, y) = radiusHeight(j2, j3)
            val fR = r - targetRadius
            val fY = y - targetHeight
            val err = hypot(fR, fY)
            if (err < 1e-7) break
            if (err > lastErr * 1.5 && iter > 5) break
            lastErr = err

            val (r2, y2) = radiusHeight(j2 + h, j3)
            val (r3, y3) = radiusHeight(j2, j3 + h)
            val dRdj2 = (r2 - r) / h
            val dYdj2 = (y2 - y) / h
            val dRdj3 = (r3 - r) / h
            val dYdj3 = (y3 - y) / h
            val det = dRdj2 * dYdj3 - dRdj3 * dYdj2
            if (abs(det) < 1e-9) break

            val maxStep = 15.0
            val dj2 = clampD((fR * dYdj3 - fY * dRdj3) / det, -maxStep, maxStep)
            val dj3 = clampD((fY * dRdj2 - fR * dYdj2) / det, -maxStep, maxStep)
            j2 -= dj2
            j3 -= dj3
        }
        val (r, y) = radiusHeight(j2, j3)
        val err = hypot(r - targetRadius, y - targetHeight)
        if (err < bestErr) {
            bestErr = err
            bestJ2 = j2
            bestJ3 = j3
        }
        if (bestErr < 1e-6) break
    }

    val j2Limits = PAROL6_JOINT_LIMITS_DEG.getValue("j2")
    val j3Limits = PAROL6_JOINT_LIMITS_DEG.getValue("j3")
    return Pair(clampD(bestJ2, j2Limits.first, j2Limits.second), clampD(bestJ3, j3Limits.first, j3Limits.second))
}

/** {j1..j6} in degrees for the real Parol6 chain - direct port of parol6CartesianToJoints(). */
fun parol6CartesianToJoints(x: Double, y: Double, z: Double, a: Double, b: Double, c: Double): DoubleArray {
    val j1 = atan2(y, x) * (180.0 / Math.PI)
    val targetRadius = sqrt(x * x + y * y) / 1000.0
    val targetHeight = (z + Z_OFFSET_MM) / 1000.0
    val (j2, j3) = solveJ2J3(targetRadius, targetHeight)
    val j1Limits = PAROL6_JOINT_LIMITS_DEG.getValue("j1")
    val j4Limits = PAROL6_JOINT_LIMITS_DEG.getValue("j4")
    val j5Limits = PAROL6_JOINT_LIMITS_DEG.getValue("j5")
    val j6Limits = PAROL6_JOINT_LIMITS_DEG.getValue("j6")
    return doubleArrayOf(
        clampD(j1, j1Limits.first, j1Limits.second),
        j2,
        j3,
        clampD(a, j4Limits.first, j4Limits.second),
        clampD(b, j5Limits.first, j5Limits.second),
        clampD(c, j6Limits.first, j6Limits.second),
    )
}
