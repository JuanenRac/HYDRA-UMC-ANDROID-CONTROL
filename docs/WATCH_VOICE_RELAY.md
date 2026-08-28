<!-- =============================================================================
HYDRA-UMC-ANDROID-CONTROL - Paired Watch voice relay contract
Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
GPL-3.0 - see LICENSE
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

The Data Layer is the official paired Android/Wear transport, not a custom
Bluetooth socket. Google Play services accepts messages only when the Watch
and Android Control APKs have the same `applicationId`
(`com.hydraumc.control`) and signing certificate; it encrypts the channel
over Bluetooth or its relay path. The Watch never receives the Server JWT or
Voice UI token. A real paired device remains required to validate radio,
account and speech-engine behaviour.
