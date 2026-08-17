# Content Sources & Extensions Specification

## Requirements

### Requirement: APK-based extension model (anime/manga)
The system SHALL install anime and manga extensions as signed APKs, verifying signature trust on install and auto-carrying trust across updates when the signing key is unchanged.
Source: `AnimeExtensionManager.installExtension/updateExtension/uninstallExtension/trust`, `carryTrustToNewVersion`.

#### Scenario: Untrusted extension is flagged, not silently blocked
- GIVEN an extension's signature does not match a previously trusted signature
- WHEN it is installed
- THEN it appears in `untrustedExtensionsFlow` rather than being silently rejected, requiring explicit user trust

#### Scenario: Trust carries across same-key updates
- GIVEN a trusted extension is updated
- WHEN the new APK is signed with the same key as the previous version
- THEN trust is automatically carried over without re-prompting the user

### Requirement: Dual-runtime novel plugin model
The system SHALL support novel sources as "plugins" using either a JS runtime (Legado-style) or a Kotlin APK extension, both normalized behind a common plugin source factory, with checksum-based (not signature-based) trust verification.
Source: `app/.../extension/novel/`, `NovelPlugin` sealed class, `NovelJsRuntime`, `KotlinNovelExtensionSupport`, `NovelPluginSourceFactory`, `NovelPluginChecksum`.

#### Scenario: JS plugin runs through the custom JS engine
- GIVEN a novel plugin is JS-based (not `isKotlinExtension`)
- WHEN it fetches content
- THEN it executes through `NovelJsRuntime`/`NovelJsRuntimeBinder` with DOM shimming (`NovelJsDomStore`), not the standard Android APK extension loader

#### Scenario: Novel plugin trust uses checksum, not APK signature
- GIVEN a novel plugin is installed
- WHEN its trust is verified
- THEN a SHA-256 checksum (`NovelPluginChecksum`) is checked, unlike anime/manga extensions which rely on APK signature verification

#### Scenario: Both plugin types share one management interface
- GIVEN one novel plugin is JS-based and another is a Kotlin APK extension
- WHEN a user installs, uninstalls, or trusts either
- THEN both go through the same `NovelExtensionManager` interface (`installPlugin/uninstallPlugin/trustPlugin`) despite the different runtimes underneath

### Requirement: Independent per-media repo lists
The system SHALL maintain separate extension/plugin repository lists for anime, manga, and novel sources.

#### Scenario: Adding a manga repo does not affect anime or novel
- GIVEN a user adds a custom repository under manga extension settings
- WHEN they check anime and novel extension settings
- THEN the new repository does not appear in either — repo lists are fully independent per media type

### Requirement: Novel fallback content extraction
The system SHALL provide a generic readability-style content extraction fallback (OmniResolver) for novel sources, usable when no dedicated plugin covers a site or when cleanup rules are needed — with no anime/manga equivalent.
Source: `domain/.../source/novel/resolver/OmniResolverEngine`, `OmniRule`, `ContentPurifier`.

#### Scenario: OmniResolver has no anime/manga counterpart
- GIVEN a developer searches for an equivalent fallback resolver under anime or manga source packages
- WHEN they inspect `domain/.../source/{anime,manga}/`
- THEN no such generic extraction/purification system exists — it is novel-specific

### Requirement: Source pinning
The system SHALL let users pin favorite sources to the top of the Browse screen via a shared bitflag (`Unpinned`/`Pinned`/`Actual`).

#### Scenario: Pinned source sorts to top of Browse
- GIVEN a source is pinned
- WHEN the Browse screen is displayed
- THEN it appears above unpinned sources
