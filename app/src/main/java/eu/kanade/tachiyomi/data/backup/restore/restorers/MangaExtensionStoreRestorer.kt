package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import mihon.domain.extensionstore.manga.repository.MangaExtensionStoreRepository
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaExtensionStoreRestorer(
    private val mangaHandler: MangaDatabaseHandler = Injekt.get(),
    private val mangaStoreRepository: MangaExtensionStoreRepository = Injekt.get(),
) {

    suspend operator fun invoke(
        backupStore: BackupExtensionStore,
    ) {
        val dbStores = mangaStoreRepository.getAll()
        val existingByIndex = dbStores.associateBy { it.indexUrl }
        val existingByKey = dbStores.associateBy { it.signingKey }
        val indexExists = existingByIndex[backupStore.indexUrl]
        val keyExists = existingByKey[backupStore.signingKey]
        if (indexExists != null && indexExists.signingKey == backupStore.signingKey) {
            // Already present, e.g. the same backup restored twice: nothing to do.
            return
        }
        if (indexExists != null) {
            error("Already Exists with different signing key")
        } else if (keyExists != null) {
            error("${keyExists.name} has the same signing key")
        } else {
            mangaHandler.await { db ->
                db.extension_storeQueries.upsert(
                    indexUrl = backupStore.indexUrl,
                    name = backupStore.name,
                    badgeLabel = backupStore.badgeLabel,
                    signingKey = backupStore.signingKey,
                    contactWebsite = backupStore.contactWebsite,
                    contactDiscord = backupStore.contactDiscord,
                    isLegacy = backupStore.isLegacy,
                    extensionListUrl = backupStore.extensionListUrl,
                )
            }
        }
    }
}
