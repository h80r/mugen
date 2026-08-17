# History Specification

## Requirements

### Requirement: Per-media reading/watching history
The system SHALL track history of read chapters, watched episodes, and read novel chapters, with anime and manga each backed by a full interactor set (Get/GetNext/Upsert/Remove) that novel does not fully mirror.
Source: `domain/.../history/{anime,manga,novel}/`.

#### Scenario: Anime and manga have symmetric history interactors
- GIVEN a developer looks for `UpsertAnimeHistory` and `UpsertMangaHistory`
- WHEN they check the domain interactor layer
- THEN both exist with matching Get/GetNext/Upsert/Remove interactor sets

#### Scenario: Novel history lacks a dedicated interactor layer
- GIVEN a developer looks for `UpsertNovelHistory` or `RemoveNovelHistory`
- WHEN they check `domain/.../history/novel/`
- THEN no such interactor files exist — only `GetTotalNovelReadDuration` is present; novel history writes go directly through `NovelProgressPersistenceController`/`NovelReaderScreenModel` against `NovelHistoryRepository`, bypassing the interactor layer used by anime and manga
