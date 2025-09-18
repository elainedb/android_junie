# android_junie

## CI builds without committing google-services.json

The project uses Google Sign-In but does not require the Firebase Google Services Gradle plugin to run unit tests. The build is configured to apply the Google Services plugin only when app/google-services.json is present locally. On CI, where this file is intentionally absent, the plugin is not applied, so tasks like :app:testDebugUnitTest and :app:JacocoDebugCodeCoverage run successfully without secrets.

- Local: keep your app/google-services.json to enable Firebase-related features.
- CI: do not commit google-services.json. The build script skips applying the plugin when the file is missing.

## Authorized emails configuration (DO NOT COMMIT secrets)

The list of authorized Google emails is NOT stored in source control. Instead, it is injected at build time and compiled into BuildConfig as AUTHORIZED_EMAILS. The app reads this list at runtime.

Resolution order (first non-empty wins):
1) Environment variable AUTHORIZED_EMAILS (comma-separated)
2) File authorized_emails.txt at the repository root (one email per line, supports comments with #)
3) Fallback to dummy emails in CI: dummy1@example.com,dummy2@example.com

Local development:
- Create a file authorized_emails.txt at the repo root (this file is ignored by Git) with your real emails, e.g.:

```
# one email per line
elaine.batista1105@gmail.com
paulamcunha31@gmail.com
edbpmc@gmail.com
```

Alternatively, you can export an env var before building:
- macOS/Linux:
```
export AUTHORIZED_EMAILS="elaine.batista1105@gmail.com,paulamcunha31@gmail.com,edbpmc@gmail.com"
./gradlew :app:assembleDebug
```
- Windows (PowerShell):
```
$Env:AUTHORIZED_EMAILS = "elaine.batista1105@gmail.com,paulamcunha31@gmail.com,edbpmc@gmail.com"
./gradlew :app:assembleDebug
```

CI behavior:
- .github/workflows/build.yml sets AUTHORIZED_EMAILS to dummy emails so CI builds use non-sensitive values.

Where it’s used:
- app/build.gradle.kts reads the source and generates BuildConfig.AUTHORIZED_EMAILS (String[]).
- LoginActivity uses BuildConfig.AUTHORIZED_EMAILS.toSet() to validate sign-ins.

## Verify signing certificate (SHA1) with keytool

The project’s release build is signed using the debug keystore (on purpose), so the release APK is signed with the standard Android debug certificate.

Expected SHA1 fingerprint for the signing certificate:
- Continuous: 6e540ca025e0ab3ab8aa81fcf7833a1b0a1b238b
- Colon-separated (uppercase): 6E:54:0C:A0:25:E0:AB:3A:B8:AA:81:FC:F7:83:3A:1B:0A:1B:23:8B

You can verify this locally with keytool in several ways.

### 1) Debug keystore directly

macOS/Linux:

```
keytool -list -v \
  -alias androiddebugkey \
  -keystore ~/.android/debug.keystore \
  -storepass android \
  -keypass android
```

Windows (PowerShell or CMD):

```
keytool -list -v \
  -alias androiddebugkey \
  -keystore "%USERPROFILE%\.android\debug.keystore" \
  -storepass android \
  -keypass android
```

Look for the line that starts with SHA1:. It should match the expected SHA1 above.

### 2) From the built release APK/AAB

After assembling the artifact, print the signing certificate directly from the file.

- APK:
```
./gradlew :app:assembleRelease
keytool -printcert -jarfile app/build/outputs/apk/release/app-release.apk
```

- AAB (if you build a bundle):
```
./gradlew :app:bundleRelease
keytool -printcert -jarfile app/build/outputs/bundle/release/app-release.aab
```

Compare the reported SHA1 with the expected one above.

### 3) From an APK already installed on a device/emulator

```
adb shell pm path dev.elainedb.android_junie
# Example output: package:/data/app/~~XYZ==/dev.elainedb.android_junie-abc123==/base.apk
adb pull /data/app/~~XYZ==/dev.elainedb.android_junie-abc123==/base.apk ./downloaded.apk
keytool -printcert -jarfile ./downloaded.apk
```

Again, ensure the SHA1 matches the expected fingerprint.

Notes:
- keytool is included with the JDK. If the command isn’t found, ensure Java is installed and keytool is on your PATH.
- If you later change the signing configuration to use a different keystore, the SHA1 will change. Update this README accordingly.