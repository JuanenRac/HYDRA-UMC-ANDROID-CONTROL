# HYDRA-UMC Android Control - Architecture

**Status: Wi-Fi transport implemented.** `app/src/` now contains a real
implementation of section 2 below (discovery, `GET`/`POST /api/settings`,
`/ws` live sync, jog/speed/tool/playback controls) - see
`network/HydraApiClient.kt`, `network/HydraWebSocket.kt`,
`network/Discovery.kt`, `model/HydraState.kt` and
`viewmodel/RobotViewModel.kt` for the real code and their own header
comments for the contract each speaks. Bluetooth transport (section 3)
remains unimplemented, as described below.

## 1. What this app is

A native Android app that controls a robot running on the
[HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC) platform, reached
through its Compute Module 5 host - either over the same local Wi-Fi
network HYDRA-UMC STUDIO's own browser UI uses, or over Bluetooth for a
closer-range, no-network-setup-needed control path. Direct counterpart to
[HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL) -
same design, same API contract, different platform/toolchain. The 2
codebases don't share code, but should stay in feature parity.

## 2. Wi-Fi transport (primary - real API exists today)

The CM5 already runs a real, working server for this: HYDRA-UMC STUDIO's
own `server.ts`. This app should speak the exact same contract documented
in [`HYDRA-UMC-STUDIO/docs/REMOTE_API.md`](https://github.com/JuanenRac/HYDRA-UMC-STUDIO/blob/main/docs/REMOTE_API.md) -
the same one [HYDRA-UMC SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE)
uses, not a separate mobile-specific protocol:

- `GET /api/hydra-info` - discover/confirm a candidate IP is actually
  running HYDRA-UMC STUDIO (product name, API version, robot/controller
  counts) before connecting to it for real.
- `GET`/`POST /api/settings` - read and write the full application state
  (robots, jobs, trajectories, configuration) - read-modify-write, no
  granular per-field update exists server-side.
- `WebSocket /ws` - live push: the server sends the current state on
  connect, then broadcasts every change (from any client - a browser tab,
  this app, HYDRA-UMC SUITE) to every other connected client. A change
  made from this app should show up live in an open HYDRA-UMC STUDIO
  browser tab, and vice versa - not just on next manual refresh.

**Recommended stack:** `OkHttp` for the REST calls and its own WebSocket
support for `/ws` (the de facto standard Android HTTP/WebSocket client;
no need to hand-roll either), `kotlinx.serialization` or `Moshi` for the
JSON payloads. Both are widely-used, actively-maintained libraries, not
an unusual choice for this ecosystem's own "no unnecessary dependencies"
convention - a hand-rolled `HttpURLConnection` WebSocket client would be
real, avoidable extra work for no benefit here.

**Discovery on a real network:** the REMOTE_API.md document itself notes
no mDNS/Bonjour service is advertised yet (a real gap, not an oversight -
see that document's own "Future work" section). Until that exists
server-side, this app has 2 realistic options: (a) let the user type in
a HYDRA-UMC's IP/hostname manually (simplest, always works), or (b) scan
the local subnet's likely IP range hitting `/api/hydra-info` on each
candidate (same approach HYDRA-UMC SUITE's own network scanner uses -
see that project's own `discovery.py` for the reference algorithm once it
exists). Android's `NsdManager` (Network Service Discovery, mDNS-based)
becomes the much better option once the CM5 side actually advertises a
`_hydra-umc._tcp` service - track that against REMOTE_API.md's own
"Future work" note rather than building NSD support against nothing.

## 3. Bluetooth transport (secondary - NOT backed by any server-side support yet)

**Honesty note, matching the rest of this ecosystem's documentation
convention:** there is currently no Bluetooth service of any kind running
on a HYDRA-UMC's CM5 - no BLE GATT server, no Bluetooth Classic profile,
nothing. Before this transport can be built on the Android side,
HYDRA-UMC's own CM5-side software needs a corresponding BLE peripheral
service (most likely a BlueZ-based GATT server process on the CM5's own
Linux OS, exposing a custom service/characteristic set that mirrors a
useful subset of the Wi-Fi API above - short-range jog control and status
readout are the obvious first candidates, not full state sync, given
BLE's much lower throughput than Wi-Fi). That server-side work does not
exist yet anywhere in this ecosystem as of this document's own writing
(15 August 2026) - track it as a HYDRA-UMC-repository prerequisite, not
something to build from the Android side alone.

Once that CM5-side service exists, the Android side would use the
platform's `BluetoothLeScanner`/`BluetoothGatt` APIs - see
`app/src/main/kotlin/.../bluetooth/` below for where that implementation
belongs once it's real.

## 4. Suggested source layout

```text
app/src/main/kotlin/com/electrohobby3d/hydraumccontrol/
├── MainActivity.kt            # Entry point (placeholder)
├── ui/                        # Jetpack Compose screens (placeholder)
├── networking/                # OkHttp REST client + WebSocket live-sync client (placeholder)
└── bluetooth/                 # BluetoothLeScanner/BluetoothGatt client, blocked on CM5-side GATT service (placeholder)
```

**Recommended UI toolkit:** Jetpack Compose (Google's current recommended
Android UI toolkit, not the legacy XML View system) - matches "latest
technology" the same way the rest of this ecosystem's own newer projects
(React 19, Vite, PySide6 for HYDRA-UMC SUITE) lean current rather than
legacy-compatible.

## 5. Relationship to the rest of the ecosystem

See the root [`README.md`](../README.md)'s own "Related Projects" section
for the full picture. The 3 things worth knowing specifically for this
app's own design: it speaks the exact same REMOTE_API.md contract as
[HYDRA-UMC SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE) (don't
invent a separate mobile protocol), it has a direct iOS counterpart
([HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL))
that should stay in sync with this app's own feature set even though the
2 codebases don't share code, and [HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC)
is the actual hardware/firmware project this app ultimately controls.
