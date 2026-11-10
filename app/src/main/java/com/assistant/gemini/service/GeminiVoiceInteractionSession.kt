package com.assistant.gemini.service

import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.widget.NestedScrollView
import com.assistant.gemini.R
import com.assistant.gemini.gemini.GeminiClient
import com.assistant.gemini.ocr.TextRecognitionHelper
import com.assistant.gemini.search.LensSearchHelper
import com.assistant.gemini.ui.CircleSelectionOverlayView
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.*
import java.util.Locale

/**
 * Android System Assistant Session UI & Logic.
 * Recreates the Google Assistant / Circle to Search experience:
 * - Triggered via Navigation bar, Home button long-press, or corner swipe
 * - Screen freeze & interactive Circle / Box selection
 * - 1-tap On-Device Text OCR & Copy to Clipboard
 * - Instant Translation (via Gemini Multimodal AI)
 * - Automatic Google Lens / Google Image Search
 * - Multimodal Google Gemini Q&A with voice and text
 */
class GeminiVoiceInteractionSession(context: Context) : VoiceInteractionSession(context),
    TextToSpeech.OnInitListener {

    private val sessionScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var activeScreenshot: Bitmap? = null
    private var currentSelectedBitmap: Bitmap? = null
    private var textToSpeech: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null

    // UI elements
    private lateinit var rootView: View
    private lateinit var circleOverlay: CircleSelectionOverlayView
    private lateinit var bannerCirclePrompt: LinearLayout
    private lateinit var textBannerGuide: TextView
    private lateinit var layoutSelectionFloatingBar: LinearLayout
    private lateinit var textStatus: TextView
    private lateinit var textResponse: TextView
    private lateinit var scrollResponse: NestedScrollView
    private lateinit var progressLoading: ProgressBar

    // Floating selection buttons
    private lateinit var btnFloatingCopy: MaterialButton
    private lateinit var btnFloatingTranslate: MaterialButton
    private lateinit var btnFloatingLens: MaterialButton
    private lateinit var btnFloatingGemini: MaterialButton

    // Bottom action buttons
    private lateinit var btnActionCopyAll: MaterialButton
    private lateinit var btnActionTranslateScreen: MaterialButton
    private lateinit var btnActionOverview: MaterialButton
    private lateinit var btnActionResetSelection: MaterialButton
    private lateinit var btnCopyResult: MaterialButton
    private lateinit var btnWebSearch: MaterialButton

    // Inputs
    private lateinit var editQuery: TextInputEditText
    private lateinit var fabMic: FloatingActionButton

    private var lastExtractedText: String? = null
    private var lastSuggestedQuery: String? = null

    override fun onCreate() {
        super.onCreate()
        textToSpeech = TextToSpeech(context, this)
    }

    override fun onCreateContentView(): View {
        val inflater = LayoutInflater.from(context)
        rootView = inflater.inflate(R.layout.session_assistant_overlay, null)

        circleOverlay = rootView.findViewById(R.id.circleSelectionOverlay)
        bannerCirclePrompt = rootView.findViewById(R.id.bannerCirclePrompt)
        textBannerGuide = rootView.findViewById(R.id.textBannerGuide)
        layoutSelectionFloatingBar = rootView.findViewById(R.id.layoutSelectionFloatingBar)
        textStatus = rootView.findViewById(R.id.textAssistantStatus)
        textResponse = rootView.findViewById(R.id.textResponseContent)
        scrollResponse = rootView.findViewById(R.id.scrollResponse)
        progressLoading = rootView.findViewById(R.id.progressLoading)

        // Floating action bar
        btnFloatingCopy = rootView.findViewById(R.id.btnFloatingCopy)
        btnFloatingTranslate = rootView.findViewById(R.id.btnFloatingTranslate)
        btnFloatingLens = rootView.findViewById(R.id.btnFloatingLens)
        btnFloatingGemini = rootView.findViewById(R.id.btnFloatingGemini)

        // Bottom actions
        btnActionCopyAll = rootView.findViewById(R.id.btnActionCopyAll)
        btnActionTranslateScreen = rootView.findViewById(R.id.btnActionTranslateScreen)
        btnActionOverview = rootView.findViewById(R.id.btnActionOverview)
        btnActionResetSelection = rootView.findViewById(R.id.btnActionResetSelection)
        btnCopyResult = rootView.findViewById(R.id.btnCopyResult)
        btnWebSearch = rootView.findViewById(R.id.btnWebSearch)

        editQuery = rootView.findViewById(R.id.editQueryInput)
        fabMic = rootView.findViewById(R.id.fabVoiceMic)

        setupListeners()
        return rootView
    }

    private fun setupListeners() {
        // Selection callback from the drawing canvas
        circleOverlay.onRegionSelected = { croppedBitmap, _ ->
            currentSelectedBitmap = croppedBitmap
            onRegionSelectedByUser(croppedBitmap)
        }

        circleOverlay.onSelectionCleared = {
            currentSelectedBitmap = null
            layoutSelectionFloatingBar.visibility = View.GONE
            btnActionResetSelection.visibility = View.GONE
            textStatus.text = "Select an area or ask Gemini"
        }

        // Floating actions for the selected region
        btnFloatingCopy.setOnClickListener {
            currentSelectedBitmap?.let { copyTextFromBitmap(it) }
        }

        btnFloatingTranslate.setOnClickListener {
            currentSelectedBitmap?.let { translateBitmap(it) }
        }

        btnFloatingLens.setOnClickListener {
            currentSelectedBitmap?.let { searchWithGoogleLens(it) }
        }

        btnFloatingGemini.setOnClickListener {
            currentSelectedBitmap?.let { askGeminiAboutRegion(it) }
        }

        // Screen-wide actions
        btnActionCopyAll.setOnClickListener {
            activeScreenshot?.let { copyTextFromBitmap(it) } ?: run {
                Toast.makeText(context, "No screen buffer available", Toast.LENGTH_SHORT).show()
            }
        }

        btnActionTranslateScreen.setOnClickListener {
            activeScreenshot?.let { translateBitmap(it) } ?: run {
                Toast.makeText(context, "No screen buffer available", Toast.LENGTH_SHORT).show()
            }
        }

        btnActionOverview.setOnClickListener {
            analyzeWholeScreen()
        }

        btnActionResetSelection.setOnClickListener {
            circleOverlay.resetSelection()
        }

        // Copy current result content to clipboard
        btnCopyResult.setOnClickListener {
            val text = textResponse.text?.toString() ?: ""
            if (text.isNotBlank()) {
                copyToClipboard("Assistant Result", text)
            }
        }

        // Web search button
        btnWebSearch.setOnClickListener {
            lastSuggestedQuery?.let { query ->
                launchWebSearch(query)
            }
        }

        // Text query input
        editQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                val query = editQuery.text?.toString()?.trim() ?: ""
                if (query.isNotEmpty()) {
                    performUserQuery(query)
                }
                true
            } else {
                false
            }
        }

        // Voice microphone button
        fabMic.setOnClickListener {
            startVoiceListening()
        }
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        super.onHandleScreenshot(screenshot)
        this.activeScreenshot = screenshot
        circleOverlay.setScreenshot(screenshot)
    }

    override fun onHandleAssist(data: Bundle?, structure: AssistStructure?, content: AssistContent?) {
        super.onHandleAssist(data, structure, content)
    }

    /**
     * Triggered when user circles or drags a selection box over a screen element.
     */
    private fun onRegionSelectedByUser(croppedBitmap: Bitmap) {
        layoutSelectionFloatingBar.visibility = View.VISIBLE
        btnActionResetSelection.visibility = View.VISIBLE
        textStatus.text = "Selected area: choose action"

        // Pre-fetch OCR in background so 1-tap Copy is instant
        sessionScope.launch {
            try {
                lastExtractedText = TextRecognitionHelper.extractText(croppedBitmap)
            } catch (e: Exception) {
                lastExtractedText = null
            }
        }
    }

    /**
     * Extracts text from the bitmap using on-device ML Kit OCR (or Gemini fallback)
     * and copies it directly to the Android Clipboard.
     */
    private fun copyTextFromBitmap(bitmap: Bitmap) {
        setLoadingState("Extracting text from selection...")

        sessionScope.launch {
            try {
                var detectedText = lastExtractedText
                if (detectedText.isNullOrBlank()) {
                    detectedText = try {
                        TextRecognitionHelper.extractText(bitmap)
                    } catch (e: Exception) {
                        // Fallback to Gemini Multimodal OCR
                        GeminiClient.extractTextWithGemini(bitmap)
                    }
                }

                if (!detectedText.isNullOrBlank()) {
                    copyToClipboard("Extracted Text", detectedText)
                    displayResult("Copied to clipboard:\n\n$detectedText")
                } else {
                    displayResult("No readable text detected in this selection.")
                    Toast.makeText(context, R.string.msg_no_text_found, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                displayError("Failed to extract text: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    /**
     * Translates text from the bitmap using Gemini Multimodal translation.
     */
    private fun translateBitmap(bitmap: Bitmap) {
        setLoadingState("Translating selected text...")

        sessionScope.launch {
            try {
                val translation = if (!lastExtractedText.isNullOrBlank()) {
                    GeminiClient.translateText(lastExtractedText!!)
                } else {
                    GeminiClient.translateImageRegion(bitmap)
                }

                displayResult("Translation:\n\n$translation")
                speakAnswer(translation)
            } catch (e: Exception) {
                displayError("Translation failed: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    /**
     * Dispatches the cropped bitmap directly into Google Lens or Google Reverse Image Search.
     */
    private fun searchWithGoogleLens(bitmap: Bitmap) {
        setLoadingState("Opening Google Lens...")
        LensSearchHelper.launchVisualSearch(context, bitmap, lastSuggestedQuery)
        finish()
    }

    /**
     * Analyzes the circled region with Gemini 1.5 Flash multimodal intelligence.
     */
    private fun askGeminiAboutRegion(bitmap: Bitmap) {
        setLoadingState("Analyzing with Gemini...")

        sessionScope.launch {
            try {
                val result = GeminiClient.searchCircledRegion(bitmap)
                displayResult(result.explanation)

                if (!result.suggestedSearchQuery.isNullOrBlank()) {
                    lastSuggestedQuery = result.suggestedSearchQuery
                    btnWebSearch.text = "Google: \"$lastSuggestedQuery\""
                    btnWebSearch.visibility = View.VISIBLE
                } else {
                    btnWebSearch.visibility = View.GONE
                }
            } catch (e: Exception) {
                displayError("Analysis failed: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    /**
     * Full active screen overview with Gemini.
     */
    private fun analyzeWholeScreen() {
        val bmp = activeScreenshot
        if (bmp == null) {
            Toast.makeText(context, "Screenshot unavailable for this screen", Toast.LENGTH_SHORT).show()
            return
        }

        setLoadingState("Summarizing active screen...")
        sessionScope.launch {
            try {
                val summary = GeminiClient.analyzeScreen(bmp)
                displayResult(summary)
            } catch (e: Exception) {
                displayError("Overview failed: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    /**
     * Processes custom user question with active selection or full screen visual context.
     */
    private fun performUserQuery(query: String) {
        setLoadingState("Gemini is thinking...")
        val imageContext = currentSelectedBitmap ?: activeScreenshot

        sessionScope.launch {
            try {
                val answer = GeminiClient.answerQuestion(query, imageContext)
                displayResult(answer)
                speakAnswer(answer)
            } catch (e: Exception) {
                displayError("Failed to answer: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    /**
     * Voice query handling with Android SpeechRecognizer.
     */
    private fun startVoiceListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Toast.makeText(context, "Speech recognition is not available", Toast.LENGTH_SHORT).show()
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    textStatus.text = "Listening..."
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    textStatus.text = "Could not hear you. Tap mic to retry."
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spokenText = matches?.firstOrNull()
                    if (!spokenText.isNullOrBlank()) {
                        editQuery.setText(spokenText)
                        performUserQuery(spokenText)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        speechRecognizer?.startListening(intent)
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, R.string.msg_text_copied, Toast.LENGTH_SHORT).show()
    }

    private fun setLoadingState(message: String) {
        progressLoading.visibility = View.VISIBLE
        textStatus.text = message
        scrollResponse.visibility = View.GONE
    }

    private fun displayResult(text: String) {
        progressLoading.visibility = View.GONE
        textStatus.text = "Result"
        textResponse.text = text
        scrollResponse.visibility = View.VISIBLE
    }

    private fun displayError(errorMessage: String) {
        progressLoading.visibility = View.GONE
        textStatus.text = "Error"
        textResponse.text = errorMessage
        scrollResponse.visibility = View.VISIBLE
    }

    private fun speakAnswer(text: String) {
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "gemini_answer_id")
    }

    private fun launchWebSearch(query: String) {
        val searchUri = Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
        val customTabsIntent = CustomTabsIntent.Builder().build()
        customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        customTabsIntent.launchUrl(context, searchUri)
        finish()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.getDefault()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionScope.cancel()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        speechRecognizer?.destroy()
    }
}
