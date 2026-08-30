<!-- =============================================================================
     HYDRA-UMC-ANDROID-CONTROL - GitHub Release update-channel operation guide
     Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
     GPL-3.0 - see LICENSE
     ============================================================================= -->

# HYDRA-UMC-ANDROID-CONTROL GitHub Release updates

The Android application checks the latest stable GitHub Release when it
starts and exposes the same operation under **Settings → Updates**. It never
downloads an APK automatically: an operator selects **Download and install**
and Android's package installer asks for the final consent.

## Release contract

Create a non-draft, non-prerelease GitHub Release with a stable semantic tag:

```text
vMAJOR.MINOR.PATCH
```

Attach the signed release APK using this exact filename:

```text
HYDRA-UMC-ANDROID-CONTROL-release.apk
```

The client deliberately ignores every other asset. This prevents it from
accidentally downloading a debug APK, mapping file, source archive, or an APK
for a different product.

## Signing requirement

All distributed releases must use one protected, long-lived Android release
keystore. Android's package installer verifies that an update is signed by the
same certificate as the installed application; a differently signed APK is
rejected even if it has a higher version number.

The current debug signing fallback is suitable only for local testing. Before
the first public GitHub Release, copy `keystore.properties.example` to the
ignored `keystore.properties`, supply the protected release-keystore values,
and retain an encrypted backup of that key. Losing the key prevents in-place
updates to existing installations.

To prepare the exact artifact without incrementing a version again, run one of
these after the normal version-bumping build:

```text
prepare-github-release.bat
./prepare-github-release.sh
```

The scripts refuse to run without all private signing values and write only
`dist/HYDRA-UMC-ANDROID-CONTROL-release.apk`, the exact asset name required by
the updater.

## Publishing the artifact

Creating and publishing the GitHub Release is still deliberately gated behind
one thing only its owner has: a personal GitHub token, never a CI secret.

Copy `.env.example` to `.env` (gitignored) and set `GITHUB_TOKEN` to a
personal access token with `repo` scope on your own GitHub account. Once
that's in place, `prepare-github-release.bat`/`.sh` automatically calls
`publish-github-release.ps1`/`.sh` right after a successful signed build -
it creates the release for the current `app/version.properties` version (or
replaces just the APK asset if that version's release already exists) and
uploads `dist/HYDRA-UMC-ANDROID-CONTROL-release.apk` to it. No `.env` means
no auto-publish: the script silently stops after producing the local
artifact, exactly like before this existed, so a checkout with no personal
token configured still works for local builds.

This intentionally stays a private, locally-run step - never a GitHub
Actions workflow - for the same reason release signing itself does: no
secret that could publish a trusted, auto-installed update to every existing
installation is ever handed to CI.

## Device flow

1. The app requests only the latest release metadata over HTTPS.
2. If its tag is newer than the installed stable version and it contains the
   exact asset above, the app displays an update prompt.
3. The operator chooses **Download and install**.
4. The app checks the response length when GitHub provides one, rejects and
   removes a truncated download, then checks that the archive belongs to
   `com.hydraumc.control` and has a larger Android `versionCode`. Invalid,
   wrong-package, and non-newer cached APKs are removed rather than retained
   for a later install attempt.
5. Android's own installer validates the signing certificate and requests the
   platform-required installation approval.
6. If Android opens the one-time unknown-sources permission page, returning to
   Android Control revalidates the already downloaded APK and resumes the
   system-installer handoff. It does not download the asset twice.

On Android 8 and later, the operator must allow this application to install
unknown-source packages once in Android Settings. The app opens that specific
system settings page when required; it cannot bypass it.
