package com.example.data.model

import java.util.Locale

object LanguageUtils {
    private val KNOWN_LANGUAGES = mapOf(
        "en" to "English",
        "hi" to "Hindi",
        "gu" to "Gujarati",
        "ko" to "Korean",
        "ja" to "Japanese",
        "ta" to "Tamil",
        "te" to "Telugu",
        "ml" to "Malayalam",
        "kn" to "Kannada",
        "mr" to "Marathi",
        "bn" to "Bengali",
        "pa" to "Punjabi",
        "fr" to "French",
        "es" to "Spanish",
        "de" to "German",
        "it" to "Italian",
        "pt" to "Portuguese",
        "zh" to "Chinese",
        "ru" to "Russian",
        "th" to "Thai",
        "tr" to "Turkish",
        "ar" to "Arabic",
        "id" to "Indonesian",
        "fa" to "Persian",
    )

    val PRIMARY_LANGUAGE_CODES = listOf("en", "hi", "gu", "ko", "ja")

    fun displayName(code: String?): String {
        val normalized = code?.trim()?.lowercase(Locale.US).orEmpty()
        if (normalized.isEmpty()) return ""
        KNOWN_LANGUAGES[normalized]?.let { return it }
        val localeName = Locale.forLanguageTag(normalized).getDisplayLanguage(Locale.ENGLISH)
        return if (localeName.isNotBlank() && !localeName.equals(normalized, ignoreCase = true)) {
            localeName.replaceFirstChar { it.titlecase(Locale.ENGLISH) }
        } else {
            normalized.uppercase(Locale.US)
        }
    }

    fun shortBadge(code: String?): String {
        val name = displayName(code)
        return name.ifBlank { "" }
    }
}

data class LanguageChipCount(
    val code: String,
    val label: String,
    val count: Int,
) {
    val isPrimary: Boolean
        get() = code in LanguageUtils.PRIMARY_LANGUAGE_CODES
}
