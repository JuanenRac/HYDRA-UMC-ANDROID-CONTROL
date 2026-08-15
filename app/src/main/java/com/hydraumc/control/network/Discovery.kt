// =============================================================================
// HYDRA-UMC Android Control - network/Discovery.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
//
// Subnet scanner - hits GET /api/hydra-info (REMOTE_API.md section 1) on
// every candidate IP in the phone's own /24 range concurrently, keeps
// whichever ones actually answer with a real HYDRA-UMC STUDIO payload. No
// mDNS/Bonjour service exists on the server side yet (REMOTE_API.md's own
// "Future work" note) - a raw concurrent scan is the real, working option
// today. This is a direct Kotlin port of HYDRA-UMC-SUITE's own
// hydra_suite/net/discovery.py - same 2 functions
// (localIpv4Addresses/candidateHostsFor), same "always probe 127.0.0.1 and
// the phone's own LAN IP too, not just the other hosts on the subnet" fix
// that file's own header comment calls out as a real bug already found and
// fixed there (a same-machine or same-network server bound to a specific
// interface, not just localhost, would otherwise never be probed).
// =============================================================================
package com.hydraumc.control.network

import com.hydraumc.control.model.ServerInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

private const val DEFAULT_PORT = 3000
private const val SCAN_TIMEOUT_MS = 600L
private const val SCAN_CONCURRENCY = 64

/** Every non-loopback IPv4 address this device currently has - a phone on
 * more than one network (Wi-Fi + a VPN tunnel) gets a candidate subnet per
 * address. Mirrors discovery.py's local_ipv4_addresses(). */
private fun localIpv4Addresses(): List<String> {
    val addrs = mutableListOf<String>()
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return addrs
        for (iface in interfaces) {
            if (!iface.isUp || iface.isLoopback) continue
            for (addr in iface.inetAddresses) {
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    val host = addr.hostAddress
                    if (host != null && host !in addrs) addrs.add(host)
                }
            }
        }
    } catch (e: Exception) {
        // No network interfaces available (airplane mode, etc.) - empty result, scan finds nothing.
    }
    return addrs
}

/** Every other host in localIp's own /24 - the common case for a home/lab
 * Wi-Fi network. Mirrors discovery.py's candidate_hosts_for(). */
private fun candidateHostsFor(localIp: String): List<String> {
    val parts = localIp.split(".")
    if (parts.size != 4) return emptyList()
    val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"
    return (1..254).map { "$prefix.$it" }.filter { it != localIp }
}

/** One GET /api/hydra-info - returns null for anything that doesn't answer
 * or doesn't answer with a recognizable HYDRA-UMC STUDIO payload (closed
 * port, different service, remote access disabled - all "not a real
 * server" from here, not worth surfacing per-host during a broad scan).
 * Mirrors discovery.py's probe_host(). */
private fun probeHost(client: OkHttpClient, host: String, port: Int): ServerInfo? {
    return try {
        val request = Request.Builder()
            .url("http://$host:$port/api/hydra-info")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            if (json.optString("product") != "HYDRA-UMC STUDIO") return null
            ServerInfo.fromHydraInfo(host, port, json)
        }
    } catch (e: Exception) {
        null
    }
}

/** Scans every candidate host across every local subnet concurrently
 * (bounded by SCAN_CONCURRENCY), emitting each real HYDRA-UMC found as soon
 * as it answers rather than waiting for the whole scan to finish - the
 * Settings screen can start listing results immediately. Mirrors
 * discovery.py's scan_subnets(), including its own "always probe 127.0.0.1
 * first" fix: the single most common real setup is HYDRA-UMC STUDIO's dev
 * server running on the same machine as the phone would never reach
 * (obviously - a phone isn't "the same machine" as a dev server the way
 * SUITE can be), so what matters here is the phone's own LAN IP itself,
 * which localIpv4Addresses() intentionally does NOT filter out (unlike
 * loopback), since a server bound to that exact address (not just
 * "localhost") should still answer a probe sent to it. */
fun scanSubnets(client: OkHttpClient, port: Int = DEFAULT_PORT): Flow<ServerInfo> = callbackFlow {
    val timedClient = client.newBuilder()
        .connectTimeout(SCAN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(SCAN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    val localIps = localIpv4Addresses()
    val hosts = linkedSetOf<String>()
    for (localIp in localIps) {
        hosts.add(localIp) // the phone's own LAN IP - a server bound to it (not just localhost) answers here too
        hosts.addAll(candidateHostsFor(localIp))
    }

    if (hosts.isEmpty()) {
        close()
        return@callbackFlow
    }

    val semaphore = Semaphore(SCAN_CONCURRENCY)
    val jobs = hosts.map { host ->
        launch(Dispatchers.IO) {
            semaphore.withPermit {
                probeHost(timedClient, host, port)?.let { trySend(it) }
            }
        }
    }
    // Close the flow on our own once every host has answered (or timed out) -
    // otherwise a collector like .toList() would hang forever waiting for a
    // completion that only awaitClose's own cancellation would ever trigger.
    launch {
        jobs.forEach { it.join() }
        close()
    }

    awaitClose { jobs.forEach { it.cancel() } }
}
