# TAKEOVER

- Source: `android/app/src/main/java/community/auroville/agentmanager/` (`Api.kt` the phone door client and settings store, `MainActivity.kt` the whole Compose UI). Version: `android/gradle.properties` `appVersion=N`, whole numbers.
- Server side lives in the Mac repo AGENT_MANAGER (`helper/server.py`, v11+): GET /overview, GET /plans, POST /command {slug, agent, task, action, text}; bearer token; Tailscale address only; port 8847.
- Release: push to main, the workflow builds and marks vN Latest. Install on the Pixel 7: `gh release download --repo markoboskoauroville/AGENT_MANAGER_ANDROID --pattern "*.apk"`, `adb install -r`.
- Commands are written by the Mac server as files in `~/.mantra-os/commands/<slug>/`; the master session executes them. The phone never runs anything itself.
