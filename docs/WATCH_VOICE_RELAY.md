<!-- =============================================================================
HYDRA-UMC-ANDROID-CONTROL - Paired Watch voice relay contract
Copyright (C) JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
GPL-3.0-or-later - see LICENSE
============================================================================= -->

# Paired Watch voice relay

Android Control owns the authenticated client session to HYDRA-UMC-SERVER.
`WatchVoiceRelayService` receives only an already-recognised,
user-initiated `WatchVoiceTurn` through the Wear OS Data Layer. It loads the
phone's encrypted Server session, relays the text through the bounded Server
endpoint and sends back a typed `WatchAssistantReply`.

```text
Wear microphone -> system STT -> paired Android transport
  -> POST /api/voice/turn (Server bearer token)
  -> loopback HYDRA-UMC-VOICE-UI -> assistant reply
  -> paired Android transport -> Wear UI/TTS/haptics
```

`HydraApiClient` also reads `GET /api/watch/system-status` into a compact
`WatchSystemStatus` card. Neither method calls the robot command API or
modifies `HydraState`; a motion-related assistant reply is explicitly marked
`requiresConfirmation` and must be confirmed through a primary control UI.

## The security status of the server, read only

The watch can ask for what the server's security options and its history of connections say (`/hydra-umc/server-security-request/v1`). The phone reads
`GET /api/admin/server-config` and `GET /api/connections/clients` with the session of the person who signed in (an administrator; the server refuses anybody
else), builds a `WatchServerSecurityStatus` (`wear/WatchServerSecurity.kt`) and answers on `/hydra-umc/server-security/v1`:

```json
{"type": "server_security_status", "protocolVersion": 1, "loweredSwitches": ["health.open"], "lockedSwitches": 2, "activeClients": 3, "failedLogins": 1}
```

`loweredSwitches` lists, by the `section.key` id the server uses, the options that are on the value that lowers the protection (`health.open`,
`connections.recordAddress`, `camera.open`, `websocket.allowQueryToken`, `logging.redactSecrets`, `camera.credentialsViaEnv`, `install.newInstallTls`); an option
the server does not report is not counted. `lockedSwitches` counts the options the environment of the server fixes; `activeClients` and `failedLogins` come from
the history of connections. The phone writes nothing to the server and the watch has no message that writes. When the options cannot be read (the account is not
an administrator, the server does not answer, a server that predates the options) the watch gets the same "offline" status card as for a failed status request.
The rules and the vectors are in `docs/watch_relay_contract.json`, which this repository keeps identical to the one of HYDRA-UMC-WATCH.

The Data Layer is the official paired Android/Wear transport, not a custom
Bluetooth socket. Google Play services accepts messages only when the Watch
and Android Control APKs have the same `applicationId`
(`com.hydraumc.control`) and signing certificate; it encrypts the channel
over Bluetooth or its relay path. The Watch never receives the Server JWT or
Voice UI token. A real paired device remains required to validate radio,
account and speech-engine behaviour.

## This app's own voice assistant button

`POST /api/voice/turn` also has a second, independent caller:
`HydraApiClient.postVoiceTurn()`, used by this app's own in-app
`VoiceAssistantDialog` (mic icon in the top bar) - this phone's own
operator asking/ordering something directly, not relaying a paired
Watch's request. The two calls share the exact same request/reply shape
and Server route, but `postVoiceTurn()` deliberately omits the
`X-Hydra-Client: watch` header `postWatchVoiceTurn()` sets, so Server's
Config > Remote Access gates them independently (the `android` and
`watch` toggles) - turning off Watch's voice access can never silently
take this button away too, and vice versa. See `RobotViewModel.
sendVoiceTurn()`'s own comment.

## Alerts, presence and two-step confirmation

Three more messages travel between the phone and the watch. Their limits come from `docs/robot_command_contract.json` (`watchAlerts`,
`presenceRelay`, `voiceConfirmRelay`), not from the code.

| Path | Direction | What the phone does |
| --- | --- | --- |
| `/hydra-umc/alert/v1` | phone to watch | `{"type":"alert","severity":"CRITICAL\|WARNING\|INFO","message":"<1-280 characters>"}` for a lost connection (and its return), a robot offline or in safe mode and a STOP the server did not confirm (and the one it did), in the language of the phone (`wear/WatchAlerts.kt`, `wear/WearAlertSink.kt`). The same event is not repeated within thirty seconds and the others are spaced and capped at six a minute; a critical alert is never held back. A switch in the Access panel turns it off. |
| `/hydra-umc/presence/v1` | watch to phone | The heartbeat of the operator is read (`wear/WatchBridge.kt`), the heartbeats that never arrived are counted from the gaps in `sequence` and it is passed to `POST /api/operator/presence` with `missed`. It never retries and what a lost heartbeat means is the server's to decide. Off by default (a switch in the Access panel). A server that answers 404 is remembered until the service starts again. |
| `/hydra-umc/voice-confirm/v1` | watch to phone | The second tap of a spoken order is passed to `POST /api/voice/confirm` and the assistant reply for the new request id goes back to the watch. Off by default; with it off, or when the call fails, the watch gets a sentence instead. The one-use token is never logged or printed; the server still requires the session and the reservation of the robot. |

The reply to a voice turn may carry `confirmationToken` (16 to 1024 characters of `A-Za-z0-9._~+/=-`) and `confirmationValiditySeconds` (1 to 300);
a value the watch would refuse is dropped and the reply itself still arrives. The status for the watch carries `robotsRunning` (robots playing and
not paused) and `alarmActive` (a robot that is online in safe mode) for the complication.

The endpoints `POST /api/operator/presence` and `POST /api/voice/confirm` are not in the server yet.
