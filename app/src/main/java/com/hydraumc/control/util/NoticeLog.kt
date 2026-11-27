// =============================================================================
// HYDRA-UMC CONTROL - The list of notices kept on the phone
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
// What happened, so it does not only live in a message that disappears. It is filled by the phone itself (what it sees
// while the app is open, and the push messages it receives); nothing is asked to the server. It holds the kind, a robot
// name or a short text and the time, and only the last [MAX] entries.
// =============================================================================
package com.hydraumc.control.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class NoticeGroup { ROBOTS, SERVER, JOBS }

enum class NoticeKind(val group: NoticeGroup, val attention: Boolean) {
    CONNECTION_LOST(NoticeGroup.SERVER, true),
    CONNECTION_RESTORED(NoticeGroup.SERVER, false),
    ROBOT_OFFLINE(NoticeGroup.ROBOTS, true),
    ROBOT_SAFE_MODE(NoticeGroup.ROBOTS, true),
    STOP_NOT_CONFIRMED(NoticeGroup.ROBOTS, true),
    STOP_CONFIRMED(NoticeGroup.ROBOTS, false),
    JOB_DONE(NoticeGroup.JOBS, false),
    SERVER_MESSAGE(NoticeGroup.SERVER, true),
    UPDATE(NoticeGroup.SERVER, false),
}

/** One notice. [detail] is a robot name, a version, or the text of a server message. */
data class Notice(val kind: NoticeKind, val detail: String, val atMs: Long, val seen: Boolean = false)

object Notices {
    const val MAX = 100
    private const val DETAIL_MAX = 300

    /** The newest first; the oldest fall off. */
    fun add(list: List<Notice>, notice: Notice): List<Notice> = (listOf(notice.copy(detail = notice.detail.take(DETAIL_MAX))) + list).take(MAX)

    fun unseen(list: List<Notice>): Int = list.count { !it.seen && it.kind.attention }

    fun encode(list: List<Notice>): String = JSONArray().also { array ->
        list.forEach { array.put(JSONObject().put("kind", it.kind.name).put("detail", it.detail).put("at", it.atMs).put("seen", it.seen)) }
    }.toString()

    /** A damaged or unknown entry is dropped, never an error. */
    fun decode(text: String): List<Notice> = runCatching {
        val array = JSONArray(text)
        (0 until array.length()).mapNotNull { i ->
            val entry = array.optJSONObject(i) ?: return@mapNotNull null
            val kind = NoticeKind.entries.firstOrNull { it.name == entry.optString("kind") } ?: return@mapNotNull null
            Notice(kind, entry.optString("detail"), entry.optLong("at"), entry.optBoolean("seen"))
        }.take(MAX)
    }.getOrDefault(emptyList())
}

class NoticeLog(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("hydra_notices", Context.MODE_PRIVATE)

    /** The stored text: it changes exactly when the list does. */
    fun raw(): String = prefs.getString(KEY, "[]") ?: "[]"

    fun all(): List<Notice> = Notices.decode(raw())

    @Synchronized
    fun add(kind: NoticeKind, detail: String = "", atMs: Long = System.currentTimeMillis()) = write(Notices.add(all(), Notice(kind, detail, atMs)))

    /** Adds it unless the same kind and detail is already in the list (a version that is offered at every start). */
    @Synchronized
    fun addOnce(kind: NoticeKind, detail: String) {
        if (all().none { it.kind == kind && it.detail == detail }) add(kind, detail)
    }

    @Synchronized
    fun markAllSeen() = write(all().map { it.copy(seen = true) })

    private fun write(list: List<Notice>) {
        prefs.edit().putString(KEY, Notices.encode(list)).apply()
    }

    private companion object {
        const val KEY = "list"
    }
}
