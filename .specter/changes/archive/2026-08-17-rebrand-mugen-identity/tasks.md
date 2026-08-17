# Tasks

## 1. Provide mugen brand assets
**Branch:** none (user-fulfilled, no code changes)
- [x] 1.1 Provide adaptive launcher icon source (foreground + background layers, plus monochrome variant for themed icons) — vector or ≥432×432 PNG — provided in `assets/android/res/mipmap-*/ic_launcher_{foreground,background,monochrome}.png` (mdpi→xxxhdpi, up to 432×432) + `assets/android/res/mipmap-anydpi-v26/ic_launcher.xml`
- [x] 1.2 Provide fallback/legacy launcher icon (single flat PNG, ≥192×192, for pre-adaptive-icon devices) — provided as per-density `ic_launcher.png` set in `assets/android/res/mipmap-*/` (up to 192×192 at xxxhdpi)
- [x] 1.3 Provide Play Store listing icon (512×512 PNG) — provided at `assets/android/play_store_512.png`
- [x] 1.4 Provide splash logo (square PNG, ~1024×1024 to match current asset; transparent background recommended since it sits on `@color/splash`) — provided at `assets/ic_splash_logo.png` (1024×1024, real alpha transparency confirmed); corrected during device testing (7.2) — original version clipped on-device, replaced with a fixed 1024×1024 render, applied to both `assets/ic_splash_logo.png` and `app/src/main/res/drawable-nodpi/ic_splash_logo.png`
- [x] 1.5 Confirm final "mugen" capitalization for any surface that might warrant it differently (default: lowercase everywhere) — confirmed: lowercase "mugen" everywhere
- [x] 1.6 Decide easter egg replacement copy: new "mugen Hall of Fame" title text (RU + EN) to replace the Tadami-branded strings in `SecretHallSceneConfig.kt` — confirmed: simple "Tadami"→"mugen" swap, rest of copy unchanged:
  - RU: `title = "Зал славы mugen"`, `rosterTitle = "Все участники mugen"`
  - EN: `titleEn = "mugen Hall of Fame"`, `rosterTitleEn = "All mugen Participants"`

## 2. Rename package identity
- [x] 2.1 Update `applicationId`/`namespace` in `app/build.gradle.kts` to `dev.h80r.mugen` (preserve existing debug/localdev/benchmark suffixes)
- [x] 2.2 Update `namespace` in `domain/build.gradle.kts` and `data/build.gradle.kts` to `dev.h80r.mugen.domain` / `dev.h80r.mugen.data`
- [x] 2.3 Update `rootProject.name` in `settings.gradle.kts` to `mugen`
- [x] 2.4 Update `CLAUDE.md` title and applicationId references — n/a: `CLAUDE.md` does not exist in this repo (deleted in commit `d5bd8949d`, prior to this change); nothing to update
- [x] 2.5 (discovered) Fix all Kotlin source references to the old generated `com.tadami.aurora.{BuildConfig,R,databinding.*}` classes, now broken by the 2.1 namespace rename — bulk-updated ~130 files across `app/` to import from `dev.h80r.mugen.*` instead; also fixed two hardcoded `TARGET_PACKAGE` benchmark constants (`macrobenchmark/`) to `dev.h80r.mugen.benchmark`. Left the Russian flavor-text comment in `AuroraShaders.kt` and the arbitrary test-fixture package strings in `CloudflareWebViewHeadersTest.kt`/`CloudflareChallengeResolverHeaderSanitizerTest.kt` untouched — cosmetic only, matches the proposal's out-of-scope carve-out for internal references with no user/wire-format impact. Verified with `./gradlew :app:compileDebugKotlin`

## 3. Update app name strings
- [x] 3.1 Update `app_name` in `i18n/src/commonMain/moko-resources/{base,ar,id,ru}/strings.xml` to "mugen"
- [x] 3.2 Update `app_name` in `i18n-aniyomi/.../{base,ar}/strings.xml` to "mugen" — actual key is `app_name_snake` (used for cloud sync); updated to `mugen`, also added `translatable="false"` to the `ar` entry for consistency

## 4. Replace icon and splash assets
- [x] 4.1 Replace adaptive launcher icon layers (`app/src/main/res/drawable/ic_launcher_background.xml`, `app/src/main/res/drawable-nodpi/ic_launcher_foreground.png`, monochrome layer in `mipmap-anydpi-v33/ic_launcher*.xml`) — actual structure differed from assumption: background was a placeholder vector (`drawable/ic_launcher_background.xml`), foreground a single nodpi PNG, monochrome a separate vector (`drawable/ic_ani_monochrome_launcher.xml`). Replaced all three with the provided per-density PNG set (`mipmap-{mdpi..xxxhdpi}/ic_launcher_{foreground,background,monochrome}.png`) and repointed `mipmap-anydpi-v33/ic_launcher.xml` + `ic_launcher_round.xml` to `@mipmap/ic_launcher_{foreground,background,monochrome}`; deleted the superseded vector/PNG sources
- [x] 4.2 Replace fallback launcher icon (`app/src/main/res/mipmap/ic_launcher.xml` + `ic_launcher_round.xml`) — repointed both to `@mipmap/ic_launcher_{foreground,background}`; added flat per-density `ic_launcher.png` (legacy pre-adaptive-icon fallback) and `ic_launcher_round.png` (reused the same square PNG — no distinct round mask was provided, matching the pattern Android Studio's icon wizard uses when no round-specific asset exists)
- [x] 4.3 Replace debug source set launcher icon assets (`app/src/debug/res/...`) — repointed `ic_launcher.xml`/`ic_launcher_round.xml` to `@mipmap/ic_launcher_{foreground,background,monochrome}` (inherited from main's per-density PNGs via source-set merge); deleted the now-unused hand-drawn vector foreground
- [x] 4.4 Replace Play Store source icon (`app/src/debug/ic_launcher-playstore.png`)
- [x] 4.5 Replace splash logo (`app/src/main/res/drawable-nodpi/ic_splash_logo.png`)

## 5. Rework About/Help screen credits and easter egg
- [x] 5.1 `AboutScreen.kt`: rename the "Tadami" section to "mugen" — update title/label, GitHub URL to `https://github.com/h80r/mugen`, remove the `TelegramChannel`/`TelegramGroup` entries from this section
- [x] 5.2 `AboutScreen.kt`: remove the "Aniyomi" section entirely
- [x] 5.3 `AboutScreen.kt`: add a new "Tadami" upstream-credit section (GitHub link `https://github.com/andarcanum/Tadami-Aniyomi-fork`) positioned where the Aniyomi section was
- [x] 5.4 `AboutScreen.kt`: remove now-unused `AboutFooterLinkLabel`/`AboutFooterLinkIcon` enum cases and icon mappings for the dropped Telegram entries (keep `Tadami` case, now used by the new upstream section) — also removed now-orphaned imports (`Icons`, `Icons.Outlined.Public`, `Icons.AutoMirrored.Outlined.{Chat,Send}`, `CustomIcons`'s `Discord` extension); verified with `./gradlew :app:compileDebugKotlin`
- [x] 5.5 `HelpScreen.kt`: remove the two Telegram links and update the GitHub issues URL to the new repo — issues URL now `https://github.com/h80r/mugen/issues`; removed now-orphaned `Icons`/`Chat`/`Send` imports
- [x] 5.6 `SecretHallSceneConfig.kt`: rename "Tadami Hall of Fame" title/rosterTitle strings (RU + EN) to mugen, using copy confirmed in 1.6 — updated `app/src/main/java/eu/kanade/presentation/browse/local/SecretHallSceneConfig.kt` (the file with the actual literal strings; the sibling `browse/SecretHallSceneConfig.kt` only holds the data class definitions, no literals to change); verified with `./gradlew :app:compileDebugKotlin`

## 6. Backup signature, update checker, issue templates, tracker UAs
- [x] 6.1 Rename `TadamiSisterManifest.SIGNATURE` from `"TADAMI_SISTER"` to `"MUGEN_SISTER"` (`TadamiSisterManifest.kt`)
- [x] 6.2 Update `AppUpdateChecker.kt` `GITHUB_REPO` constant to `"h80r/mugen"`
- [x] 6.3 Update `.github/ISSUE_TEMPLATE/config.yml`: remove the three Telegram contact-link entries
- [x] 6.4 Update `.github/ISSUE_TEMPLATE/report_issue.yml` and `request_feature.yml`: update repo name references and the "latest release" link to `github.com/h80r/mugen` — also updated the templates' user-facing "Tadami" app-name prose to "mugen" for consistency with the task-3 display-name rename (description, field label/id, checklist copy); validated YAML syntax
- [x] 6.5 Update tracker User-Agent header strings from "Tadami" to "mugen" across the 9 interceptor files (`AnilistInterceptor.kt`, `MyAnimeListInterceptor.kt`, `SimklInterceptor.kt`, `KavitaInterceptor.kt`, `JellyfinInterceptor.kt`, `KitsuInterceptor.kt`, `ShikimoriInterceptor.kt`, `KomgaApi.kt`, `TraktApi.kt`) — all followed the identical `"Tadami v${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})"` pattern; bulk-replaced (including the commented-out line in `MyAnimeListInterceptor.kt`); verified with `./gradlew :app:compileDebugKotlin`
- [x] 6.6 Verify and update the hardcoded repo URL in `BangumiInterceptor.kt`'s User-Agent string — found a stale `"jmir1/Tadami/v... (https://github.com/Tadami-app/Tadami)"` (leftover from an earlier upstream fork chain, didn't match either the Tadami source repo or mugen); updated to `"h80r/mugen/v${BuildConfig.VERSION_NAME} (Android) (https://github.com/h80r/mugen)"` following Bangumi's `owner/repo/version` User-Agent convention; verified with `./gradlew :app:compileDebugKotlin`

## 7. Verification
- [x] 7.1 Build the app (`./gradlew assembleDebug` or equivalent) and confirm the applicationId/namespace rename and resource changes compile and package correctly — `./gradlew clean assembleDebug` succeeded for all ABIs; merged manifest confirms `package="dev.h80r.mugen.localdev"` (note: `clean` was required first — stale `build/` dirs from before the `rootProject.name` rename caused a `.kotlin_module` path collision against the repo's old `tadami` directory name)
- [x] 7.2 Launch the app; confirm launcher icon (home screen + app switcher), splash screen logo on cold start, and app name in system UI — confirmed on device (Pixel 6); splash logo needed one correction (clipping, see 1.4)
- [x] 7.3 Navigate to More → About: confirm mugen section (correct GitHub link, no Telegram entries) and Tadami upstream section (correct GitHub link) render correctly, and no Aniyomi section remains — confirmed on device; also surfaced an out-of-scope find: `LogoHeader.kt` (the wordmark logo rendered above the footer sections) was a hand-drawn `ImageVector` spelling out "tadami" + a "タダ見" badge, not covered by any task. Regenerated it from the actual Yomogi/Zen_Loop font files (task 1's design assets) via `fontTools` glyph-outline extraction: "mugen" wordmark (`onSurfaceColor`, theme-adaptive) + a vertical pill badge (536DFE→6A3DE8 gradient) with "無/限" stacked in white. Hit and fixed a path-parsing bug along the way (SVG `H`/`V` commands were silently dropped, corrupting multi-subpath glyphs like 無); confirmed correct on device after the fix.
- [x] 7.4 Navigate to Help screen: confirm links match — confirmed on device
- [x] 7.5 Trigger the Hall of Fame easter egg: confirm renamed strings display in both locales — confirmed on device, mugen strings display correctly in RU and EN
- [x] 7.6 Create a backup and restore it in the same build; confirm the renamed signature round-trips and anime/novel sections route correctly — confirmed on device, `MUGEN_SISTER` signature round-trips and anime/novel sections route correctly after restore
