package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.models.Backup

/**
 * Where a backup file came from, as detected by [BackupDecoder].
 */
enum class BackupOrigin {
    TADAMI,
    MIHON,
    LEGACY_ANIYOMI,
}

/**
 * A decoded backup together with its detected origin.
 */
data class DecodedBackup(
    val backup: Backup,
    val origin: BackupOrigin,
)

/**
 * Human-oriented summary of what a backup file contains, used to preview the
 * contents before restoring. Pure function over the decoded model so it can be
 * unit tested without Android dependencies.
 */
data class BackupInspection(
    val origin: BackupOrigin,
    val mangaCount: Int,
    val animeCount: Int,
    val novelCount: Int,
    val categoriesCount: Int,
    val hasAppSettings: Boolean,
    val hasSourceSettings: Boolean,
    val hasExtensions: Boolean,
    val hasAchievements: Boolean,
    val hasExtensionRepos: Boolean,
) {

    val isEmpty: Boolean
        get() = mangaCount == 0 &&
            animeCount == 0 &&
            novelCount == 0 &&
            categoriesCount == 0 &&
            !hasAppSettings &&
            !hasSourceSettings &&
            !hasExtensions &&
            !hasAchievements &&
            !hasExtensionRepos

    companion object {
        fun of(decoded: DecodedBackup): BackupInspection = of(decoded.backup, decoded.origin)

        fun of(backup: Backup, origin: BackupOrigin): BackupInspection {
            return BackupInspection(
                origin = origin,
                mangaCount = backup.backupManga.size,
                animeCount = backup.backupAnime.size,
                novelCount = backup.backupNovel.size,
                categoriesCount = backup.backupCategories.size +
                    backup.backupAnimeCategories.size +
                    backup.backupNovelCategories.size,
                hasAppSettings = backup.backupPreferences.isNotEmpty(),
                hasSourceSettings = backup.backupSourcePreferences.isNotEmpty(),
                hasExtensions = backup.backupExtensions.isNotEmpty(),
                hasAchievements = backup.backupAchievements.isNotEmpty() || backup.backupStats != null,
                hasExtensionRepos = backup.backupMangaExtensionRepo.isNotEmpty() ||
                    backup.backupAnimeExtensionRepo.isNotEmpty() ||
                    backup.backupNovelExtensionRepo.isNotEmpty() ||
                    backup.backupMangaExtensionStore.isNotEmpty() ||
                    backup.backupAnimeExtensionStore.isNotEmpty() ||
                    backup.backupNovelExtensionStore.isNotEmpty(),
            )
        }
    }
}
