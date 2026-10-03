# Publishing Clearoo on Google Play

Clearoo is already set up for release:
- It targets Android 16 (API 36), which Google Play currently requires.
- Release builds are shrunk with R8.
- The **Play Store release** workflow builds a signed app bundle (`.aab`).

What's left needs your Google account. Work through these steps in order.

## 1. Create a Google Play developer account (once)

1. Go to https://play.google.com/console/signup.
2. Pay the one-time **US$25** fee and verify your identity with an ID document. Verification can take a few days.
3. Choose the account type:
   - A **personal** account is fine to start with.
   - **Heads-up:** a new personal account must run a **closed test with at least 12 testers for 14 days in a row** before Google lets you publish to everyone. Start recruiting friends early (see step 6).
   - An **organization** account skips that rule, but you need a registered business and a D-U-N-S number.

## 2. Add your upload key to GitHub (once)

Play signs the final app with its own key (*Play App Signing*). You sign each upload with an **upload key**. Keep the key file and passwords somewhere safe, like a password manager. If you lose them, Google can reset the upload key, but that takes a few days.

In GitHub, go to **Settings → Secrets and variables → Actions → New repository secret** and add these four secrets:

| Secret name | Value |
|---|---|
| `UPLOAD_KEYSTORE_BASE64` | The keystore file, base64-encoded (one long line of text) |
| `UPLOAD_KEYSTORE_PASSWORD` | The keystore password |
| `UPLOAD_KEY_ALIAS` | `upload` |
| `UPLOAD_KEY_PASSWORD` | The key password |

To make your own key instead of using the one generated for you:

```bash
keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10950
base64 -w0 upload.jks   # paste the output into UPLOAD_KEYSTORE_BASE64
```

Never commit the keystore to the repo.

## 3. Build the app bundle

1. Optionally bump `versionName` (the version users see, e.g. 1.0.1) in `app/build.gradle.kts`. The `versionCode` that Play checks is set automatically from the workflow's run number, so it always goes up.
2. In GitHub, open **Actions → Play Store release → Run workflow**. Alternatively, push a tag like `v1.0.0`.
3. When it's done, download the **clearoo-release** artifact. It contains:
   - `app-release.aab`: upload this to Play.
   - `app-release.apk`: a signed APK you can install to test the release build. It's signed with a different key from the debug builds, so uninstall the debug app first.
   - `mapping.txt`: upload this to Play Console so crash reports are readable.

## 4. Create the app in Play Console

1. Click **Create app**. Fill in:
   - App name: Clearoo
   - Default language
   - App or game: **App**
   - Free or paid: **Free**
2. The package name is **`com.clearoo.app`**. It's permanent once you upload, so change it now if you want something else.
3. Under **App content**, fill in each section. Copy the answers from [`play-store/listing.md`](play-store/listing.md).
   - **Privacy policy:** `https://github.com/beenow11/Clearoo/blob/HEAD/PRIVACY.md`
   - **Ads:** No.
   - **App access:** All functionality is available without special access.
   - **Content rating:** take the questionnaire. Clearoo is a utility, so answer No to everything.
   - **Target audience:** 13+.
   - **Data safety:** no data collected or shared.
   - **Photo and video permissions:** paste the declaration from `listing.md`. This is the one most likely to get questioned. Clearoo qualifies because broad gallery access is its core purpose.
4. Under **Store listing**:
   - Add the name and descriptions from `listing.md`.
   - **App icon:** `play-store/icon-512.png`
   - **Feature graphic:** `play-store/feature-graphic-1024x500.png`
   - **Phone screenshots:** at least 2, up to 8. Take them on your phone. Good picks: home screen with Roo, a card mid-swipe showing the DELETE stamp, smart decks, the bin, the celebration screen, the wardrobe. Clear any private photos from view first.

## 5. Internal testing (do this first)

1. Go to **Testing → Internal testing → Create release** and upload the `.aab`.
2. Add yourself as a tester and install from the opt-in link.
3. This is the first time the shrunk release build runs on a real phone. Check onboarding, swiping, emptying the bin, the widget and the reminder.

## 6. Closed testing (required for new personal accounts)

1. Go to **Testing → Closed testing**, create a track, upload the same `.aab`, and add **12+ testers** by email or with a Google Group.
2. Testers must opt in and keep the app installed for **14 consecutive days**.
3. Then apply for production access from the dashboard. Google asks a few questions about the test.

## 7. Production

Go to **Production → Create release**, upload, and roll out. A first review usually takes a few days. Updates are faster.

## Shipping updates later

1. Optionally bump `versionName`.
2. Run the release workflow.
3. Upload the new `.aab` to the track you want. Use a staged rollout (e.g. 20%) for production updates and watch Android vitals before going to 100%.

## Keeping user data safe across updates

- **Same package name and the same Play signing key:** every update installs in place, so streak, stats, settings, kept photos and the bin are all kept.
- **Stats, streak and settings are also backed up by Android,** so they come back after a reinstall or on a new phone. Kept-photo and bin IDs stay on the device on purpose, because they only make sense on the phone that recorded them.
- **Never rename or reuse a stored key** in `PrefsRepository.kt`. Add new ones instead.
- **Every push runs the emulator smoke test,** which installs the release build, uses it, re-installs it as an update and checks the stats are still there. Don't ship if it's red.
