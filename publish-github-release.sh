#!/usr/bin/env bash
# =============================================================================
# HYDRA-UMC-ANDROID-CONTROL - publish-github-release.sh
# Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
# GPL-3.0 - see LICENSE
#
# POSIX sibling of publish-github-release.ps1 - see that file's own header
# for the full rationale (why this is a local-only, personal-token step,
# never CI). Publishes dist/HYDRA-UMC-ANDROID-CONTROL-release.apk as a
# GitHub Release for the current app/version.properties version, creating
# the release if its tag doesn't exist yet or replacing just the APK asset
# if it does.
# =============================================================================
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

if [[ ! -f .env ]]; then
  echo "publish-github-release.sh: no .env file - skipping publish (local build only)."
  exit 0
fi
token="$(grep -E '^GITHUB_TOKEN=' .env | head -1 | sed 's/^GITHUB_TOKEN=//')"
if [[ -z "$token" ]]; then
  echo "publish-github-release.sh: .env has no GITHUB_TOKEN - skipping publish (local build only)."
  exit 0
fi

apk="dist/HYDRA-UMC-ANDROID-CONTROL-release.apk"
if [[ ! -f "$apk" ]]; then
  echo "ERROR: $apk does not exist - run prepare-github-release.sh first." >&2
  exit 1
fi

major="$(grep '^versionMajor=' app/version.properties | cut -d= -f2)"
minor="$(grep '^versionMinor=' app/version.properties | cut -d= -f2)"
patch="$(grep '^versionPatch=' app/version.properties | cut -d= -f2)"
tag="v${major}.${minor}.${patch}"

repo_api="https://api.github.com/repos/JuanenRac/HYDRA-UMC-ANDROID-CONTROL"
auth_header="Authorization: Bearer ${token}"

http_status="$(curl -s -o /tmp/hydra_release_existing.json -w '%{http_code}' \
  -H "$auth_header" -H "Accept: application/vnd.github+json" \
  "${repo_api}/releases/tags/${tag}")"

if [[ "$http_status" == "200" ]]; then
  echo "publish-github-release.sh: release ${tag} already exists - replacing its APK asset."
  release_id="$(grep -o '"id": *[0-9]*' /tmp/hydra_release_existing.json | head -1 | grep -o '[0-9]*')"
  # Real bug found and fixed: python3 was handed the bare path
  # '/tmp/hydra_release_existing.json' to open() itself - on a Windows
  # dev machine using Git Bash, that path is a real, bash-resolvable MSYS
  # mount, but the plain Windows python3.exe this line actually invokes
  # has no idea what to do with it and threw FileNotFoundError, silently
  # leaving stale_asset_id empty (the enclosing $(...) still "succeeds"
  # from bash's point of view) - so the stale APK asset below was never
  # actually deleted, and a later "replace" run just left an old,
  # already-shipped versionCode's APK live under the new tag until this
  # was caught by hand. Piping the file in via stdin (bash itself opens
  # it, not python3) sidesteps the whole path-resolution mismatch, and
  # works identically on a real POSIX shell too.
  stale_asset_id="$(python3 -c "
import json, sys
d = json.load(sys.stdin)
for a in d.get('assets', []):
    if a['name'] == 'HYDRA-UMC-ANDROID-CONTROL-release.apk':
        print(a['id'])
        break
" < /tmp/hydra_release_existing.json)"
  if [[ -n "${stale_asset_id:-}" ]]; then
    curl -s -X DELETE -H "$auth_header" -H "Accept: application/vnd.github+json" \
      "${repo_api}/releases/assets/${stale_asset_id}" > /dev/null
  fi
else
  echo "publish-github-release.sh: creating release ${tag}."
  changelog_top="$(awk '/^## \[/{if(found)exit; found=1} found' CHANGELOG.md 2>/dev/null || true)"
  [[ -z "$changelog_top" ]] && changelog_top="See CHANGELOG.md."
  body_json="$(python3 -c "
import json, sys
print(json.dumps({
    'tag_name': sys.argv[1],
    'name': f'HYDRA-UMC CONTROL {sys.argv[1]}',
    'body': sys.argv[2],
    'draft': False,
    'prerelease': False,
}))
" "$tag" "$changelog_top")"
  curl -s -X POST -H "$auth_header" -H "Accept: application/vnd.github+json" \
    -H "Content-Type: application/json" -d "$body_json" \
    "${repo_api}/releases" -o /tmp/hydra_release_new.json
  release_id="$(grep -o '"id": *[0-9]*' /tmp/hydra_release_new.json | head -1 | grep -o '[0-9]*')"
fi

if [[ -z "${release_id:-}" ]]; then
  echo "ERROR: could not determine release id for ${tag}." >&2
  exit 1
fi

upload_url="https://uploads.github.com/repos/JuanenRac/HYDRA-UMC-ANDROID-CONTROL/releases/${release_id}/assets?name=HYDRA-UMC-ANDROID-CONTROL-release.apk"
asset_json="$(curl -s -X POST -H "$auth_header" -H "Content-Type: application/vnd.android.package-archive" \
  --data-binary @"$apk" "$upload_url")"

echo "Published: https://github.com/JuanenRac/HYDRA-UMC-ANDROID-CONTROL/releases/tag/${tag}"
echo "$asset_json" | grep -o '"browser_download_url": *"[^"]*"' || true
