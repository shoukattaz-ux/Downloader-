# Architecture

## Layers (Clean Architecture + MVVM, single module, package-by-layer)
```
ui/            Compose screens + ViewModels (Home, Downloads, Files, Settings, Player, nav)
domain/        models, MediaSourceProvider, ProviderRegistry, FormatSelector, restricted-platform rules
data/          Room (AppDatabase, DAOs), repositories (Download, Library, Settings/DataStore)
download/      HttpDownloader (pure JVM), DownloadWorker (WorkManager), MediaStoreSink, notifier, scheduler
core/          AppError/ErrorCode, UrlRules, FileRules (allow-list + magic bytes), Formatters
di/            Hilt module (OkHttp, Retrofit, Room, providers)
```
Dependencies point inward: `ui → data/domain`, `download → data/core`. `core`, `domain` and `HttpDownloader` have **no Android dependencies**, which is why they are covered by fast JVM tests.

## Key flows
**Analyze:** `UrlRules.parse` → `RestrictedPlatforms.detect` (compliant alternative, no network call) → first matching `MediaSourceProvider.resolve` → `MediaInfo` with real formats → `FormatSelector` applies the user's defaults to *existing* formats only.

**Download:** `DownloadRepository.enqueue` (duplicate check, filename sanitising, allow-list re-check) → Room row `QUEUED` → unique WorkManager request (`download-<id>`, network or unmetered constraint, expedited with fallback) → `DownloadWorker`:
1. create/reuse a *pending* MediaStore entry under `Download/Stashly[/folder]`
2. `HttpDownloader` streams into it (Range resume, retries, progress throttled to 500 ms → Room → UI Flow)
3. verify: size, magic bytes, `MediaMetadataRetriever` parse for audio/video
4. `IS_PENDING=0`, index in Room, mark `COMPLETED`, notify

**State integrity:** progress/failed writes are conditional on the current status, so a user *pause/cancel* is never overwritten by a late worker write. `reconcile()` on app start re-arms `QUEUED/DOWNLOADING` rows (KEEP policy) and re-syncs the library.

**Files:** MediaStore (`Download/Stashly/**`) is the source of truth; Room `library_files` is a searchable, indexed mirror (`sync()` adds new files, removes vanished ones, lazily probes durations). Folders are path prefixes (`RELATIVE_PATH`); empty folders live in `library_folders`. Move = update `RELATIVE_PATH`; rename = update `DISPLAY_NAME`; copy = stream to a new pending entry. No storage permission is needed because the app only touches files it created.

## Adding a source
Implement `MediaSourceProvider` (`supports(url)` offline, `resolve(url)` returning real formats, throwing `AppError` for failures) and add it to `AppModule.providers` *before* `DirectUrlProvider`. Use only official APIs or explicit authorisation. Never bypass DRM, sign-in or platform restrictions.

## Phase map
1 analysis → this document · 2 architecture → above · 3 design system/navigation → `ui/theme`, `ui/components`, `ui/nav` · 4 Home → `ui/home` · 5 engine/providers → `domain/provider`, `download` · 6 Download Manager → `ui/downloads` · 7 File Manager → `ui/files`, `LibraryRepository` · 8 Room/MediaStore/WorkManager → `data`, `download` · 9 settings/security/errors → `ui/settings`, `core` · 10 QA → `docs/TESTING.md` · 11 release → `store/`, `docs/RELEASE_CHECKLIST.md`.
