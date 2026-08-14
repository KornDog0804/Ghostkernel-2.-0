# GhostKernel -> KornOS Sender v1

Adds a **Send to KornOS** button to the existing Ghost Brain discovery card.

The sender exports a sanitized snapshot containing:
- the current Ghost Brain headline/subtext/action
- Ghost Brain seed songs
- up to 12 Recently Haunted songs
- up to 10 top artists
- up to 6 suggested KornOS topics

It sends the snapshot to the KornOS Netlify function using:
- `KORNOS_SYNC_URL`
- `KORNOS_SYNC_KEY`

Both values are injected into `BuildConfig` only at build time from GitHub Actions secrets.
The GitHub token remains server-side in KornOS/Netlify and is never placed in GhostKernel.

## Required GitHub secrets in GhostkernelApp

- `KORNOS_SYNC_URL`
  - Example: `https://YOUR-KORNOS-SITE.netlify.app/.netlify/functions/sync-ghostkernel`
- `KORNOS_SYNC_KEY`
  - Must exactly match the `GHOSTKERNEL_SYNC_KEY` environment variable configured in the KornOS Netlify site.

## Required Netlify environment variable in KornOS

- `GHOSTKERNEL_SYNC_KEY`
  - Use a long random value.

## Files changed

- `app/build.gradle.kts`
- `.github/workflows/build-apk.yml`
- `app/src/main/kotlin/com/github/soundpod/repository/KornOsBridgeRepository.kt` (new)
- `app/src/main/kotlin/com/github/soundpod/viewmodels/home/GhostBrainViewModel.kt`
- `app/src/main/kotlin/com/github/soundpod/ui/screens/home/DiscoveryCard.kt`

## Verification status

The patch was reviewed against the uploaded GhostKernel source and matches the live KornOS sync schema.
A local Gradle compile could not run in the artifact sandbox because it could not reach `services.gradle.org` to download Gradle. GitHub Actions is the real compile/build check after pushing.
