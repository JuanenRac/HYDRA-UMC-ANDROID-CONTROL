<!-- =============================================================================
HYDRA-UMC-ANDROID-CONTROL - Paired Watch voice relay contract
Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
GPL-3.0 - see LICENSE
============================================================================= -->

# Paired Watch voice relay

Android Control owns the authenticated client session to HYDRA-UMC-SERVER. A
future Wear Data Layer receiver must pass only an already-recognised,
user-initiated `WatchVoiceTurn` to `RobotViewModel.relayWatchVoiceTurn()`.
That method uses the normal Server bearer token and receives a typed
`WatchAssistantReply`.

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

The Wear Data Layer/Bluetooth transport itself is intentionally not emulated
without a real paired watch. It must authenticate and associate a device
before invoking these relay methods; it must never embed the Voice UI token.
