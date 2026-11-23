# Stashly — Video Downloader, File Manager & Download Manager (Android)

Kotlin · Jetpack Compose · Clean Architecture + MVVM · Hilt · Room · WorkManager · Media3 · Retrofit/OkHttp · DataStore · Coil

Stashly downloads media you own or are authorised to save, organises it automatically in a built-in file manager, and tracks every transfer in a download manager that survives restarts.

## What works
| Area | Implementation |
|---|---|
| **Home / Downloader** | URL box with live validation + source detection, Paste/Clear, opt-in clipboard suggestion, Analyze → preview card (thumbnail, title, source, duration, sizes) → real format list → Download with live progress, speed, ETA, pause/resume/cancel |
| **Providers** | `MediaSourceProvider` abstraction. Shipped: **Direct links** (any public https media file; one honest "Original" format) and **Internet Archive** (official metadata API; real formats/resolutions/bitrates). Restricted platforms (YouTube, Instagram, TikTok, Facebook, X, Netflix, Spotify …) are detected and offered *open officially / share link* — never downloaded |
| **Download engine** | Resumable HTTP (`Range` + `If-Range`), retry with back-off, ENOSPC/network separation, streams straight into a pending MediaStore entry (no second copy), post-download verification (size, magic bytes, media parse) before the file becomes visible |
| **Download Manager** | All / Downloading / Completed / Failed / Paused, search, pause/resume/cancel/retry/remove/open/share, "file missing" detection, duplicate prevention, WorkManager persistence, Wi-Fi-only |
| **File Manager** | Videos, Audio (play/pause per row), Images (grid + viewer), Documents (open with other apps), Downloads (chronological), Folders (create/rename/delete/browse/move/copy), rename, share, delete, search, sort, storage overview. One physical file, categories are queries — nothing is duplicated |
| **Settings** | Theme (system/light/dark), default format & quality, save folder, Wi-Fi only, notifications, clipboard suggestion, storage tools, clear history, privacy/terms, about, version, contact support |
| **Security** | https only (network security config + URL rules), no credentials in URLs, private/loopback hosts rejected, no https→http redirects, extension **allow-list** (executables, scripts, HTML, archives blocked), MIME/extension agreement, magic-byte check, no storage permission, no API keys, no cookies/passwords stored |

## Honest limitations
* Only direct links and Internet Archive are supported out of the box. Platforms that forbid third-party downloading are deliberately **not** supported. Add compliant integrations by implementing `MediaSourceProvider` (see `docs/ARCHITECTURE.md`).
* MP3 conversion is **not** offered: no source supplies it legitimately and transcoding isn't needed for formats sources already publish (MP3/M4A/OGG are listed when a source really has them).
* No HLS/DASH segment downloading yet (plain files only).
* `minSdk 29` (Android 10) so scoped storage/MediaStore works without any storage permission.
* Files created by a previous install are not visible after reinstall (Android scoped-storage rule).
* Audio plays only while the app is in the foreground (no background playback service — by design).

## Build
Requires JDK 17+ and the Android SDK (platform 36, build-tools 35+).
```bash
./gradlew :app:testDebugUnitTest      # 66 JVM tests
./gradlew :app:assembleDebug          # app/build/outputs/apk/debug
./gradlew :app:lintDebug
./gradlew :app:connectedDebugAndroidTest   # needs an emulator/device
./gradlew :app:bundleRelease          # app/build/outputs/bundle/release/app-release.aab
```
**Signing:** create `keystore.properties` (git-ignored) with `storeFile`, `storePassword`, `keyAlias`, `keyPassword` — or set `STASHLY_STORE_FILE/PASSWORD`, `STASHLY_KEY_ALIAS/PASSWORD`. Without them `bundleRelease` produces an **unsigned** bundle.

**Before publishing**, replace the placeholders: `SUPPORT_EMAIL` and `PRIVACY_POLICY_URL` in `app/build.gradle.kts`, the contact line in `app/src/main/res/raw/*.txt` and `store/*.md`, and the package id `com.stashly.app` if you want your own.

## Docs
`docs/ARCHITECTURE.md` · `docs/TESTING.md` · `docs/RELEASE_CHECKLIST.md` · `store/` (listing, Data Safety, permission justification, content rating, privacy policy, terms, graphics)
