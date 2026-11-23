# Data safety form — prepared answers
Verify against the final build and your hosting before submitting; you are responsible for the accuracy of the form.

| Question | Answer | Reason |
|---|---|---|
| Does the app collect or share user data? | **No data collected, no data shared** | History, settings and files stay on-device. No analytics/ads/crash SDK, no developer server. |
| Data encrypted in transit | **Yes** | https only (cleartext disabled in the network security config) |
| Way to request data deletion | Not applicable (no data held by the developer); users clear history in Settings or uninstall | |
| Third-party SDKs that collect data | None | Libraries used: AndroidX, Compose, Room, WorkManager, Media3, OkHttp/Retrofit, Coil, Hilt |

Note: when a user pastes a link, the app connects to the server they chose (and archive.org's public API). That traffic goes directly from the device to that third party and is user-initiated; it isn't collected by the developer. Re-check Google's current definition of "collection" for your case.
