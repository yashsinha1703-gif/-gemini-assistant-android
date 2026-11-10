# Gemini Assistant Android: Circle to Search & AI Assistant

A complete native Android AI Assistant app that replaces the legacy Google Assistant with **Circle to Search**, **Google Lens Visual Search**, **On-Device Text OCR & Copy**, **Screen Translation**, and **Gemini 1.5 Multimodal AI**.

---

## ✨ Features

1. **Navigation Button / Gesture Trigger (`VoiceInteractionService`)**:
   - Long-press the Home button (or navigation bar pill).
   - Swipe inward from bottom corners (gesture navigation).
   - Hold the Power button (configured to digital assistant).
   - Instantly freezes the screen buffer and brings up the interactive assistant overlay.

2. **Interactive Screen Selection (Circle to Search)**:
   - Lasso, circle, or drag a selection box around any text, object, image, or UI element on screen.
   - Smooth animated glow and bounding box selection handles.

3. **1-Tap Text Extraction & Copy (OCR)**:
   - Powered by Google Play Services **ML Kit Text Recognition** (blazing fast, on-device, offline).
   - Instantly detects all text in the selected area or entire screen.
   - Tap **"Copy Text"** to immediately copy to Android's clipboard.

4. **Instant Translation**:
   - Tap **"Translate"** on any selected region or full screen.
   - Automatically translates foreign text into your local language using **Gemini Multimodal AI**.
   - Optional Text-to-Speech audio readout of the translation.

5. **Automatic Google Lens & Reverse Image Search**:
   - Tap **"Google Lens"** to immediately dispatch the cropped screenshot directly into the Google Lens app / Google App.
   - Performs instant visual search for products, landmarks, plants, artwork, and visual matches.

6. **Gemini Multimodal Search & Voice Chat**:
   - Tap **"Ask Gemini"** for AI explanations, homework solving, and code breakdown.
   - Type in the search box ("Where can I buy this?", "Explain this graph").
   - Tap the microphone button to ask spoken questions about what's on your screen.

---

## 📁 Architecture & File Overview

```
gemini-assistant-android/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml                        # Assistant & FileProvider declarations
│   │   ├── java/com/assistant/gemini/
│   │   │   ├── GeminiAssistantApp.kt                  # App instance & shared preferences
│   │   │   ├── MainActivity.kt                        # Setup, default assistant activation & API keys
│   │   │   ├── ocr/
│   │   │   │   └── TextRecognitionHelper.kt           # Google ML Kit on-device fast OCR
│   │   │   ├── search/
│   │   │   │   └── LensSearchHelper.kt                # Google Lens & Image Search launcher
│   │   │   ├── gemini/
│   │   │   │   └── GeminiClient.kt                    # Gemini 1.5 Flash multimodal API client
│   │   │   ├── service/
│   │   │   │   ├── GeminiVoiceInteractionService.kt   # System VoiceInteractionService
│   │   │   │   ├── GeminiVoiceInteractionSessionService.kt # Session factory
│   │   │   │   ├── GeminiVoiceInteractionSession.kt   # Screen freeze, actions, OCR, Lens & voice
│   │   │   │   └── AssistantRecognitionService.kt     # Speech recognition service
│   │   │   └── ui/
│   │   │       └── CircleSelectionOverlayView.kt      # Interactive drawing canvas (Circle to Search)
│   │   └── res/
│   │       ├── layout/
│   │       │   ├── activity_main.xml                  # Settings & setup screen
│   │       │   └── session_assistant_overlay.xml      # Fullscreen assistant overlay layout
│   │       ├── xml/
│   │       │   ├── file_paths.xml                     # FileProvider cache paths for image sharing
│   │       │   ├── assistant_interaction_service.xml
│   │       │   └── assistant_recognition_service.xml
│   └── build.gradle.kts                               # Dependencies (Gemini, ML Kit, AndroidX)
```

---

## 🚀 How to Set Up & Use

### Step 1: Open the Project in Android Studio
1. Open **Android Studio** (Koala / Ladybug or newer).
2. Select **Open** and choose the directory:
   `gemini-assistant-android`
3. Allow Gradle to sync.

### Step 2: Configure your Gemini API Key
- Get a free key from [Google AI Studio](https://aistudio.google.com/).
- Enter it directly inside the app on first launch (or set it in `GeminiAssistantApp`).

### Step 3: Set as Default Assistant on your Android Phone
1. Build and install the APK on your Android device (Android 8.0+ / API 26 to 35).
2. Open the **Gemini Assistant** app.
3. Tap **"Set as Default Assistant"** (or open phone **Settings > Apps > Default apps > Digital assistant app**).
4. Select **Gemini Assistant**.
5. Enable the toggles for:
   - **"Use screenshot"** (allows the app to capture screen for Circle to Search).
   - **"Use text from screen"**.

### Step 4: Use via Navigation Button
- **Long-press Home Button / Navigation Bar**: The screen freezes with an assistant overlay.
- **Circle or Drag**: Draw a circle or box over any part of the screen.
- Tap **Copy Text** 📋 to copy text to clipboard.
- Tap **Translate** 🌐 to translate the selected text.
- Tap **Google Lens** 🔍 to run a visual image search.
- Tap **Ask Gemini** ✨ or type/speak a question in the bottom search bar.
