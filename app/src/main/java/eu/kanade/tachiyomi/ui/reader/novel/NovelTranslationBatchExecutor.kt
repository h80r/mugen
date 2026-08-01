package eu.kanade.tachiyomi.ui.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.translation.DeepSeekTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.MistralTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.NvidiaTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.OllamaCloudTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.OpenRouterTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.toDeepSeekTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.toGeminiTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.toMistralTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.toNvidiaTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.toOllamaCloudTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.toOpenRouterTranslationParams

/**
 * Dispatches a translation batch to the service of the configured provider.
 *
 * Owns the provider translation services so [NovelReaderScreenModel] and its controllers stay
 * focused on reader orchestration instead of provider wiring.
 */
internal class NovelTranslationBatchExecutor(
    private val geminiTranslationService: GeminiTranslationService,
    private val openRouterTranslationService: OpenRouterTranslationService,
    private val deepSeekTranslationService: DeepSeekTranslationService,
    private val mistralTranslationService: MistralTranslationService,
    private val nvidiaTranslationService: NvidiaTranslationService,
    private val ollamaCloudTranslationService: OllamaCloudTranslationService,
) {
    suspend fun requestTranslationBatch(
        segments: List<String>,
        settings: NovelReaderSettings,
        onLog: ((String) -> Unit)? = null,
    ): List<String?>? {
        return when (settings.translationProvider) {
            NovelTranslationProvider.GEMINI -> {
                geminiTranslationService.translateBatch(
                    segments = segments,
                    params = settings.toGeminiTranslationParams(),
                    onLog = onLog,
                )
            }
            NovelTranslationProvider.GEMINI_PRIVATE -> {
                geminiTranslationService.translateBatch(
                    segments = segments,
                    params = settings.toGeminiTranslationParams(),
                    onLog = onLog,
                )
            }
            NovelTranslationProvider.OPENROUTER -> {
                openRouterTranslationService.translateBatch(
                    segments = segments,
                    params = settings.toOpenRouterTranslationParams(),
                    onLog = onLog,
                )
            }
            NovelTranslationProvider.DEEPSEEK -> {
                deepSeekTranslationService.translateBatch(
                    segments = segments,
                    params = settings.toDeepSeekTranslationParams(),
                    onLog = onLog,
                )
            }
            NovelTranslationProvider.MISTRAL -> {
                mistralTranslationService.translateBatch(
                    segments = segments,
                    params = settings.toMistralTranslationParams(),
                    onLog = onLog,
                )
            }
            NovelTranslationProvider.NVIDIA -> {
                nvidiaTranslationService.translateBatch(
                    segments = segments,
                    params = settings.toNvidiaTranslationParams(),
                    onLog = onLog,
                )
            }
            NovelTranslationProvider.OLLAMA_CLOUD -> {
                ollamaCloudTranslationService.translateBatch(
                    segments = segments,
                    params = settings.toOllamaCloudTranslationParams(),
                    onLog = onLog,
                )
            }
        }
    }
}
