# Releasing SleepSync

Pushing a `vX.Y.Z` tag runs `.github/workflows/release.yml`: it builds the release APK, signs it with a keystore from GitHub Secrets, and attaches it to a GitHub Release. Obtainium on the phone picks the release up.

`versionName` comes from the tag (`v1.2.3` -> `1.2.3`); `versionCode` is the workflow run number, so it only goes up. If a signing secret is missing the workflow fails and publishes nothing.

## One-time setup

1. Generate the keystore (Windows, Android Studio's bundled JBR):

   ```
   "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore sleepsync-release.jks -alias sleepsync -keyalg RSA -keysize 2048 -validity 10000
   ```

   Pick a store password and a key password and remember the alias (`sleepsync` above).

2. **Back up `sleepsync-release.jks` and both passwords somewhere safe (password manager, cloud drive).** Android only installs an update if it is signed with the same key. If you lose the keystore, the phone cannot update the app; you would have to uninstall it (losing app data) and install a new build signed with a new key. `*.jks` is gitignored; never commit it.

3. Base64-encode it (Git Bash) and set the secrets with the GitHub CLI:

   ```
   base64 -w0 sleepsync-release.jks | gh secret set SIGNING_KEYSTORE_BASE64 --repo yiyiscode/sleepsync
   gh secret set SIGNING_STORE_PASSWORD --repo yiyiscode/sleepsync
   gh secret set SIGNING_KEY_ALIAS --repo yiyiscode/sleepsync
   gh secret set SIGNING_KEY_PASSWORD --repo yiyiscode/sleepsync
   ```

   Each `gh secret set` without a piped value prompts for it.

4. On the phone, install Obtainium, tap **Add App**, enter `https://github.com/yiyiscode/sleepsync`, and install. Later releases show up as updates.

## Cut a release

From an up-to-date `main`:

```
git tag v0.0.1 && git push origin v0.0.1
```

Watch the **Release** run in the Actions tab; the signed `sleepsync-v0.0.1.apk` appears under Releases. Use a new tag each time (`v0.0.2`, ...).

## Dry run

Actions -> Release -> **Run workflow** builds an unsigned release APK (uploaded as a workflow artifact, not installable as an update) to check the build. It never signs or publishes.
