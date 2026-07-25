package mihon.domain.extensionstore.model

data class ExtensionStore(
    val indexUrl: String,
    val name: String,
    val badgeLabel: String,
    val signingKey: String,
    val contact: Contact,
    val isLegacy: Boolean,
    val extensionListUrl: String?,
) {
    data class Contact(
        val website: String,
        val discord: String?,
    )
}

/** Base URL used by legacy plugin listing (manga/anime index.min.json, novel plugin repos). */
fun ExtensionStore.legacyBaseUrl(): String = when {
    indexUrl.isExtensionStoreIndexUrl() -> indexUrl.toExtensionStoreBaseUrl()
    indexUrl.endsWith(".json") -> indexUrl.substringBeforeLast("/")
    else -> indexUrl.trimEnd('/')
}
