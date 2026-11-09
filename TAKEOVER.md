# TAKEOVER

- Source: `android/app/src/main/java/community/auroville/agentmanager/` (`Api.kt` the phone door client and settings store, `MainActivity.kt` the whole Compose UI). Version: `android/gradle.properties` `appVersion=N`, whole numbers.
- Server side lives in the Mac repo AGENT_MANAGER (`helper/server.py`, v11+): GET /overview, GET /plans, POST /command {slug, agent, task, action, text}; bearer token; Tailscale address only; port 8847.
- Release: push to main, the workflow builds and marks vN Latest. Install on the Pixel 7: `gh release download --repo markoboskoauroville/AGENT_MANAGER_ANDROID --pattern "*.apk"`, `adb install -r`.
- Commands are written by the Mac server as files in `~/.mantra-os/commands/<slug>/`; the master session executes them. The phone never runs anything itself.
- v5 (9.11.2026): `VersionLabel` (upper right of the header on every screen, "v<BuildConfig.VERSION_NAME>", tap opens https://github.com/markoboskoauroville/AGENT_MANAGER_ANDROID/releases/latest; MANTRA_MANIFEST modules/app-version-and-parity.md). Parity with the Mac AGENT_MANAGER v19: `UsageHeader` (SESSION 5 h and WEEK in orange, thin bars), `RunningList` (busy sessions + running agents, model in orange, idle gone), `BlockerList` WHAT STOPPED ME (GET /blockers, orange cards, SOLVE = POST /blocker/solve, DONE = /blocker/done, hidden when empty; Mac v16-v19). The bottom status bar is gone, as on the Mac (v18). Parity rule: every Mac feature lands here the same day, or is listed here as next. Next: nothing outstanding from Mac v19.


## From the former README (9.11.2026)

# Agent Manager (CEO) (Mantra) for Android

The CEO status of the Mac app on any phone: usage header (SESSION, WEEK), RUNNING, WHAT STOPPED ME (SOLVE / DONE), totals tiles with SOLO filters, Session Manager plan cards (goal, progress, IN FOCUS, workers, check marks, last report, WAITING FOR YOU with a reply box), sessions per device (the model in orange for busy ones), the modes and usage strip pinned at the bottom, and PAUSE / STOP / MESSAGE per task. Refreshes every 5 s while the app is visible. Portrait, dark.

**Everything goes through Tailscale.** The Mac app (AGENT_MANAGER v19) runs a tiny HTTP door bound only to the Mac's Tailscale address, port 8847, protected by a bearer token. The phone must have Tailscale on; otherwise the app says CONNECT TAILSCALE.

- Setup on the phone: SETTINGS, the Mac's Tailscale address (100.x.y.z), the token (file `~/Downloads/API/agent-manager-token.txt` on the Mac). Stored only on the phone.
- Setup by adb: `adb shell am start -n community.auroville.agentmanager/.MainActivity --es address 100.x.y.z:8847 --es token <token>`.
- This repository is public and holds no secret: no address, no token, no keystore (the signing key is the GitHub secret `ANDROID_DEBUG_KEYSTORE_B64`).
- Compiled only by GitHub Actions (`.github/workflows/android.yml`); every push to main releases `v<appVersion>` as Latest. Install the APK from the latest release; each version installs over the last.
- The version `v<N>` sits in the upper right; a tap opens the latest release.
