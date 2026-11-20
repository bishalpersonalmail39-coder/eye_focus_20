# OptiRest Android — Native Eye Care & 20-20-20 Assistant

Native Kotlin + Jetpack Compose Android project designed to compile directly into an `.apk` via **GitHub Actions** or **Android Studio**.

## Core Native Features
- **Starts Automatically on Boot**: `BootCompletedReceiver` listens for `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` and starts `EyeCareForegroundService`.
- **Battery Optimization Exemption**: Prompts for `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` so Android Doze mode does not kill background 20-20-20 tracking.
- **Screen-On Only Tracking (Excludes Off-Screen Audio)**: `EyeCareForegroundService` listens to `ACTION_SCREEN_ON`, `ACTION_SCREEN_OFF`, and `ACTION_USER_PRESENT`. Counting freezes immediately when the screen turns off (e.g., playing music with screen locked) with zero background polling.
- **20-20-20 Break Alerts**: Persistent status + high-priority break notifications with sound, vibration, Snooze 5m, and Skip controls.
- **System-Wide Blue-Light Filter**: `BlueLightOverlayManager` draws an adjustable warm amber tint over all Android apps using `SYSTEM_ALERT_WINDOW`.

## Build an APK Using GitHub Actions
1. Extract this zip folder and push its contents to a new GitHub repository (make sure `.github/workflows/build-apk.yml` is at the root of your repository).
2. Open the **Actions** tab in your GitHub repository.
3. Wait for the **Build OptiRest Android APK** workflow to finish (or click **Run workflow**).
4. Download **OptiRest-debug-apk** from the **Artifacts** section at the bottom of the workflow run, extract it, and install `app-debug.apk` on your Android phone.
