# Testing

## Automated — JVM (`./gradlew :app:testDebugUnitTest`) — **66 tests, all passing**
| Suite | Covers |
|---|---|
| `UrlRulesTest` | https-only, scheme/credential/local-IP rejection, host matching, file names |
| `FileRulesTest` | allow-list, blocked executables/scripts/HTML, MIME-vs-extension mismatch, magic bytes, sanitising |
| `FormattersTest`, `FormatSelectorTest` | size/speed/duration/ETA text; default-quality selection never invents formats |
| `HttpDownloaderTest` (**download engine, network interruption, large file**) | full download + progress, Range/If-Range resume, server ignoring Range, 416-complete, **connection dropped mid-body → resumed**, retry limits, 404/403 no-retry, HTML veto, **disk full (ENOSPC)**, short body, **cancellation keeps partial**, **48 MiB file + resume with SHA-256 check** |
| `ProvidersTest` | Direct provider (HEAD/ranged GET, Content-Disposition, web page/executable rejection), Internet Archive (real formats only, private/thumbnail/metadata files excluded, restricted items rejected, errors), registry routing, restricted platforms incl. look-alike hosts, live hints |
| `HomeViewModelTest` | analyze → default selection, error phase, restricted → never enqueued, audio switch, duplicate handling, stale-result clearing, clipboard candidate |

## Automated — instrumented (`./gradlew :app:connectedDebugAndroidTest`) — **written and compiling; NOT yet executed**
The authoring environment had no emulator/KVM, so these have not been run. Run them on API 29, 34 and 36 devices before release.
* `DatabaseTest` (Room, in-memory): duplicate detection, pause/cancel never overwritten by late progress, completion, clear history, library filter/sort/search incl. LIKE-wildcard escaping, folder trees.
* `LibraryRepositoryTest` (real MediaStore, no permission): publish → indexed once & categorised, rename/move/copy/delete, rename can't create `.apk`, sync drops externally deleted files, invalid folder names, pending files invisible.
* `AppUiTest` (Compose + Hilt): 3-tab navigation, every Files category tab, Analyze gating, restricted-platform flow (offline), Settings + legal pages.

## Build verification (done)
`assembleDebug` ✔ · `lintDebug` — 0 errors (warnings: dependency-update hints only) · `bundleRelease` ✔ with R8 + resource shrinking, no missing-rules · all 8 bundled native libs are 16 KB aligned · release AAB is unsigned until you supply a key.

## Manual device test plan (required before release)
Devices: Android 10 (API 29), 13/14 (notification + FGS rules), 16 (API 36); a low-RAM device; phone + tablet + foldable widths; light/dark; font scale 1.3.
1. **Direct link:** paste an https MP4 you host → preview shows size → download → appears in *Videos* and *Downloads*, plays, shares, renames, moves into a new folder, deletes.
2. **Internet Archive:** paste an `archive.org/details/<id>` item (e.g. a public-domain film) → multiple real formats → download MP4 and MP3 → verify categorisation.
3. **Pause/resume/cancel** mid-download; kill the app while downloading → reopen: download continues/resumes; reboot device.
4. **Airplane mode** mid-download → resumes on reconnect; **Wi-Fi only** waits on mobile data.
5. **Low storage:** fill the device, start a large download → clear "not enough storage" error, no crash, no leftover files.
6. **Duplicate** same format twice → blocked with message. Delete a completed file in another app → Downloads shows "File was moved or deleted" + *Download again*.
7. **Restricted link** (e.g. a YouTube URL) → compliant card, nothing fetched. **Bad inputs:** http link, `file://`, localhost, HTML page URL, `.apk` URL → clear errors.
8. **Notifications:** permission prompt on Android 13+, progress and completion tap opens Downloads; denied permission → downloads still run.
9. Rotation/split-screen during download and playback; TalkBack pass on all tabs.
10. Large file (≥2 GB) on a slow network; verify memory stays flat and battery use is reasonable.
