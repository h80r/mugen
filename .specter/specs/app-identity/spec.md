# App Identity Specification

## Requirements

### Requirement: App display name
The system SHALL present "mugen" (all lowercase) as the app's display name everywhere the app name is shown to the user (launcher label, system UI, About/Help screens), sourced from the `app_name` string resource across all locales.
Source: `i18n/src/commonMain/moko-resources/*/strings.xml`, `i18n-aniyomi/.../strings.xml`, `AndroidManifest.xml` (`android:label`).

#### Scenario: App name is consistent across locales
- GIVEN any supported locale (base, ar, id, ru)
- WHEN the app name string resource is resolved
- THEN it renders as "mugen"

### Requirement: applicationId identity
The system SHALL build under the applicationId/namespace `dev.h80r.mugen` (with existing debug/localdev/benchmark variant suffixes preserved) across the app, domain, and data modules.
Source: `app/build.gradle.kts`, `domain/build.gradle.kts`, `data/build.gradle.kts`, `settings.gradle.kts`.

#### Scenario: Debug and release variants share the mugen base id
- GIVEN a debug build variant
- WHEN its applicationId is resolved
- THEN it is `dev.h80r.mugen` plus the existing debug suffix, not the old `com.tadami.aurora` base

### Requirement: Launcher icon and splash logo
The system SHALL use mugen-branded artwork for the adaptive launcher icon (foreground/background/monochrome layers), the fallback launcher icon, the Play Store listing icon, and the Android 12+ splash screen icon.
Source: `app/src/main/res/mipmap*/ic_launcher*`, `app/src/debug/ic_launcher-playstore.png`, `app/src/main/res/drawable-nodpi/ic_splash_logo.png`.

#### Scenario: Cold launch shows the mugen splash logo
- GIVEN a cold app launch on Android 12+
- WHEN the splash screen renders
- THEN it displays the mugen splash artwork via `windowSplashScreenAnimatedIcon`, not the previous Tadami artwork

### Requirement: About screen credit structure
The system SHALL show exactly two credit sections on the About screen, in order: a "mugen" section (project identity, GitHub link to `github.com/h80r/mugen`, no Telegram links) followed by a "Tadami" upstream-credit section (GitHub link to `github.com/andarcanum/Tadami-Aniyomi-fork`). No separate Aniyomi section is shown. The screen's wordmark header renders "mugen" (theme-adaptive color) beside a gradient badge containing the "無限" glyph.
Source: `app/src/main/java/eu/kanade/presentation/more/settings/screen/about/AboutScreen.kt`, `app/src/main/java/eu/kanade/presentation/more/LogoHeader.kt`.

#### Scenario: mugen section has no Telegram links
- GIVEN the About screen renders its footer link sections
- WHEN the mugen section is displayed
- THEN it shows only the GitHub entry, with no Telegram channel or group entries

#### Scenario: Tadami section is the sole upstream credit
- GIVEN the About screen renders its footer link sections
- WHEN the sections below mugen are displayed
- THEN a "Tadami" section links to `github.com/andarcanum/Tadami-Aniyomi-fork`, and no "Aniyomi" section exists

### Requirement: Help screen links match About screen branding
The system SHALL present the same mugen-branded GitHub link (no Telegram links) on the Help screen as on the About screen's mugen section.
Source: `app/src/main/java/eu/kanade/presentation/more/settings/screen/HelpScreen.kt`.

#### Scenario: Help screen has no Telegram links
- GIVEN the Help screen renders its support links
- WHEN the link list is displayed
- THEN it contains no `t.me/TadamiApp` or `t.me/TadamiSupport` entries

### Requirement: Backup wire signature reflects mugen identity
The system SHALL write and recognize `"MUGEN_SISTER"` as the sister-app manifest signature embedded in backup files (protobuf field 20000), replacing the previous `"TADAMI_SISTER"` value.
Source: `app/src/main/java/eu/kanade/tachiyomi/data/backup/models/TadamiSisterManifest.kt`.

#### Scenario: A backup written by this build round-trips on restore
- GIVEN a backup created by the current build
- WHEN that backup is restored by the same build
- THEN the sister manifest signature is recognized and anime/novel sections route correctly

### Requirement: In-app update checker targets the mugen repository
The system SHALL check `h80r/mugen` (not the legacy Tadami fork repo) for update availability.
Source: `app/src/main/java/eu/kanade/tachiyomi/data/updater/AppUpdateChecker.kt`.

#### Scenario: Update check queries the new repo
- GIVEN the app performs an update check
- WHEN it queries GitHub for the latest release
- THEN it queries the `h80r/mugen` repository

### Requirement: Hall of Fame easter egg reflects mugen identity
The system SHALL present the "Hall of Fame" easter egg screen under the mugen name (both English and Russian display strings), not the Tadami name.
Source: `app/src/main/java/eu/kanade/presentation/browse/local/SecretHallSceneConfig.kt`.

#### Scenario: Easter egg title is locale-consistent
- GIVEN the Hall of Fame easter egg is triggered
- WHEN its title and roster title render in either English or Russian
- THEN they reference mugen, not Tadami

### Requirement: Tracker User-Agent headers reflect mugen identity
The system SHALL send `mugen`-branded User-Agent header strings when calling tracker/library APIs (AniList, MyAnimeList, Simkl, Kavita, Jellyfin, Kitsu, Shikimori, Komga, Trakt, Bangumi), replacing the previous Tadami-branded values.
Source: `app/src/main/java/eu/kanade/tachiyomi/data/track/*/`.

#### Scenario: Tracker requests carry the mugen User-Agent
- GIVEN the app makes a request to a tracker API
- WHEN the `User-Agent` header is constructed
- THEN it includes "mugen" rather than "Tadami"
