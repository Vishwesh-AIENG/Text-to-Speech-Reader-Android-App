package com.app.ttsreader.ai

import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stateless helper that calls the Gemini 1.5 Flash model to produce a
 * 4-6 bullet-point summary of the supplied [text].
 *
 * The caller is responsible for supplying a valid [apiKey].  This object
 * never caches keys — it is created fresh on each call so the key can be
 * rotated without restarting the ViewModel.
 *
 * Throws on network failure, invalid key, or empty model response so the
 * ViewModel can surface a user-facing error.
 */
object GeminiSummarizer {

    private const val MODEL = "gemini-1.5-flash"

    /**
     * Summarises [text] in 4–6 bullet points.
     *
     * @param apiKey       User-supplied Gemini API key.
     * @param text         Raw or translated page text (typically <8 000 chars).
     * @param targetLanguage Display name of the language for the summary output,
     *                     e.g. "Spanish". Pass "English" to always summarise in English.
     * @return             Bullet-point summary string as returned by the model.
     * @throws Exception   On network error, invalid key, quota exceeded, or empty response.
     */
    suspend fun summarize(
        apiKey:         String,
        text:           String,
        targetLanguage: String = "English"
    ): String = withContext(Dispatchers.IO) {
        val model = GenerativeModel(
            modelName = MODEL,
            apiKey    = apiKey
        )

        val langInstruction = if (targetLanguage.equals("english", ignoreCase = true))
            ""
        else
            " Write the summary in $targetLanguage."

        val prompt = buildString {
            append("Summarize the following text in 4 to 6 concise bullet points.")
            append(langInstruction)
            append("\n\nText:\n")
            // Clamp input so we never exceed the token budget
            append(text.take(MAX_INPUT_CHARS))
        }

        val response = model.generateContent(prompt)
        response.text?.trim().takeIf { it?.isNotEmpty() == true }
            ?: error("Gemini returned an empty response.")
    }

    private const val MAX_INPUT_CHARS = 12_000
}
