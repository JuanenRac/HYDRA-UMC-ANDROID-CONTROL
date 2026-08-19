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
//
// Identity check: a hit is "a real HYDRA-UMC server" purely by the presence
// of remoteApiVersion in the response - the exact same test
// HydraApiClient.getHydraInfo() already uses for the manual-IP path
// (RobotViewModel.connect()). The `product` field server.ts returns
// (server.ts's own GET /api/hydra-info handler) is the operator's
// configurable serverName setting, only defaulting to the literal string
// "HYDRA-UMC STUDIO" when unset - matching against that literal here would
// silently drop every server whose owner ever renamed it, while the exact
// same host still answers fine to a manual IP/port entry.
// =============================================================================
package com.hydraumc.control.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
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
private const val SCAN_TIMEOUT_MS = 1500L
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
            // Same identity test as HydraApiClient.getHydraInfo() - `product`
            // is a user-editable server name, not a fixed marker.
            if (!json.has("remoteApiVersion")) return null
            ServerInfo.fromHydraInfo(host, port, json)
        }
    } catch (e: Exception) {
        null
    }
}

/** 
 * Scans all available local subnets for HYDRA-UMC servers.
 */
fun scanSubnets(context: Context, client: OkHttpClient, port: Int = DEFAULT_PORT): Flow<ServerInfo> = callbackFlow {
    val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    
    val nsdListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {}
        override fun onServiceFound(service: NsdServiceInfo) {
            if (service.serviceType == "_hydra._tcp.") {
                nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val host = serviceInfo.host.hostAddress ?: return
                        // Dispatchers.IO explicitly - this callback runs on whatever
                        // dispatcher the collector's own scope uses (viewModelScope is
                        // Dispatchers.Main.immediate), and probeHost() is a blocking call.
                        launch(Dispatchers.IO) {
                            probeHost(client, host, serviceInfo.port)?.let { trySend(it) }
                        }
                    }
                })
            }
        }
        override fun onServiceLost(service: NsdServiceInfo) {}
        override fun onDiscoveryStopped(regType: String) {}
        override fun onStartDiscoveryFailed(regType: String, errorCode: Int) { nsdManager.stopServiceDiscovery(this) }
        override fun onStopDiscoveryFailed(regType: String, errorCode: Int) { nsdManager.stopServiceDiscovery(this) }
    }

    nsdManager.discoverServices("_hydra._tcp.", NsdManager.PROTOCOL_DNS_SD, nsdListener)

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

    awaitClose { 
        try { nsdManager.stopServiceDiscovery(nsdListener) } catch (e: Exception) {}
        jobs.forEach { it.cancel() } 
    }
}
