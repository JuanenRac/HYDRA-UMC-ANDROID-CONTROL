# Contributing to HYDRA-UMC-ANDROID-CONTROL 📱

## Technology Stack
- **Language**: Kotlin 2.x.
- **UI**: Jetpack Compose.
- **Storage**: DataStore + EncryptedSharedPreferences.

## Guidelines
1. **Lifecycle**: Sockets must be properly closed in `onCleared()` or via `LaunchedEffect` keys.
2. **Permissions**: Request runtime permissions (NSD/Bluetooth) explicitly in `MainActivity`.
3. **Composition**: Avoid complex calculations in `@Composable` functions; move them to the `RobotViewModel`.
