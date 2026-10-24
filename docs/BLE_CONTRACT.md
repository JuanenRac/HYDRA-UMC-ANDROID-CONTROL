# Bluetooth LE contract

How this app talks to a HYDRA-UMC device over Bluetooth Low Energy (GATT). This is the contract the
app implements (`network/BleContract.kt`, `network/HydraBleClient.kt`). The peripheral side is implemented by
HYDRA-UMC-SERVER on the CM5 (its `docs/BLE_GATT.md` is the reference for the peripheral: options, codes and
limits); it is off by default and has not been tried against this app on a phone. The app itself is tested
against a simulated GATT only (see "What was verified"). Any firmware or gateway that wants to be reachable from
this app implements the table below.

## Service and characteristics

| Item | UUID | Properties | Content |
| --- | --- | --- | --- |
| HYDRA service | `48594452-0001-4000-8000-554d43000001` | primary, advertised | groups the two characteristics |
| STATE | `48594452-0002-4000-8000-554d43000001` | read, **notify** (CCCD `2902`) | the robot state, UTF-8 JSON, the same settings tree the WebSocket sends |
| COMMAND | `48594452-0003-4000-8000-554d43000001` | **write** (with response) | one command per message, UTF-8 JSON |
| ACK | `48594452-0004-4000-8000-554d43000001` | **notify** (CCCD `2902`) | one acknowledgement per command, UTF-8 JSON (optional: a device that does not offer it cannot take commands) |

The peripheral advertises the service UUID, so the app can scan with a service filter. The Bluetooth tab
can also list every device (switch "Show all Bluetooth devices", off by default).

## Messages and frames

A message (state or command) can be larger than one ATT payload, so it is cut into **frames**. The
payload of a frame is `MTU - 3` bytes; the app asks for MTU 247 and uses what the device grants (23 if
it grants nothing). Every frame starts with one header byte:

| Bits | Meaning |
| --- | --- |
| 0 | `1` = first frame of the message |
| 1 | `1` = last frame of the message (a one-frame message has bits 0 and 1 set) |
| 2-3 | reserved, `0` |
| 4-7 | sequence number: `0` on the first frame, `+1` per frame, modulo 16 |

The rest of the frame is the next slice of the message bytes. The receiver appends slices until it sees
the last frame. A frame that arrives out of sequence, a continuation with no start, or a message past
64 KiB is dropped (the app reports a damaged message) and the receiver waits for the next first frame.
A new first frame always discards a partial message.

### STATE

Same content as the `settings` payload of the WebSocket (`REMOTE_API.md` of the server): the whole state
tree. The peripheral notifies a full state after the subscription is enabled and after every change.
Delivery is not guaranteed by BLE; the app only trusts complete, well-ordered messages.

### COMMAND

The app writes the body that `POST /api/robot/{id}/command` takes, with the robot id added:

```json
{ "robotId": 1, "command": "jog", "params": { "axis": "x", "amount": 1.0 }, "controllerId": "..." }
```

Frames are written one at a time, each waiting for the write response of the previous one. A refused
write drops the rest of that message and is reported to the user. The peripheral applies the same
authorization, reservation and safety-limit rules as the server; BLE gives no extra privilege.

A command carries an `id` (1 to 64 characters, chosen by the app) and the access token of the account:

```json
{ "id": "c-17", "robotId": 1, "command": "pause", "params": { "paused": true }, "controllerId": "...", "token": "..." }
```

The peripheral answers on ACK, after the route of the server answered and never before:

```json
{ "id": "c-17", "ok": true, "status": 200, "code": "ok" }
```

`ok` is true only for a 2xx answer; `status` is the HTTP status of the route (0 when it was never reached) and
`code` a stable word (`other-account`, `claimed`, `forbidden`, `rate-limited`, `commands-disabled`, ...). The
full list is in the server's `docs/BLE_GATT.md`.

**The client can send a command and wait for its acknowledgement; no screen uses it yet.** `HydraBleClient.sendCommand`
turns on ACK after STATE (the link is reported connected only after both), writes the framed command and reports one of:
`Acknowledged` (the peripheral answered; `ack.ok` says whether the server applied it), `NoAnswer` (nothing answered within
4 seconds, or the link dropped: the command counts as **not delivered**), `NotAvailable` (the link is not ready, or the device
has no ACK characteristic: nothing was sent) or `TooLarge`. The BLE write response only proves that the radio delivered a frame
and is never taken as an acknowledgement, because a STOP that cannot be confirmed must not look confirmed. The control screens
still require the Wi-Fi session; wiring them to this call is the remaining step.

## Connection sequence

1. Connect (LE transport, no auto-connect).
2. Request MTU 247; continue when answered (or at once if the request cannot start).
3. Discover services. Missing HYDRA service: "not a HYDRA-UMC device"; missing or wrong characteristic
   properties: its own error. Neither is retried.
4. Enable notifications on STATE (write `0x0001` to its CCCD). The link is reported **connected** only
   after this succeeds.
5. State frames arrive; commands may be written.

A link that drops by itself is retried after 1 s, 2 s, 4 s, 8 s, 16 s; after the fifth failed attempt the
app stays disconnected and says so. A successful connection resets the count. "Disconnect" stops the
retries at once.

## Permissions

| Android | Scan | Connect |
| --- | --- | --- |
| 12 and newer | `BLUETOOTH_SCAN` (declared `neverForLocation`) | `BLUETOOTH_CONNECT` |
| 11 and older | `ACCESS_FINE_LOCATION` and Location Services switched on | none (the legacy `BLUETOOTH`/`BLUETOOTH_ADMIN` are declared up to API 30) |

A missing permission, a permission revoked while scanning or connecting, Bluetooth switched off, Location
Services off on Android 11 and older, a missing scanner and a malformed address each show their own
message in the language of the phone.

## What was verified

- Unit tests drive the whole client against a simulated GATT: the connection sequence (with and without ACK), MTU fallback,
  framing and reassembly in both directions (every size and MTU), damaged and lost frames, one write in
  flight, refused writes, a device without the service, missing characteristics, notifications refused,
  retry delays and the give-up, the retry budget reset, no leak of a partial message between links, and
  late events of a closed link.
- **Not verified on any phone, radio or peripheral:** the Android implementation (`AndroidBleGatt.kt`),
  the scan (`RobotViewModel.scanBluetooth`), the permission prompts and the behaviour of real chipsets
  (for example status 133 on the first connection after a failure).
