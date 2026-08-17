# Tasks

## 1. Decide and prepare
**Branch:** none (user-fulfilled, no code changes)
- [ ] 1.1 Decide the new scheme string (e.g. `mugen://`)
- [ ] 1.2 Decide migration strategy: cut over immediately (old `tadami://` stops working) vs. keep `tadami://` as a compat alias alongside the new scheme, the same way `tachiyomi`/`aniyomi` are kept today
- [ ] 1.3 Register the new redirect URI with Trakt's OAuth app dashboard
- [ ] 1.4 Register the new redirect URI with Simkl's OAuth app dashboard
- [ ] 1.5 Register the new redirect URI with Bangumi's OAuth app dashboard
- [ ] 1.6 Register the new redirect URI with Shikimori's OAuth app dashboard
- [ ] 1.7 Register the new redirect URI with TMDB's OAuth app dashboard
- [ ] 1.8 Register the new redirect URI with MyAnimeList's OAuth app dashboard
- [ ] 1.9 Decide whether to re-register the Trakt OAuth app under the mugen name (`Trakt.kt:48` comment), or leave the existing "Tadami"-named registration as-is

## 2. Update AndroidManifest and MainActivity
- [ ] 2.1 Add/replace the `tadami` scheme with the new scheme across all 4 intent filters in `AndroidManifest.xml` (`add-repo`, `extension-store`, `novel-extension-store`, `myanimelist-auth`), per the migration strategy decided in 1.2
- [ ] 2.2 Update the 3 scheme checks in `MainActivity.kt` to match

## 3. Update tracker OAuth redirect URIs
- [ ] 3.1 Update `Tmdb.kt` `REDIRECT_URI`
- [ ] 3.2 Update `SimklApi.kt` `REDIRECT_URL`
- [ ] 3.3 Update `BangumiApi.kt` `REDIRECT_URL`
- [ ] 3.4 Update `Trakt.kt` `REDIRECT_URI` (and its registration comment if re-registered per 1.9)
- [ ] 3.5 Update `ShikimoriApi.kt` `REDIRECT_URL`
- [ ] 3.6 Update `TraktTrackerTest.kt` assertion to match the new redirect URI

## 4. Verification
- [ ] 4.1 Build and install the app; trigger each of the 4 deep link intent filters manually (`adb shell am start -W -a android.intent.action.VIEW -d "<scheme>://<host>"`) and confirm they route correctly
- [ ] 4.2 Complete an OAuth login flow for each of the 6 trackers (Trakt, Simkl, Bangumi, Shikimori, TMDB, MAL) end-to-end and confirm the redirect completes successfully
- [ ] 4.3 If the compat-alias strategy was chosen in 1.2, confirm the legacy `tadami://` scheme still resolves correctly alongside the new one
