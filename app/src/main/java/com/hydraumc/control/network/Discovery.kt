// =============================================================================
// HYDRA-UMC CONTROL - Network discovery for locating Hydra servers on the LAN
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

/** Default port for HYDRA-UMC servers. */
private const val DEFAULT_PORT = 3000
/** Timeout for each individual host probe. */
private const val SCAN_TIMEOUT_MS = 600L
/** Maximum number of concurrent probes to run. */
private const val SCAN_CONCURRENCY = 64

/** 
 * Every non-loopback IPv4 address this device currently has.
 * @return List of IPv4 address strings.
 */
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

/** 
 * Every other host in localIp's own /24 subnet.
 * @param localIp The local IP address to derive the subnet from.
 * @return List of candidate IP addresses in the same subnet.
 */
private fun candidateHostsFor(localIp: String): List<String> {
    val parts = localIp.split(".")
    if (parts.size != 4) return emptyList()
    val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"
    return (1..254).map { "$prefix.$it" }.filter { it != localIp }
}

/** 
 * Probes a single host to see if it hosts a HYDRA-UMC server.
 * @param client The OkHttpClient to use.
 * @param host The host IP address.
 * @param port The port number.
 * @return ServerInfo if found, null otherwise.
 */
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

/** 
 * Scans all available local subnets for HYDRA-UMC servers.
 * @param client The shared OkHttpClient.
 * @param port The port to scan on (default 3000).
 * @return A Flow that emits discovered ServerInfo objects.
 */
fun scanSubnets(client: OkHttpClient, port: Int = DEFAULT_PORT): Flow<ServerInfo> = callbackFlow {
    /** Specialized client with shorter timeouts for scanning. */
    val timedClient = client.newBuilder()
        .connectTimeout(SCAN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(SCAN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    /** List of all local IPs across all interfaces. */
    val localIps = localIpv4Addresses()
    /** Set of all unique hosts to probe. */
    val hosts = linkedSetOf<String>()
    for (localIp in localIps) {
        hosts.add(localIp) // the phone's own LAN IP - a server bound to it (not just localhost) answers here too
        hosts.addAll(candidateHostsFor(localIp))
    }

    if (hosts.isEmpty()) {
        close()
        return@callbackFlow
    }

    /** Semaphore to limit concurrency and avoid overloading the network. */
    val semaphore = Semaphore(SCAN_CONCURRENCY)
    /** List of active probe jobs. */
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
