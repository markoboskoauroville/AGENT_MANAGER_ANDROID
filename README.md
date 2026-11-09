# Agent Manager (CEO) (Mantra) for Android

The CEO status of the Mac app on any phone: usage header (SESSION, WEEK), RUNNING, WHAT STOPPED ME (SOLVE / DONE), totals tiles with SOLO filters, Session Manager plan cards (goal, progress, IN FOCUS, workers, check marks, last report, WAITING FOR YOU with a reply box), sessions per device (the model in orange for busy ones), the modes and usage strip pinned at the bottom, and PAUSE / STOP / MESSAGE per task. Refreshes every 5 s while the app is visible. Portrait, dark.

**Everything goes through Tailscale.** The Mac app (AGENT_MANAGER v19) runs a tiny HTTP door bound only to the Mac's Tailscale address, port 8847, protected by a bearer token. The phone must have Tailscale on; otherwise the app says CONNECT TAILSCALE.

- Setup on the phone: SETTINGS, the Mac's Tailscale address (100.x.y.z), the token (file `~/Downloads/API/agent-manager-token.txt` on the Mac). Stored only on the phone.
- Setup by adb: `adb shell am start -n community.auroville.agentmanager/.MainActivity --es address 100.x.y.z:8847 --es token <token>`.
- This repository is public and holds no secret: no address, no token, no keystore (the signing key is the GitHub secret `ANDROID_DEBUG_KEYSTORE_B64`).
- Compiled only by GitHub Actions (`.github/workflows/android.yml`); every push to main releases `v<appVersion>` as Latest. Install the APK from the latest release; each version installs over the last.
- The version `v<N>` sits in the upper right; a tap opens the latest release.
