package com.app.ttsreader.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-device text summarizer powered by Gemma (via MediaPipe LLM Inference API).
 *
 * No internet connection or API key is required after the model file is present.
 * Inference is CPU-bound and takes 20–60 seconds on mid-range devices for a
 * typical page summary — always call from a coroutine and show a progress indicator.
 *
 * The [LlmInference] instance is created and destroyed per call so memory is not
 * held between summaries. Re-initialization costs ~10 seconds on first use.
 */
object GemmaSummarizer {

    private const val MAX_INPUT_CHARS = 6_000
    private const val MAX_NEW_TOKENS  = 512

    /**
     * Summarises [text] in 4–6 bullet points using the on-device Gemma model.
     *
     * @param context        Application context — used to resolve the model file path.
     * @param text           Page text to summarise (clamped to [MAX_INPUT_CHARS]).
     * @param targetLanguage Display language for the summary, e.g. "Spanish".
     *                       Pass "English" (default) to always summarise in English.
     * @return               Bullet-point summary string.
     * @throws Exception     If the model file is missing, initialisation fails,
     *                       or the model returns an empty response.
     */
    suspend fun summarize(
        context:        Context,
        text:           String,
        targetLanguage: String = "English"
    ): String = withContext(Dispatchers.IO) {

        if (!GemmaModelManager.modelExists(context)) {
            error("Gemma model not found. Please download it first.")
        }

        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(GemmaModelManager.modelPath(context))
            .setMaxTokens(MAX_NEW_TOKENS)
            .build()

        val llm = LlmInference.createFromOptions(context, options)
        try {
            val langInstruction =
                if (targetLanguage.equals("english", ignoreCase = true)) ""
                else " Write the summary in $targetLanguage."

            // Gemma instruction-tuned prompt format (<start_of_turn> tokens)
            val prompt = buildString {
                append("<start_of_turn>user\n")
                append("Summarize the following text in 4 to 6 concise bullet points.")
                append(langInstruction)
                append("\n\nText:\n")
                append(text.take(MAX_INPUT_CHARS))
                append("<end_of_turn>\n<start_of_turn>model\n")
            }

            llm.generateResponse(prompt)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: error("Gemma returned an empty response.")
        } finally {
            llm.close()
        }
    }
}
