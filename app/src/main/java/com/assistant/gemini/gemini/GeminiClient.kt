package com.assistant.gemini.gemini

import android.graphics.Bitmap
import com.assistant.gemini.GeminiAssistantApp
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GeminiAnalysisResult(
    val explanation: String,
    val suggestedSearchQuery: String? = null
)

object GeminiClient {

    private const val MODEL_NAME = "gemini-1.5-flash"

    private fun getModel(): GenerativeModel {
        val apiKey = GeminiAssistantApp.instance.geminiApiKey
        if (apiKey.isBlank()) {
            throw IllegalStateException("Gemini API key is not set. Please add your API key in settings.")
        }
        return GenerativeModel(
            modelName = MODEL_NAME,
            apiKey = apiKey
        )
    }

    /**
     * Multimodal analysis of the entire active screen.
     */
    suspend fun analyzeScreen(
        screenshot: Bitmap,
        customPrompt: String? = null
    ): String = withContext(Dispatchers.IO) {
        val model = getModel()
        val promptText = if (!customPrompt.isNullOrBlank()) {
            customPrompt
        } else {
            "Give me a clear, helpful overview of what is currently displayed on this screen. " +
            "Summarize the main content, extract any notable names, dates, or prices, " +
            "and suggest relevant next steps or actions."
        }

        val response = model.generateContent(
            content {
                image(screenshot)
                text(promptText)
            }
        )
        response.text ?: "No analysis could be generated for this screen."
    }

    /**
     * Circle to Search: Analyses a cropped region of the screen.
     * Identifies the circled object, product, artwork, code, or text,
     * and suggests a Google search query.
     */
    suspend fun searchCircledRegion(
        croppedBitmap: Bitmap,
        customPrompt: String? = null
    ): GeminiAnalysisResult = withContext(Dispatchers.IO) {
        val model = getModel()
        val promptText = if (!customPrompt.isNullOrBlank()) {
            customPrompt
        } else {
            "You are powering an intelligent 'Circle to Search' feature. " +
            "1. Identify exactly what is inside this circled image (e.g. product name, person, translation, concept, or answer). " +
            "2. Explain what it is concisely. " +
            "3. On the very last line, write 'SEARCH_QUERY: <best Google search query>' so the user can look it up."
        }

        val response = model.generateContent(
            content {
                image(croppedBitmap)
                text(promptText)
            }
        )

        val fullText = response.text ?: "Could not identify the selected area."
        var searchQuery: String? = null
        val lines = fullText.lines()

        for (line in lines) {
            if (line.contains("SEARCH_QUERY:", ignoreCase = true)) {
                searchQuery = line.substringAfter("SEARCH_QUERY:").trim()
                break
            }
        }

        val cleanExplanation = fullText
            .replace(Regex("(?i)SEARCH_QUERY:.*"), "")
            .trim()

        GeminiAnalysisResult(
            explanation = cleanExplanation,
            suggestedSearchQuery = searchQuery
        )
    }

    /**
     * Translates text detected on the screen to the target language (default English).
     */
    suspend fun translateText(
        text: String,
        targetLanguage: String = "English"
    ): String = withContext(Dispatchers.IO) {
        val model = getModel()
        val prompt = "Translate the following text into $targetLanguage accurately and naturally. Only return the translation:\n\n$text"
        val response = model.generateContent(prompt)
        response.text ?: "Translation failed."
    }

    /**
     * Translates any text present in the image directly using Gemini Multimodal.
     */
    suspend fun translateImageRegion(
        bitmap: Bitmap,
        targetLanguage: String = "English"
    ): String = withContext(Dispatchers.IO) {
        val model = getModel()
        val prompt = "Detect any text inside this image and translate it into $targetLanguage. Provide the original text (if short) and the clean translation."
        val response = model.generateContent(
            content {
                image(bitmap)
                text(prompt)
            }
        )
        response.text ?: "Could not translate text in image."
    }

    /**
     * High accuracy multimodal OCR using Gemini Vision.
     */
    suspend fun extractTextWithGemini(
        bitmap: Bitmap
    ): String = withContext(Dispatchers.IO) {
        val model = getModel()
        val prompt = "Transcribe all visible text in this image verbatim. Do not add commentary or explanations, just return the extracted text."
        val response = model.generateContent(
            content {
                image(bitmap)
                text(prompt)
            }
        )
        response.text ?: ""
    }

    /**
     * Voice or text query answered with the screen as visual context.
     */
    suspend fun answerQuestion(
        query: String,
        screenshot: Bitmap?
    ): String = withContext(Dispatchers.IO) {
        val model = getModel()
        val response = model.generateContent(
            content {
                screenshot?.let { image(it) }
                text(query)
            }
        )
        response.text ?: "I couldn't process your request."
    }
}
