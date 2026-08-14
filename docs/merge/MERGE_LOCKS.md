# GhostKernel 2.0 Merge Locks

These are regression locks for the GhostKernel + NouTube integration. A merge stage does not advance when any locked item regresses.

## GhostKernel locked behavior
- Green boat riding the music waveform/progress visualization remains intact.
- Existing Home screen controls remain intact and functional.
- Ghost Brain remains functional.
- Music DNA remains functional.
- Rabbit Hole remains functional.
- Ghost Picks/recommendation behavior remains functional.
- Native playback remains functional.
- Android Auto remains functional.
- Existing library/history/database behavior remains functional.

## NouTube locked behavior before transplant
- Full YouTube Music/NouTube experience.
- Existing Google/YouTube account and session behavior.
- Existing NouTube WebView bridge and injected KornDog controls.
- Track and album downloads.
- Download history.
- Poweramp handoff.
- KornDog Discovery generator.
- KornDog Streaming generator.
- KornDog Now Spinning/site workflow.
- TV/casting behavior.
- Existing NouTube controls and navigation.
- NouTube GhostKernel logos/branding assets are the preferred branding source where they are superior.

## Merge rules
1. Merge by addition before replacement.
2. Keep the original GhostKernel and NouTube snapshots untouched.
3. Do not delete duplicate functionality until the merged replacement passes regression testing.
4. Each subsystem lands in its own checkpoint commit.
5. Playback ownership remains unchanged until a dedicated playback integration stage.
6. Do not redesign locked UI while wiring backend integration.
7. Every stage must produce a runnable APK before the next stage begins.
