package com.app.ttsreader.utils

import com.google.mlkit.nl.translate.TranslateLanguage
import com.app.ttsreader.domain.model.AppLanguage
import java.util.Locale

/**
 * Curated list of languages supported by both ML Kit Translation and Android TTS.
 *
 * ML Kit supports 100+ languages but not all have reliable TTS engine support.
 * This list is the practical intersection of both systems, ordered by global speaker count.
 *
 * The [AppLanguage.mlKitCode] values use [TranslateLanguage] constants — do not
 * hard-code raw strings, as ML Kit may change them between releases.
 *
 * Note on Hebrew: ML Kit uses the older ISO code "iw" ([TranslateLanguage.HEBREW])
 * while Java Locale uses "he". Both are handled correctly here.
 */
object LanguageUtils {

    val SUPPORTED_LANGUAGES: List<AppLanguage> = listOf(
        AppLanguage(TranslateLanguage.ENGLISH,    "English",            Locale.ENGLISH),
        AppLanguage(TranslateLanguage.SPANISH,    "Spanish",            Locale("es")),
        AppLanguage(TranslateLanguage.FRENCH,     "French",             Locale.FRENCH),
        AppLanguage(TranslateLanguage.GERMAN,     "German",             Locale.GERMAN),
        AppLanguage(TranslateLanguage.ITALIAN,    "Italian",            Locale.ITALIAN),
        AppLanguage(TranslateLanguage.PORTUGUESE, "Portuguese",         Locale("pt")),
        AppLanguage(TranslateLanguage.RUSSIAN,    "Russian",            Locale("ru")),
        AppLanguage(TranslateLanguage.JAPANESE,   "Japanese",           Locale.JAPANESE),
        AppLanguage(TranslateLanguage.KOREAN,     "Korean",             Locale.KOREAN),
        AppLanguage(TranslateLanguage.CHINESE,    "Chinese (Simplified)", Locale.SIMPLIFIED_CHINESE),
        AppLanguage(TranslateLanguage.ARABIC,     "Arabic",             Locale("ar")),
        AppLanguage(TranslateLanguage.HINDI,      "Hindi",              Locale("hi")),
        AppLanguage(TranslateLanguage.DUTCH,      "Dutch",              Locale("nl")),
        AppLanguage(TranslateLanguage.POLISH,     "Polish",             Locale("pl")),
        AppLanguage(TranslateLanguage.TURKISH,    "Turkish",            Locale("tr")),
        AppLanguage(TranslateLanguage.VIETNAMESE, "Vietnamese",         Locale("vi")),
        AppLanguage(TranslateLanguage.THAI,       "Thai",               Locale("th")),
        AppLanguage(TranslateLanguage.INDONESIAN, "Indonesian",         Locale("id")),
        AppLanguage(TranslateLanguage.SWEDISH,    "Swedish",            Locale("sv")),
        AppLanguage(TranslateLanguage.GREEK,      "Greek",              Locale("el")),
        AppLanguage(TranslateLanguage.DANISH,     "Danish",             Locale("da")),
        AppLanguage(TranslateLanguage.UKRAINIAN,  "Ukrainian",          Locale("uk")),
        AppLanguage(TranslateLanguage.HEBREW,     "Hebrew",             Locale("iw")),
        AppLanguage(TranslateLanguage.BENGALI,    "Bengali",            Locale("bn")),
        AppLanguage(TranslateLanguage.TAMIL,      "Tamil",              Locale("ta")),
        AppLanguage(TranslateLanguage.TELUGU,     "Telugu",             Locale("te")),
        AppLanguage(TranslateLanguage.SWAHILI,    "Swahili",            Locale("sw")),

        // ── Extended language set ─────────────────────────────────────────────
        AppLanguage(TranslateLanguage.AFRIKAANS,     "Afrikaans",          Locale("af")),
        AppLanguage(TranslateLanguage.ALBANIAN,      "Albanian",           Locale("sq")),
        AppLanguage(TranslateLanguage.BELARUSIAN,    "Belarusian",         Locale("be")),
        AppLanguage(TranslateLanguage.BULGARIAN,     "Bulgarian",          Locale("bg")),
        AppLanguage(TranslateLanguage.CATALAN,       "Catalan",            Locale("ca")),
        AppLanguage(TranslateLanguage.CROATIAN,      "Croatian",           Locale("hr")),
        AppLanguage(TranslateLanguage.CZECH,         "Czech",              Locale("cs")),
        AppLanguage(TranslateLanguage.ESPERANTO,     "Esperanto",          Locale("eo")),
        AppLanguage(TranslateLanguage.ESTONIAN,      "Estonian",           Locale("et")),
        AppLanguage(TranslateLanguage.PERSIAN,       "Persian",            Locale("fa")),
        AppLanguage(TranslateLanguage.TAGALOG,       "Filipino",           Locale("fil")),
        AppLanguage(TranslateLanguage.FINNISH,       "Finnish",            Locale("fi")),
        AppLanguage(TranslateLanguage.GALICIAN,      "Galician",           Locale("gl")),
        AppLanguage(TranslateLanguage.GEORGIAN,      "Georgian",           Locale("ka")),
        AppLanguage(TranslateLanguage.GUJARATI,      "Gujarati",           Locale("gu")),
        AppLanguage(TranslateLanguage.HAITIAN_CREOLE,"Haitian Creole",     Locale("ht")),
        AppLanguage(TranslateLanguage.HUNGARIAN,     "Hungarian",          Locale("hu")),
        AppLanguage(TranslateLanguage.ICELANDIC,     "Icelandic",          Locale("is")),
        AppLanguage(TranslateLanguage.IRISH,         "Irish",              Locale("ga")),
        AppLanguage(TranslateLanguage.KANNADA,       "Kannada",            Locale("kn")),
        AppLanguage(TranslateLanguage.LATVIAN,       "Latvian",            Locale("lv")),
        AppLanguage(TranslateLanguage.LITHUANIAN,    "Lithuanian",         Locale("lt")),
        AppLanguage(TranslateLanguage.MACEDONIAN,    "Macedonian",         Locale("mk")),
        AppLanguage(TranslateLanguage.MALAY,         "Malay",              Locale("ms")),
        AppLanguage(TranslateLanguage.MALTESE,       "Maltese",            Locale("mt")),
        AppLanguage(TranslateLanguage.MARATHI,       "Marathi",            Locale("mr")),
        AppLanguage(TranslateLanguage.NORWEGIAN,     "Norwegian",          Locale("no")),
        AppLanguage(TranslateLanguage.ROMANIAN,      "Romanian",           Locale("ro")),
        AppLanguage(TranslateLanguage.SLOVAK,        "Slovak",             Locale("sk")),
        AppLanguage(TranslateLanguage.SLOVENIAN,     "Slovenian",          Locale("sl")),
        AppLanguage(TranslateLanguage.URDU,          "Urdu",               Locale("ur")),
        AppLanguage(TranslateLanguage.WELSH,         "Welsh",              Locale("cy")),
    )

    /** Default source language — English, the most common OCR source. */
    val DEFAULT_SOURCE: AppLanguage = SUPPORTED_LANGUAGES.first {
        it.mlKitCode == TranslateLanguage.ENGLISH
    }

    /** Default target language — Spanish, the second-most-spoken language globally. */
    val DEFAULT_TARGET: AppLanguage = SUPPORTED_LANGUAGES.first {
        it.mlKitCode == TranslateLanguage.SPANISH
    }

    /** Looks up an [AppLanguage] by its ML Kit code. Returns null if not in the supported list. */
    fun findByCode(mlKitCode: String): AppLanguage? =
        SUPPORTED_LANGUAGES.firstOrNull { it.mlKitCode == mlKitCode }
}
