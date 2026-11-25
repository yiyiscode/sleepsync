# Google Drive setup (human-only steps)

The app asks for the `https://www.googleapis.com/auth/drive.file` scope only. Google identifies the app by package name plus signing-certificate SHA-1, so there is no client secret in the code. Console layouts change; where a step says "check current docs", follow the current Google Cloud documentation for OAuth client creation.

Package name: `dev.sleepsync.app`

## 1. Get the debug SHA-1

Either:

- `./gradlew signingReport` and copy `SHA1` from the `debug` variant of `:app`, or
- `keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android`

Debug keystores differ per machine. A CI-built APK is signed with a different key, so it needs its own OAuth client later (release ticket).

## 2. Google Cloud project

1. Open the Google Cloud console and create (or pick) a project.
2. Enable the Google Drive API for it (APIs & Services, Library).
3. Configure the OAuth consent screen (check current docs for the current page name). Choose External, keep the publishing status as Testing, and add your own Google account as a test user. Add the `drive.file` scope if the page asks for scopes.
4. Create an OAuth client ID of type Android with package name `dev.sleepsync.app` and the SHA-1 from step 1 (APIs & Services, Credentials; check current docs for exact labels).

Note: apps in Testing status may have refresh grants expire after about 7 days. Re-consent when asked.

## 3. Manual phone run

1. Install the debug APK and Health Connect (built in on Android 14+).
2. Make sure Health Connect holds some sleep data.
3. Open SleepSync, tap Sync, grant the sleep read permission, then the Google account and Drive consent.
4. Expect "Synced N nights into ...". A `SleepSync` folder with `sleep-YYYY-MM.csv` should appear in Drive; confirm Claude can read it via the Drive connector.
