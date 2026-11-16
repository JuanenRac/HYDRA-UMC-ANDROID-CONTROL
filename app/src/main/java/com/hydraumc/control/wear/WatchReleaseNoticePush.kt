// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - Tells the paired watch that a newer release exists
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import com.hydraumc.common.SemanticVersion
import com.hydraumc.common.UpdateCheckResult
import com.hydraumc.control.network.RobotCommandContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Where the notice of a new release goes (the Wear Data Layer in the app, a fake in the tests). */
fun interface WatchReleaseNoticeSink {
    /** Returns true when at least one watch took the message. */
    suspend fun send(notice: WatchReleaseNotice): Boolean
}

/**
 * Decides when the watch is told: once for each version, and never for a version that is not newer than the last one told, so a screen that checks
 * again and again does not repeat itself. The watch shows the notice only when its own switch is on (off by default); this class holds no network and
 * no Android class.
 */
class ReleaseNoticeWatchPolicy {
    private var lastTold: SemanticVersion? = null

    /** True when [version] should be sent now; it is then remembered as the last one told. */
    fun accept(version: SemanticVersion): Boolean {
        val last = lastTold
        if (last != null && version <= last) return false
        lastTold = version
        return true
    }

    /** The last version told, or null. */
    val told: SemanticVersion? get() = lastTold

    /** Puts back the version told before [version] was accepted, because no watch took the message. */
    fun forget(version: SemanticVersion, before: SemanticVersion?) {
        if (lastTold == version) lastTold = before
    }

    /** Forgets what was told: the next newer release is sent whatever it is. */
    fun reset() {
        lastTold = null
    }
}

/**
 * Passes the verdict of the update check to the watch. Only a release that the shared gate accepted ([UpdateCheckResult.Available]: stable tag, newer than
 * the installed one, the exact asset, HTTPS, digest) is told; a failed or an up-to-date check sends nothing. The watch never downloads or installs anything.
 * [enabled] is the switch of the phone (Settings, "Tell the watch when a newer version exists"; on as it ships): when it is off this sends nothing, whatever the check found.
 * Returns true when a message was taken by a watch.
 */
suspend fun announceReleaseToWatch(result: UpdateCheckResult, policy: ReleaseNoticeWatchPolicy, sink: WatchReleaseNoticeSink, enabled: Boolean = true): Boolean {
    if (!enabled) return false // the person switched the notice off: nothing is sent and the version is not remembered, so turning it on again tells the next check
    val available = result as? UpdateCheckResult.Available ?: return false
    val version = available.update.version
    val before = policy.told
    if (!policy.accept(version)) return false
    val notice = runCatching { WatchReleaseNotice(version.toString()) }.getOrNull()
    val taken = notice != null && sink.send(notice)
    // Told only when a watch took it: with none connected the next check tells it again.
    if (!taken) policy.forget(version, before)
    return taken
}

/**
 * Sends the notice as a message on `/hydra-umc/release-notice/v1` to every watch connected to this phone. A message is not stored: when no watch is
 * connected it is not delivered and nothing is queued; the next check of the update tells it again.
 */
class WearReleaseNoticeSink(private val context: Context) : WatchReleaseNoticeSink {
    override suspend fun send(notice: WatchReleaseNotice): Boolean = withContext(Dispatchers.IO) {
        val timeout = RobotCommandContract.WATCH_ALERTS_SEND_TIMEOUT_MS
        try {
            val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes, timeout, TimeUnit.MILLISECONDS)
            val payload = notice.toJson().toString().encodeToByteArray()
            var delivered = false
            for (node in nodes) {
                try {
                    Tasks.await(Wearable.getMessageClient(context).sendMessage(node.id, WatchRelayPaths.RELEASE_NOTICE, payload), timeout, TimeUnit.MILLISECONDS)
                    delivered = true
                } catch (e: Exception) {
                    Log.w(TAG, "notice to ${node.id} failed (${e.javaClass.simpleName})")
                }
            }
            delivered
        } catch (e: Exception) {
            Log.w(TAG, "no watch for the notice (${e.javaClass.simpleName})")
            false
        }
    }

    private companion object {
        const val TAG = "WearReleaseNoticeSink"
    }
}
