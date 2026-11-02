// =============================================================================
// HYDRA-UMC CONTROL - Work files (trajectories): the folder, the list and the points
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.util

import com.hydraumc.control.network.RobotCommandContract
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * The work files a robot can play. The server keeps them in a folder (the robot's own entry of `worksPaths`, or
 * `WORKS/<name of the robot without spaces>`), lists their names in `<folder>/index.json` and serves each file as a JSON
 * list of points. The `trajectory` command loads one list into the robot. This file only checks what the server
 * sent and builds the parameters of that command: it reads nothing and sends nothing. The limits are the server's
 * own (src/robotEdit.ts) and come from docs/robot_command_contract.json.
 */
object WorkFiles {
    /** The numeric fields a point may carry; the server drops every other one. */
    val POINT_FIELDS = listOf("j1", "j2", "j3", "j4", "j5", "j6", "x", "y", "z", "a", "b", "c", "tx", "ty", "trz")
    private val JOINTS = listOf("j1", "j2", "j3", "j4", "j5", "j6")
    private val TABLE_FIELDS = listOf("tx", "ty", "trz")

    /** A file name the server accepts as `selectedWorkFile`. */
    private val FILE_NAME = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,${RobotCommandContract.WORKS_FILE_NAME_MAX - 1}}\\.json$")
    private val FOLDER = Regex("^[A-Za-z0-9_][A-Za-z0-9_ ./-]*$")

    /**
     * The folder of [robotId]: its entry in [worksPaths] (a JSON object of folder by robot id) or `WORKS/` and its name without
     * spaces. Null when the folder is not a plain relative path inside the server (no `..`, no leading slash, no backslash).
     */
    fun folderFor(worksPaths: JSONObject?, robotId: Int, robotName: String): String? {
        val configured = worksPaths?.optString(robotId.toString(), "")?.trim().orEmpty()
        val folder = configured.ifEmpty { "WORKS/" + robotName.replace(Regex("\\s+"), "") }.trim('/')
        if (folder.isEmpty() || folder.length > RobotCommandContract.WORKS_FOLDER_MAX) return null
        if (!FOLDER.matches(folder) || folder.split('/').any { it.isEmpty() || it == "." || it == ".." }) return null
        return folder
    }

    /** The safe file names of the index (a JSON list of names); everything else in it is dropped. Null when the text is not a list. */
    fun parseIndex(body: String): List<String>? {
        val list = try {
            JSONArray(body)
        } catch (_: JSONException) {
            return null
        }
        return (0 until list.length()).mapNotNull { (list.opt(it) as? String)?.takeIf(FILE_NAME::matches) }.distinct().sorted()
    }

    fun isSafeFileName(name: String): Boolean = FILE_NAME.matches(name)

    enum class Problem { TOO_BIG, NOT_JSON, NOT_A_LIST, EMPTY, TOO_MANY_POINTS, BAD_POINT }

    sealed class Loaded {
        /** [points] hold only the fields of [POINT_FIELDS] plus `motionType` when it is the one the server knows. */
        data class Ok(val points: JSONArray, val usesTable: Boolean) : Loaded()
        data class Refused(val problem: Problem, val index: Int = -1) : Loaded()
    }

    /** Checks the file the way the server will and reduces each point to the fields it keeps. */
    fun parsePoints(body: String): Loaded {
        if (body.length > RobotCommandContract.WORKS_FILE_MAX_BYTES) return Loaded.Refused(Problem.TOO_BIG)
        val root = try {
            JSONArray(body)
        } catch (_: JSONException) {
            return if (looksLikeObject(body)) Loaded.Refused(Problem.NOT_A_LIST) else Loaded.Refused(Problem.NOT_JSON)
        }
        if (root.length() == 0) return Loaded.Refused(Problem.EMPTY)
        if (root.length() > RobotCommandContract.WORKS_MAX_POINTS) return Loaded.Refused(Problem.TOO_MANY_POINTS)
        val clean = JSONArray()
        var usesTable = false
        for (i in 0 until root.length()) {
            val point = root.optJSONObject(i) ?: return Loaded.Refused(Problem.BAD_POINT, i)
            val out = JSONObject()
            for (field in POINT_FIELDS) {
                if (!point.has(field)) continue
                val value = point.opt(field)
                if (value !is Number || value.toDouble().isNaN() || value.toDouble().isInfinite()) return Loaded.Refused(Problem.BAD_POINT, i)
                out.put(field, value.toDouble())
            }
            if (point.has("motionType")) {
                if (point.opt("motionType") != "model-joints") return Loaded.Refused(Problem.BAD_POINT, i)
                out.put("motionType", "model-joints")
            }
            val hasJoints = JOINTS.all { out.has(it) }
            val hasPose = listOf("x", "y", "z").all { out.has(it) }
            if (!hasJoints && !hasPose) return Loaded.Refused(Problem.BAD_POINT, i)
            if (TABLE_FIELDS.any { out.has(it) }) usesTable = true
            clean.put(out)
        }
        return Loaded.Ok(clean, usesTable)
    }

    private fun looksLikeObject(body: String): Boolean = try {
        JSONObject(body)
        true
    } catch (_: JSONException) {
        false
    }

    /** The parameters of the `trajectory` command for [points] loaded from [fileName]. */
    fun trajectoryParams(points: JSONArray, fileName: String): JSONObject {
        require(isSafeFileName(fileName)) { "unsafe work file name" }
        return JSONObject().put("points", points).put("selectedWorkFile", fileName)
    }

    /** Why the app does not load a work into a robot; [NONE] when it may. */
    enum class Block { NONE, NOT_CONNECTED, OFFLINE, SAFE_MODE, PLAYING, STOP_PENDING, ROLE, NO_TABLE, NOT_SUPPORTED }

    data class Situation(
        val connected: Boolean,
        val robotOnline: Boolean,
        val robotEnabled: Boolean,
        /** A trajectory is playing (paused or not): loading another would reset it under the robot. */
        val robotPlaying: Boolean,
        val stopInProgress: Boolean,
        val role: String?,
        /** The file has table points and the robot has no XY table. */
        val tableMissing: Boolean,
        val serverSupports: Boolean,
    )

    fun blockOf(s: Situation): Block = when {
        !s.connected -> Block.NOT_CONNECTED
        !s.serverSupports -> Block.NOT_SUPPORTED
        !DeadManJog.roleAllowed(s.role) -> Block.ROLE
        !s.robotOnline -> Block.OFFLINE
        !s.robotEnabled -> Block.SAFE_MODE
        s.stopInProgress -> Block.STOP_PENDING
        s.robotPlaying -> Block.PLAYING
        s.tableMissing -> Block.NO_TABLE
        else -> Block.NONE
    }
}
