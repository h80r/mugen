# Proposal: Rebrand Deep Link Scheme to mugen

## Intent
The `rebrand-mugen-identity` change replaced the app's name, applicationId, icons, and About/Help screen branding, but deliberately left the `tadami://` deep link scheme untouched. That scheme is used both for in-app extension/repo deep links and, more importantly, as the registered OAuth redirect URI for six external trackers (Trakt, Simkl, Bangumi, Shikimori, TMDB, MyAnimeList). Renaming it isn't a pure code change — each tracker's developer dashboard must have its registered redirect URI updated too, or login breaks for that service. This change exists to carry that scope forward as its own unit of work, coordinated separately from the identity rebrand.

## Scope
- `android:scheme="tadami"` across the 4 intent filters in `AndroidManifest.xml` (`add-repo`, `extension-store`, `novel-extension-store`, `myanimelist-auth`) — the paired legacy `tachiyomi`/`aniyomi` scheme aliases stay untouched
- The 3 scheme checks in `MainActivity.kt`
- Hardcoded OAuth redirect URI constants in `Tmdb.kt`, `SimklApi.kt`, `BangumiApi.kt`, `Trakt.kt`, `ShikimoriApi.kt`, and the matching assertion in `TraktTrackerTest.kt`
- External coordination: registering the new redirect URI with each of the 6 trackers' OAuth app dashboards before or alongside the code change, so tracker login doesn't silently break
- Decision on whether the Trakt OAuth app itself gets re-registered under the mugen name (`Trakt.kt:48`), since its `CLIENT_ID`/`CLIENT_SECRET` are tied to the external "Tadami"-named registration and won't follow a source-level string rename
- **Out of scope**: any other identity elements — those are covered by `rebrand-mugen-identity`

## Approach
Prep-then-code, same pattern as the identity rebrand: a first task group where the owner decides the new scheme string, the cutover-vs-alias migration strategy, and completes the external dashboard registrations; only then do the AndroidManifest/MainActivity/tracker-constant edits ship, followed by manual verification of both deep links and OAuth logins (not easily covered by automated tests since dashboards are external).
