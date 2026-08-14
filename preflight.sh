#!/usr/bin/env bash
set -e

echo "=== GhostKernel Preflight ==="

echo
echo "1) Git status"
git status --short

echo
echo "2) Current branch"
git branch --show-current

echo
echo "3) Audio baseline check"
grep -A3 "fun setLoudnessBoost" app/src/main/kotlin/com/github/soundpod/service/AudioEffectManager.kt

echo
echo "4) Checking playlist navigation files"
grep -R "Routes.Playlist" -n app/src/main/kotlin/com/github/soundpod || true
grep -R "onPlaylistClick" -n app/src/main/kotlin/com/github/soundpod || true
grep -R "PlaylistSongs" -n app/src/main/kotlin/com/github/soundpod || true

echo
echo "5) GitHub Actions handles compilation"
echo "Skipping local Gradle compile (no Android SDK in Termux)"

echo
echo "=== Preflight passed ==="
