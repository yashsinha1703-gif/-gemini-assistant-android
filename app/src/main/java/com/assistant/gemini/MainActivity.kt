package com.assistant.gemini

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.assistant.gemini.auth.GoogleAuthManager
import com.assistant.gemini.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Microphone permission granted for voice queries.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Microphone permission is needed for voice assistance.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkPermissions()
        initViews()
        updateUiState()
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun initViews() {
        // 1. Open Android System Voice / Assistant settings
        binding.btnSetDefaultAssistant.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
                startActivity(intent)
            } catch (e: Exception) {
                // Fallback to manage default apps
                val fallbackIntent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                startActivity(fallbackIntent)
            }
        }

        // 2. Gemini API Key save
        val currentKey = GeminiAssistantApp.instance.geminiApiKey
        if (currentKey.isNotEmpty()) {
            binding.editApiKey.setText(currentKey)
        }

        binding.btnSaveApiKey.setOnClickListener {
            val key = binding.editApiKey.text?.toString()?.trim() ?: ""
            if (key.isNotEmpty()) {
                GeminiAssistantApp.instance.geminiApiKey = key
                Toast.makeText(this, getString(R.string.msg_api_key_saved), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.err_no_api_key), Toast.LENGTH_SHORT).show()
            }
        }

        // 3. Google Sign-In via Credential Manager
        binding.btnGoogleSignIn.setOnClickListener {
            if (GoogleAuthManager.isSignedIn) {
                GoogleAuthManager.signOut()
                updateUiState()
                Toast.makeText(this, "Signed out successfully.", Toast.LENGTH_SHORT).show()
            } else {
                lifecycleScope.launch {
                    val result = GoogleAuthManager.signInWithGoogle(this@MainActivity)
                    result.onSuccess { name ->
                        Toast.makeText(this@MainActivity, getString(R.string.msg_signed_in, name), Toast.LENGTH_SHORT).show()
                        updateUiState()
                    }.onFailure { error ->
                        Toast.makeText(this@MainActivity, "Sign-in notice: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        // 4. Test Assistant Session Directly
        binding.btnTestAssistant.setOnClickListener {
            val apiKey = GeminiAssistantApp.instance.geminiApiKey
            if (apiKey.isBlank()) {
                Toast.makeText(this, getString(R.string.err_no_api_key), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            // Trigger assist action intent
            val assistIntent = Intent(Intent.ACTION_ASSIST)
            try {
                startActivity(assistIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "Please select Gemini Assistant as your Default Assistant in Step 1 first.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateUiState() {
        if (GoogleAuthManager.isSignedIn) {
            binding.textAccountStatus.text = "Logged in as: ${GoogleAuthManager.currentUserInfo}"
            binding.btnGoogleSignIn.text = getString(R.string.btn_google_signout)
        } else {
            binding.textAccountStatus.text = "Not signed in"
            binding.btnGoogleSignIn.text = getString(R.string.btn_google_signin)
        }
    }
}
