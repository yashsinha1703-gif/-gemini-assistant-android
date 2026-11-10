package com.assistant.gemini.service

import android.content.Intent
import android.speech.RecognitionService

/**
 * Standard RecognitionService stub required by Android's assistant framework
 * to support speech-to-text recognition requests from the OS.
 */
class AssistantRecognitionService : RecognitionService() {

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        // Can bridge into Android's default speech engine or Gemini audio streams
    }

    override fun onCancel(listener: Callback?) {
    }

    override fun onStopListening(listener: Callback?) {
    }
}
