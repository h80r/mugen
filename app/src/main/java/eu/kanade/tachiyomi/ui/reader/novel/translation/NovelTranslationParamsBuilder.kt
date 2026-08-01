package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider

internal const val DEEPSEEK_TEMPERATURE_MIN = 1.3f
internal const val DEEPSEEK_TEMPERATURE_MAX = 1.5f
internal const val DEEPSEEK_TOP_P_MIN = 0.9f
internal const val DEEPSEEK_TOP_P_MAX = 0.95f
internal const val DEEPSEEK_DEFAULT_PRESENCE_PENALTY = 0.15f
internal const val DEEPSEEK_DEFAULT_FREQUENCY_PENALTY = 0.15f

/**
 * Builds the provider-specific translation parameters from the shared [NovelReaderSettings].
 *
 * Shared by the chapter reader and the background translation processor so both stay in sync.
 */
internal fun NovelReaderSettings.toGeminiTranslationParams(): GeminiTranslationParams {
    return GeminiTranslationParams(
        apiKey = geminiApiKey,
        model = geminiModel.normalizeGeminiModelId(),
        sourceLang = geminiSourceLang,
        targetLang = geminiTargetLang,
        reasoningEffort = geminiReasoningEffort,
        budgetTokens = geminiBudgetTokens,
        temperature = geminiTemperature,
        topP = geminiTopP,
        topK = geminiTopK,
        promptMode = geminiPromptMode,
        promptModifiers = resolveTranslationPromptModifiers(family = translationPromptFamily()),
        provider = translationProvider,
        privateUnlocked = geminiPrivateUnlocked,
        privatePythonLikeMode = geminiPrivatePythonLikeMode,
    )
}

internal fun NovelReaderSettings.toOpenRouterTranslationParams(): OpenRouterTranslationParams {
    return OpenRouterTranslationParams(
        baseUrl = openRouterBaseUrl,
        apiKey = openRouterApiKey,
        model = openRouterModel,
        sourceLang = geminiSourceLang,
        targetLang = geminiTargetLang,
        promptMode = geminiPromptMode,
        promptModifiers = resolveTranslationPromptModifiers(family = translationPromptFamily()),
        temperature = geminiTemperature,
        topP = geminiTopP,
        reasoningEffort = normalizeTranslationReasoningEffort(
            provider = NovelTranslationProvider.OPENROUTER,
            model = openRouterModel,
            value = geminiReasoningEffort,
        ),
    )
}

internal fun NovelReaderSettings.toDeepSeekTranslationParams(): DeepSeekTranslationParams {
    return DeepSeekTranslationParams(
        baseUrl = deepSeekBaseUrl,
        apiKey = deepSeekApiKey,
        model = deepSeekModel,
        sourceLang = geminiSourceLang,
        targetLang = geminiTargetLang,
        promptMode = geminiPromptMode,
        promptModifiers = resolveTranslationPromptModifiers(family = translationPromptFamily()),
        temperature = geminiTemperature.coerceIn(DEEPSEEK_TEMPERATURE_MIN, DEEPSEEK_TEMPERATURE_MAX),
        topP = geminiTopP.coerceIn(DEEPSEEK_TOP_P_MIN, DEEPSEEK_TOP_P_MAX),
        reasoningEffort = normalizeTranslationReasoningEffort(
            provider = NovelTranslationProvider.DEEPSEEK,
            model = deepSeekModel,
            value = geminiReasoningEffort,
        ) ?: "none",
        presencePenalty = DEEPSEEK_DEFAULT_PRESENCE_PENALTY,
        frequencyPenalty = DEEPSEEK_DEFAULT_FREQUENCY_PENALTY,
    )
}

internal fun NovelReaderSettings.toMistralTranslationParams(): MistralTranslationParams {
    return MistralTranslationParams(
        baseUrl = mistralBaseUrl,
        apiKey = mistralApiKey,
        model = mistralModel,
        sourceLang = geminiSourceLang,
        targetLang = geminiTargetLang,
        promptMode = geminiPromptMode,
        promptModifiers = resolveTranslationPromptModifiers(family = translationPromptFamily()),
        temperature = geminiTemperature,
        topP = geminiTopP,
        reasoningEffort = normalizeTranslationReasoningEffort(
            provider = NovelTranslationProvider.MISTRAL,
            model = mistralModel,
            value = geminiReasoningEffort,
        ),
    )
}

internal fun NovelReaderSettings.toNvidiaTranslationParams(): NvidiaTranslationParams {
    return NvidiaTranslationParams(
        baseUrl = nvidiaBaseUrl,
        apiKey = nvidiaApiKey,
        model = nvidiaModel,
        sourceLang = geminiSourceLang,
        targetLang = geminiTargetLang,
        promptMode = geminiPromptMode,
        promptModifiers = resolveTranslationPromptModifiers(family = translationPromptFamily()),
        temperature = geminiTemperature,
        topP = geminiTopP,
    )
}

internal fun NovelReaderSettings.toOllamaCloudTranslationParams(): OllamaCloudTranslationParams {
    return OllamaCloudTranslationParams(
        baseUrl = ollamaCloudBaseUrl,
        apiKey = ollamaCloudApiKey,
        model = ollamaCloudModel,
        sourceLang = geminiSourceLang,
        targetLang = geminiTargetLang,
        promptMode = geminiPromptMode,
        promptModifiers = resolveTranslationPromptModifiers(family = translationPromptFamily()),
        temperature = geminiTemperature,
        topP = geminiTopP,
        reasoningEffort = normalizeTranslationReasoningEffort(
            provider = NovelTranslationProvider.OLLAMA_CLOUD,
            model = ollamaCloudModel,
            value = geminiReasoningEffort,
        ),
    )
}

private fun NovelReaderSettings.translationPromptFamily(): NovelTranslationPromptFamily {
    return when (translationProvider) {
        NovelTranslationProvider.GEMINI_PRIVATE,
        -> NovelTranslationPromptFamily.RUSSIAN
        NovelTranslationProvider.GEMINI,
        NovelTranslationProvider.OPENROUTER,
        NovelTranslationProvider.DEEPSEEK,
        NovelTranslationProvider.MISTRAL,
        NovelTranslationProvider.NVIDIA,
        NovelTranslationProvider.OLLAMA_CLOUD,
        ->
            resolveNovelTranslationPromptFamily(geminiTargetLang)
    }
}

private fun NovelReaderSettings.resolveTranslationPromptModifiers(
    family: NovelTranslationPromptFamily = NovelTranslationPromptFamily.RUSSIAN,
): String {
    val modifierText = GeminiPromptModifiers.buildPromptText(
        enabledIds = geminiEnabledPromptModifiers,
        customModifier = geminiCustomPromptModifier,
        family = family,
    )
    val styleDirective = NovelTranslationStylePresets.promptDirective(
        geminiStylePreset,
        family = family,
    ).trim()
    return listOf(
        styleDirective,
        modifierText,
        geminiPromptModifiers.trim(),
    ).filter { it.isNotBlank() }
        .joinToString("\n\n")
}
