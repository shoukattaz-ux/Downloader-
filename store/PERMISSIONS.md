# Permission justification
| Permission | Why | User-visible feature |
|---|---|---|
| `INTERNET` | Fetch link metadata and download files | Analyze / Download |
| `ACCESS_NETWORK_STATE` | WorkManager network constraints (Wi-Fi-only, resume on reconnect) | Wi-Fi-only, auto-resume |
| `POST_NOTIFICATIONS` (13+) | Progress / completion / failure notifications; runtime-requested on first download; app works if denied | Download notifications |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` | WorkManager runs long user-initiated downloads as a foreground task with a visible progress notification so Android doesn't kill them | Reliable large downloads |

Not requested: `READ/WRITE_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`, `READ_MEDIA_*`, camera, mic, location, contacts. Files are written with MediaStore and only app-created files are accessed.

**Play Console foreground-service declaration (type: Data sync):** "User-initiated file downloads that continue while the app is in the background; notification shows progress and the user can stop the transfer in the app." Provide a short screen recording: start download → leave app → notification → completion.
