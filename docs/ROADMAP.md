# Clearoo roadmap and future notes

## Now: launch on Google Play
- [ ] Play Console identity verification (pending)
- [ ] Trademark check for "Clearoo": the UK/EU/US registrations found are in classes 10, 11 and 15, none in software (9/42). Confirm there are no other class 9/42 results, and check the national registry.
- [ ] Add the upload-key secrets to GitHub, run the "Play Store release" workflow, and test `app-release.apk`
- [x] Support email: roolabs.support+clearoo@gmail.com (in `PRIVACY.md` and the listing)
- [ ] Screenshots; create the app; internal test → closed test (12 testers × 14 days) → production
- See [PLAY_STORE.md](PLAY_STORE.md)

## Next: features
### Free up more space
- [ ] **"Empty trash now"**: trashed items still use space for 30 days. Let users purge Clearoo's trashed items so "space freed" is literally true. *(Top priority.)*
- [ ] **"Pick the best" for look-alikes**: show a burst side by side, tap the keeper, and bin the rest in one go
- [ ] **Compress big videos** instead of deleting them (Media3 Transformer, on-device)
- [ ] **Exact duplicates** across folders (content hash)
- [ ] **More decks:** receipts and documents, memes and forwards, "on this day"
- [ ] **Storage breakdown** by app and folder

### Habit and fun
- [ ] **Streak freeze**, earned by hitting the goal several days in a row
- [ ] **Weekly recap card** ("You freed 2.3 GB this week 🦘"), shareable to Instagram and WhatsApp
- [ ] **Achievements:** first GB, 100-day streak, 1,000 cleared
- [ ] **More Roo:** seasonal outfits (Diwali, Halloween, New Year), animations, optional sounds
- [ ] **Friends leaderboard** (needs a server; later)

### Growth and money
- [ ] **Clearoo Plus:** one-time purchase or small subscription for extra decks, video compression and exclusive outfits. No ads, ever.
- [ ] **Languages:** Hindi first
- [ ] **iOS:** Apple Developer Program is US$99/year. Do it after Android proves out. CI can build on GitHub's macOS runners, so no Mac is needed.

### Polish
- [ ] **Accessibility:** TalkBack labels, button-only flow
- [ ] **Tablet layouts**

## Roo across apps
Roo is the shared character for all our apps and videos. See [roo/ROO.md](roo/ROO.md) for the character guide and ready-made PNGs.

- [ ] **Eddie the Editor: Roo as on-screen presenter**
  - Roo in a corner of every video, with a mood picked per scene to match the line ("shocked" on the hook, "proud" on the payoff)
  - Mouth flaps in sync with the narration, using Eddie's existing per-word timings
  - An end card that promotes Clearoo ("Clear your gallery with Roo: free on Google Play")
- [ ] Future apps reuse `MascotPainter` (or the PNG kit) so Roo stays identical everywhere
