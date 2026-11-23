# Google Play release checklist
Nothing here guarantees approval; Play review is at Google's discretion.

## Build & signing
- [ ] Replace placeholders: `SUPPORT_EMAIL`, `PRIVACY_POLICY_URL` (`app/build.gradle.kts`), contact lines in `store/*.md` and `app/src/main/res/raw/*.txt`
- [ ] Decide final package id (`com.stashly.app`) **before** first upload — it can't change later
- [ ] Create an upload keystore (`keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000`), keep it out of git, back it up; enrol in **Play App Signing**
- [ ] `keystore.properties` or `STASHLY_*` env vars → `./gradlew :app:bundleRelease` → verify with `jarsigner -verify`
- [ ] `targetSdk 36` ✔ (Play's current requirement; re-check the policy date when you ship) · 16 KB native alignment ✔
- [ ] Bump `versionCode` for every upload

## Quality gates
- [ ] `testDebugUnitTest` green · `lintDebug` 0 errors · instrumented tests green on API 29/34/36
- [ ] Complete the manual plan in `docs/TESTING.md`; test the **release** build (R8) not just debug — especially Analyze (Retrofit/serialization), downloads, Files
- [ ] Pre-launch report in Play Console reviewed; closed/internal testing track used first (new personal accounts need the required closed-test period)

## Console forms
- [ ] **App content → Privacy policy URL** (host `store/PRIVACY_POLICY.md` on a public page)
- [ ] **Data safety** → `store/DATA_SAFETY.md`
- [ ] **Foreground service permissions** → declare `dataSync`, use `store/PERMISSIONS.md` justification + short video of a download with its notification
- [ ] **Content rating questionnaire** → `store/LISTING.md` (answers prepared)
- [ ] Target audience: 13+/general audience, not a children's app; Ads: none
- [ ] **Store listing** → text from `store/LISTING.md`; icon `store/graphics/icon-512.png`; feature graphic `store/graphics/feature-graphic-1024x500.png`
- [ ] **Screenshots (min. 2, phone; add 7"/10" tablet):** capture from a real/emulated device with your own *licensed or public-domain* sample content — not produced here because no device was available
- [ ] Notification permission (Android 13+) and no broad storage permissions — nothing to declare

## Policy self-review (the high-risk area for this app category)
- [ ] Listing never names YouTube/Instagram/TikTok/etc. as supported, and never implies downloading from them (Play's *Deceptive Behavior* and *Intellectual Property* policies)
- [ ] Listing text and screenshots show only user-owned/public-domain/licensed content
- [ ] Be ready to respond to a rights-holder complaint (support address monitored)
- [ ] If you add providers: confirm each platform's terms/API allow downloading; keep `RestrictedPlatforms` current
