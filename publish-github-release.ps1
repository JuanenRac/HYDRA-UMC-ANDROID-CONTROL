# =============================================================================
# HYDRA-UMC-ANDROID-CONTROL - publish-github-release.ps1
# Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
# GPL-3.0 - see LICENSE
#
# Publishes dist\HYDRA-UMC-ANDROID-CONTROL-release.apk (produced by
# prepare-github-release.bat/.sh) as a GitHub Release for the current
# app/version.properties version - creating the release if its tag doesn't
# exist yet, or replacing just the APK asset if it does (re-running a
# publish for the same version, e.g. after fixing a signing mistake,
# doesn't leave two conflicting assets).
#
# Requires a real personal GITHUB_TOKEN in a gitignored .env file (see
# .env.example) with 'repo' scope on JuanenRac's own account - the same
# security property "only I can publish" as everything else that needs
# this repo's private release keystore: nobody without both the keystore
# AND this personal token can produce and publish a trusted update. Never
# run in CI; this deliberately stays a private, local, operator-run step
# (see docs/GITHUB_RELEASE_UPDATES.md's own signing-requirement section).
#
# Silently does nothing (exit 0) when .env/GITHUB_TOKEN is absent, so
# prepare-github-release.bat/.sh can call this unconditionally after every
# real release build without failing someone else's checkout that has no
# publishing credential configured.
# =============================================================================

$ErrorActionPreference = 'Stop'
$repoRoot = $PSScriptRoot
Set-Location $repoRoot

$envFile = Join-Path $repoRoot ".env"
if (-not (Test-Path $envFile)) {
    Write-Output "publish-github-release.ps1: no .env file - skipping publish (local build only)."
    exit 0
}
$tokenLine = Get-Content $envFile | Where-Object { $_ -match '^GITHUB_TOKEN=' } | Select-Object -First 1
if (-not $tokenLine) {
    Write-Output "publish-github-release.ps1: .env has no GITHUB_TOKEN - skipping publish (local build only)."
    exit 0
}
$token = $tokenLine -replace '^GITHUB_TOKEN=', ''

$apkPath = Join-Path $repoRoot "dist\HYDRA-UMC-ANDROID-CONTROL-release.apk"
if (-not (Test-Path $apkPath)) {
    Write-Output "ERROR: $apkPath does not exist - run prepare-github-release.bat/.sh first."
    exit 1
}

function Read-VersionProperty([string]$name) {
    $line = Get-Content (Join-Path $repoRoot "app\version.properties") | Where-Object { $_ -match "^$name=" } | Select-Object -First 1
    return ($line -replace "^$name=", '').Trim()
}
$tag = "v" + (Read-VersionProperty "versionMajor") + "." + (Read-VersionProperty "versionMinor") + "." + (Read-VersionProperty "versionPatch")

$headers = @{
    Authorization = "Bearer $token"
    Accept        = "application/vnd.github+json"
    "User-Agent"  = "hydra-umc-android-control-publish-script"
}
$repoApi = "https://api.github.com/repos/JuanenRac/HYDRA-UMC-ANDROID-CONTROL"

$existing = $null
try {
    $existing = Invoke-RestMethod -Uri "$repoApi/releases/tags/$tag" -Headers $headers -Method Get
} catch {
    if ($_.Exception.Response -and $_.Exception.Response.StatusCode.value__ -ne 404) { throw }
}

if ($existing) {
    Write-Output "publish-github-release.ps1: release $tag already exists - replacing its APK asset."
    $release = $existing
    $staleAsset = $release.assets | Where-Object { $_.name -eq "HYDRA-UMC-ANDROID-CONTROL-release.apk" }
    if ($staleAsset) {
        Invoke-RestMethod -Uri "$repoApi/releases/assets/$($staleAsset.id)" -Headers $headers -Method Delete | Out-Null
    }
} else {
    Write-Output "publish-github-release.ps1: creating release $tag."
    [string]$changelogTop = ""
    $changelogPath = Join-Path $repoRoot "CHANGELOG.md"
    if (Test-Path $changelogPath) {
        $lines = Get-Content $changelogPath
        $start = ($lines | Select-String -Pattern '^## \[' | Select-Object -First 1).LineNumber
        if ($start) {
            $rest = $lines[$start..($lines.Count - 1)]
            $nextHeadingOffset = ($rest | Select-Object -Skip 1 | Select-String -Pattern '^## \[' | Select-Object -First 1)
            if ($nextHeadingOffset) {
                $rest = $rest[0..($nextHeadingOffset.LineNumber - 1)]
            }
            $changelogTop = ($rest -join "`n").Trim()
        }
    }
    $bodyObj = [PSCustomObject]@{
        tag_name   = $tag
        name       = "HYDRA-UMC CONTROL $tag"
        body       = if ($changelogTop) { $changelogTop } else { "See CHANGELOG.md." }
        draft      = $false
        prerelease = $false
    }
    $json = $bodyObj | ConvertTo-Json
    $release = Invoke-RestMethod -Uri "$repoApi/releases" -Method Post -Headers $headers -Body ([System.Text.Encoding]::UTF8.GetBytes($json)) -ContentType "application/json; charset=utf-8"
}

$uploadHeaders = $headers.Clone()
$uploadHeaders["Content-Type"] = "application/vnd.android.package-archive"
$uploadUri = "https://uploads.github.com/repos/JuanenRac/HYDRA-UMC-ANDROID-CONTROL/releases/$($release.id)/assets?name=HYDRA-UMC-ANDROID-CONTROL-release.apk"
$bytes = [System.IO.File]::ReadAllBytes((Resolve-Path $apkPath))
$asset = Invoke-RestMethod -Uri $uploadUri -Method Post -Headers $uploadHeaders -Body $bytes

Write-Output "Published: $($release.html_url)"
Write-Output "Asset: $($asset.browser_download_url) ($($asset.size) bytes)"
