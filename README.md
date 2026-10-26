# Rotate360

Android tool that forces screen orientation (Portrait / Landscape / Reverse / Free 360°) over **any** running app.
It runs as a foreground service with an invisible overlay window plus an optional draggable quick-switch bubble.

## Build with GitHub
1. Create a new GitHub repo and upload everything in this folder (keep `.github/workflows/build.yml`).
2. Open the **Actions** tab -> **Build APK** -> wait for the green check.
3. Open the finished run -> **Artifacts** -> download `Rotate360-debug-apk`, unzip, install `app-debug.apk`.

## First run on the phone
1. Open Rotate360 -> tap **Start** -> allow "Display over other apps".
2. Allow notifications (Android 13+).
3. Pick a mode, or use the floating bubble inside any app.
