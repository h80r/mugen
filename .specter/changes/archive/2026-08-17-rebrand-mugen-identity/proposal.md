# Proposal: Rebrand App Identity to mugen

## Intent
The app is currently branded "Tadami" (applicationId `com.tadami.aurora`), a fork of Aniyomi/Mihon. The owner wants to fully replace the app's identity with a new "mugen" identity (site: h80r.dev) — name, applicationId, launcher icon, in-app splash logo, and the About/Help screens' credit structure — while keeping a clear upstream credit to Tadami, the codebase mugen is built from.

## Scope
- App display name → **mugen** (all lowercase) across all i18n locale string files
- applicationId/namespace → **dev.h80r.mugen** (build.gradle.kts files in app/domain/data, rootProject.name)
- Launcher icon (adaptive + fallback + Play Store source, main and debug source sets) → user-provided mugen assets
- In-app splash logo (`ic_splash_logo.png`) → user-provided mugen asset
- About screen: rename the existing "Tadami" section to "mugen" (new GitHub link, Telegram links removed); delete the "Aniyomi" upstream section outright; add a new "Tadami" upstream-credit section in its place
- Help screen: same Telegram/GitHub link updates as About
- "Tadami Hall of Fame" easter egg (`SecretHallSceneConfig.kt`) renamed to mugen
- Backup wire signature constant (`TADAMI_SISTER` → `MUGEN_SISTER`) — confirmed safe to rename, no existing backups to preserve compatibility with
- In-app update checker's GitHub repo target, GitHub issue templates (Telegram links removed, repo references updated), tracker User-Agent header strings
- **Out of scope**: the `tadami://` deep link scheme and its OAuth redirect URIs (Trakt, Simkl, Bangumi, Shikimori, MAL) — this touches external OAuth app registrations on each tracker's dashboard and is materially more involved than a string rename. Tracked as a separate future change. Internal class/enum names and doc comments that reference "Tadami" with zero user or wire-format impact are left as-is (cosmetic only).

## Approach
Straightforward asset substitution and string/identifier renames across build config, resources, and a handful of Kotlin source files, split into a user-fulfilled asset-gathering group followed by implementation groups ordered so each is independently buildable/testable (package rename → strings → icons/splash → screens/easter-egg → backup signature/misc cleanup).

## Asset Design Notes
Source design for the launcher icon / splash logo (task 1), provided by the user:
- Glyph "無限∞" — font: Yomogi — gradient 536DFE → 6A3DE8, 315°
- Wordmark "mugen" — font: Zen Loop
