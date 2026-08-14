# GhostKernel 2.0 Integration Roadmap

## Baseline 0 — Frozen working chassis
GhostKernel builds and behaves exactly as before. Only CI and integration documentation are added.

## Stage 1 — NouTube native subsystem import
Import NouTube native Android/WebView sources behind a disabled integration boundary. Do not expose UI yet.

## Stage 2 — NouTube surface
Expose the complete NouTube experience from GhostKernel without replacing GhostKernel playback or Home UI.

## Stage 3 — Feature parity verification
Verify login/session, playback, downloads, album downloads, Poweramp handoff, KornDog generators, and TV/casting.

## Stage 4 — Shared event bridge
Send NouTube track/search/play/skip/replay/download events to Ghost Brain analytics without changing NouTube behavior.

## Stage 5 — Unified intelligence
Use the shared events to improve Music DNA, Ghost Picks, Rabbit Hole, rediscovery and discovery scoring.

## Stage 6 — Shared presentation polish
Apply preferred NouTube GhostKernel branding assets while preserving GhostKernel's green-boat waveform and Home controls.

## Stage 7 — Consolidation
Only after parity tests pass, retire proven duplicate plumbing one component at a time.
