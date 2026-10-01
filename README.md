# Clearoo 🦘

Swipe your gallery clean. A few swipes a day, Tinder-style:
**swipe left to delete, right to keep.** Roo, a kangaroo mascot, reminds you every
evening and celebrates your streak.

## Features (v0.1)

- **Swipe deck.** Random photos and videos appear as full-screen cards. They follow your finger, tilt, show KEEP/DELETE stamps, and fly off with spring physics. A light vibration marks the point where a swipe counts.
- **Buttons.** ✕ to delete, ♥ to keep, ↺ to undo.
- **Video previews.** Videos autoplay muted on the top card. Tap to unmute. Tap a photo to switch between cropped and full view.
- **The bin.** Deletes are collected first. Review them in a grid, rescue anything you want to keep, then confirm once. Items go to the **system trash**, where they can be recovered for 30 days. A setting lets you skip the trash and delete permanently.
- **Daily goal and streak.** The goal is 10 a day by default (adjustable). **5 deletes keep the streak alive.** A Duolingo-style row shows the last 7 days.
- **Roo the mascot** has 9 expressions (hopeful, happy, proud, worried, sad, sleepy, love, shocked, excited):
  - On the home screen, Roo's mood follows your streak and the time of day.
  - While swiping, Roo reacts live: shocked as you drag toward delete, heart-eyed toward keep.
  - Roo celebrates with confetti after you clear the bin.
- **Home-screen widget.** Roo's face changes with your progress, e.g. worried in the evening if your streak is at risk.
- **Daily notification** at a time you choose (evening by default). It's skipped if today's streak is already secured. Tapping it opens straight into swiping.
- **Optional one-tap clearing** (Android 12+). Grant "Media management" in Settings to skip Android's delete confirmation.

### v0.2: Smart decks

- **Quick mix** is the main "Start swiping" button: a random handful of photos and videos.
- **Smart decks** on the home screen, each showing its item count and size:
  - 🐘 **Biggest**: largest files first, for the most space per swipe
  - 📸 **Screenshots**: screenshots and screen recordings
  - 💬 **Chat media**: WhatsApp, Telegram, Instagram and other chat apps, plus Downloads
  - 👯 **Look-alikes**: burst and near-duplicate shots, shown back to back ("Look-alike 2/3")
  - 😵 **Blurry**: Roo scans a sample of photos and offers the blurriest
  - 🕰️ **Old memories**: photos older than 2 years, oldest first
  - 🎬 **Videos**: biggest first
- **Roo's pick** highlights the deck (other than Biggest) that would free the most space.
- **Favourites are never shown.** Anything you've starred in your gallery app is skipped.

Everything runs on the device. No accounts, no uploads.

## Install

Every push builds a debug APK in GitHub Actions:

1. Open **Actions → Android build** and pick the latest run.
2. Download the **clearoo-debug-apk** artifact and unzip it.
3. Copy `app-debug.apk` to your phone and open it. You may need to allow "Install unknown apps".

Requires **Android 11+**.

## Build locally

```bash
./gradlew assembleDebug        # APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # streak and mood rules
```

You need JDK 17+ and the Android SDK (Android Studio sets this up).

## Code map

```
app/src/main/java/com/clearoo/app/
├── domain/      Pure Kotlin: streak rules, Roo's moods, Roo's lines (unit-tested)
├── data/        MediaStore queries and trash requests, DataStore for stats and settings
├── mascot/      Roo: drawn with android.graphics (shared by app, widget, notifications)
├── notify/      WorkManager daily reminder and notification
├── widget/      Glance home-screen widget
└── ui/          Jetpack Compose screens: onboarding, home, swipe deck, bin, celebration, settings
```

`domain/` has no Android dependencies, so it can move to Kotlin Multiplatform when we do iOS.

## Roadmap

- More Roo: animated widget states, outfits unlocked by streak milestones
- iOS via Compose Multiplatform, sharing the domain layer

## Credits

UI font: [Nunito](https://fonts.google.com/specimen/Nunito), SIL Open Font License 1.1.
