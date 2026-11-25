# SleepSync

A personal Android app (Kotlin + Jetpack Compose) that reads sleep sessions from Health Connect and uploads them to Google Drive as one CSV per month, so Claude can read them through the Drive connector. Spec: `docs/specs/sleepsync-v1.md`.

Modules: `core` (pure Kotlin/JVM, no Android imports) and `app` (Android UI, depends on `core`).

## Build and test

```
./gradlew test                      # unit tests, all modules
./gradlew lint                      # Android lint
./gradlew assembleDebug             # APK at app/build/outputs/apk/debug/app-debug.apk
./gradlew test lint assembleDebug   # what CI runs
```

CI (job `build` in `.github/workflows/ci.yml`) runs the last command on every push and pull request; `main` requires it.

## Local Android setup

1. Install Android Studio and finish the Setup Wizard (or open SDK Manager) so that Android SDK Platform-Tools, an Android SDK Platform and Build-Tools are installed.
2. Set `ANDROID_HOME` to the SDK folder and `JAVA_HOME` to Android Studio's bundled JDK (the `jbr` folder inside the Android Studio install directory).
3. On the phone, enable Developer options and USB debugging, then connect it by USB.
4. Check that `adb devices` lists the phone, then run `./gradlew installDebug`.
