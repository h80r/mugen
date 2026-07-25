package mihon.domain.extensionrepo.manga.interactor

import eu.kanade.tachiyomi.util.lang.Hash
import logcat.LogPriority
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionstore.manga.repository.MangaExtensionStoreRepository
import mihon.domain.extensionstore.model.toExtensionStoreBaseUrl
import mihon.domain.extensionstore.model.toLegacyExtensionRepoUrl
import mihon.domain.extensionstore.toExtensionRepo
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.core.common.util.system.logcat

class CreateMangaExtensionRepo(
    private val repository: MangaExtensionStoreRepository,
) {
    suspend fun await(
        indexUrl: String,
        displayName: String? = null,
        forceLocalInsert: Boolean = false,
    ): Result {
        val formattedIndexUrl = indexUrl.toHttpUrlOrNull()?.toString() ?: return Result.InvalidUrl

        val insertResult = repository.insert(formattedIndexUrl)
        if (insertResult.isSuccess) {
            if (!displayName.isNullOrBlank()) {
                renameInsertedStore(formattedIndexUrl, displayName)
            }
            return Result.Success
        }

        if (forceLocalInsert) {
            val localIndexUrl = normalizeForceLocalIndexUrl(formattedIndexUrl)
            repository.insertFromPreference(
                localIndexUrl,
                displayName?.takeIf { it.isNotBlank() } ?: extractRepoName(localIndexUrl),
            )
            return Result.Success
        }

        return handleInsertionError(formattedIndexUrl, displayName)
    }

    private fun normalizeForceLocalIndexUrl(indexUrl: String): String {
        return if (indexUrl.trimEnd('/').endsWith("/index.min.json", ignoreCase = true)) {
            indexUrl.toLegacyExtensionRepoUrl()
        } else {
            indexUrl
        }
    }

    private suspend fun renameInsertedStore(indexUrl: String, displayName: String) {
        val store = repository.getAll().find { it.indexUrl == indexUrl } ?: return
        repository.upsertStore(store.copy(name = displayName, badgeLabel = displayName))
    }

    private fun extractRepoName(url: String): String {
        return try {
            val uri = java.net.URI(url)
            val segments = uri.path?.trim('/')?.split("/").orEmpty()
            when {
                uri.host == "raw.githubusercontent.com" && segments.size >= 2 ->
                    "${segments[0]}/${segments[1]}"
                uri.host == "github.com" && segments.size >= 2 ->
                    "${segments[0]}/${segments[1]}"
                segments.size >= 2 -> segments.take(2).joinToString("/")
                else -> url
            }
        } catch (_: Exception) {
            url
        }
    }

    private suspend fun handleInsertionError(indexUrl: String, displayName: String?): Result {
        val stores = repository.getAll()
        if (stores.any { it.indexUrl == indexUrl }) {
            return Result.RepoAlreadyExists
        }
        val fingerprint = "NOFINGERPRINT-${Hash.sha256(indexUrl)}"
        val matching = stores.find { it.signingKey == fingerprint }
        if (matching != null) {
            val newRepo = ExtensionRepo(
                baseUrl = indexUrl.toExtensionStoreBaseUrl(),
                name = displayName?.takeIf { it.isNotBlank() } ?: extractRepoName(indexUrl),
                shortName = null,
                website = indexUrl,
                signingKeyFingerprint = fingerprint,
            )
            return Result.DuplicateFingerprint(matching.toExtensionRepo(), newRepo)
        }
        logcat(LogPriority.WARN) { "Failed to add manga extension store $indexUrl" }
        return Result.InvalidUrl
    }

    sealed interface Result {
        data class DuplicateFingerprint(val oldRepo: ExtensionRepo, val newRepo: ExtensionRepo) : Result
        data object InvalidUrl : Result
        data object RepoAlreadyExists : Result
        data object Success : Result
        data object Error : Result
    }
}
