# Delta for App Identity

## ADDED Requirements

### Requirement: Deep link scheme reflects mugen identity
The system SHALL register its custom deep link scheme under the new mugen scheme (replacing `tadami`) for extension/repo intent filters and tracker OAuth redirects, while continuing to accept the legacy `tachiyomi`/`aniyomi` aliases for backward compatibility.
Source: `app/src/main/AndroidManifest.xml`, `app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt`.

#### Scenario: Extension deep links resolve under the new scheme
- GIVEN a deep link using the new mugen scheme with host `add-repo`, `extension-store`, or `novel-extension-store`
- WHEN the app receives the intent
- THEN it routes to the same handler as it did for the equivalent `tadami://` link

#### Scenario: Legacy scheme aliases still resolve
- GIVEN a deep link using the legacy `tachiyomi://` or `aniyomi://` scheme
- WHEN the app receives the intent
- THEN it continues to route correctly, unaffected by the scheme rename

### Requirement: Tracker OAuth redirects use the new scheme
The system SHALL use the new mugen deep link scheme as the OAuth redirect URI for all trackers that authenticate via redirect (Trakt, Simkl, Bangumi, Shikimori, TMDB), matching the redirect URI registered on each tracker's external OAuth app dashboard.
Source: `Tmdb.kt`, `SimklApi.kt`, `BangumiApi.kt`, `Trakt.kt`, `ShikimoriApi.kt`.

#### Scenario: OAuth login completes for a redirect-based tracker
- GIVEN a user completes the OAuth authorization step for a tracker whose redirect URI uses the new scheme
- WHEN the tracker's service redirects back to the app
- THEN the app receives and handles the redirect intent, completing login
