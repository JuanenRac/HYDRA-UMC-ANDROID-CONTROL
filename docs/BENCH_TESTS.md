# Bench tests

Every test below is **not executed** on a real phone. The app builds and its unit tests pass, but nothing in this file has been checked on a device. Write a result only after a person ran the step.

## Setup

- Two phones: one with Android 9 or older and one with Android 13 or newer, each with a fingerprint or a face enrolled and a screen lock.
- A HYDRA server reachable over the network, with and without TLS, and at least one robot and one camera assigned to it.
- The release APK signed with the key of the project, installed over the previous version.

## Steps

| # | Step | Expected result | Result |
|---|------|-----------------|--------|
| 1 | Install the new APK over the old one. | It installs as an update and keeps the session. | not executed |
| 2 | Pair with a link that carries a fingerprint, on a server with TLS. | The app pairs and stores the fingerprint. | not executed |
| 3 | Pair with a link without a fingerprint, on a server without TLS. | The behaviour documented for that case; note whether it differs from the iOS app. | not executed |
| 4 | Open the camera of a robot whose camera id comes from the robot assignment. | The camera shown is the one of that robot; with no match the app says so. | not executed |
| 5 | Cut the Wi-Fi for 20 s while a camera is open and restore it. | The app reconnects with backoff and sends the credential again. | not executed |
| 6 | Open the biometric prompt on both phones. | The prompt appears; a cancelled prompt does not unlock; the look of the prompt is acceptable with the current UI libraries. | not executed |
| 7 | Scan for a Bluetooth device on each phone. | Android 9 or older asks for the old permissions, Android 12 or newer for scan and connect; the location permission is asked only for network discovery. | not executed |
| 8 | Check a firmware download whose digest is correct, then one whose digest is changed. | The first is accepted, the second refused with a message. | not executed |
| 9 | Hold a jog and put the phone to sleep. | The robot stops (dead-man). | not executed |

## What this bench cannot show

Binding the biometric key to the Android keystore with a crypto object is not implemented; step 6 only checks the prompt.
