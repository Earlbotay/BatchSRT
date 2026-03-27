package com.earlstore.batchsrt.model

data class BatchSettings(
    val sourceLanguage: String = "ms-MY",
    val translateEnabled: Boolean = false,
    val targetLanguage: String = "en",
    val outputFormat: OutputFormat = OutputFormat.SRT,
    val outputLocation: String = "BatchSRT"
)

enum class OutputFormat(val extension: String, val displayName: String) {
    SRT(".srt", "SubRip (.srt)"),
    VTT(".vtt", "WebVTT (.vtt)"),
    BOTH("", "Both (.srt + .vtt)")
}

data class LanguageItem(
    val code: String,
    val speechCode: String,
    val name: String,
    val mlKitCode: String = ""
)

val SUPPORTED_LANGUAGES = listOf(
    LanguageItem("ms", "ms-MY", "Bahasa Melayu", "ms"),
    LanguageItem("en", "en-US", "English", "en"),
    LanguageItem("zh", "zh-CN", "中文", "zh"),
    LanguageItem("ja", "ja-JP", "日本語", "ja"),
    LanguageItem("ko", "ko-KR", "한국어", "ko"),
    LanguageItem("hi", "hi-IN", "हिन्दी", "hi"),
    LanguageItem("ar", "ar-SA", "العربية", "ar"),
    LanguageItem("th", "th-TH", "ไทย", "th"),
    LanguageItem("vi", "vi-VN", "Tiếng Việt", "vi"),
    LanguageItem("id", "id-ID", "Bahasa Indonesia", "id"),
    LanguageItem("tl", "tl-PH", "Filipino", "tl"),
    LanguageItem("fr", "fr-FR", "Français", "fr"),
    LanguageItem("de", "de-DE", "Deutsch", "de"),
    LanguageItem("es", "es-ES", "Español", "es"),
    LanguageItem("pt", "pt-BR", "Português", "pt"),
    LanguageItem("ru", "ru-RU", "Русский", "ru"),
    LanguageItem("it", "it-IT", "Italiano", "it"),
    LanguageItem("nl", "nl-NL", "Nederlands", "nl"),
    LanguageItem("tr", "tr-TR", "Türkçe", "tr"),
    LanguageItem("pl", "pl-PL", "Polski", "pl"),
)
