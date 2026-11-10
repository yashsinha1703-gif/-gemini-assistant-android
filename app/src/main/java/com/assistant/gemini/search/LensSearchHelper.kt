package com.assistant.gemini.search

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object LensSearchHelper {

    /**
     * Saves the given bitmap to a temporary cache file and returns its content URI.
     */
    fun saveBitmapToCache(context: Context, bitmap: Bitmap): Uri? {
        return try {
            val cacheDir = File(context.cacheDir, "screens").apply { mkdirs() }
            val file = File(cacheDir, "lens_query_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Launches Google Lens or Google Reverse Image Search with the given cropped screenshot.
     */
    fun launchVisualSearch(context: Context, bitmap: Bitmap, queryFallback: String? = null) {
        val imageUri = saveBitmapToCache(context, bitmap)

        if (imageUri != null) {
            // Attempt 1: Direct Google Lens application package
            val lensIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                setPackage("com.google.ar.lens")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (lensIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(lensIntent)
                return
            }

            // Attempt 2: Google App (Search Box) Lens handler
            val googleAppIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                setPackage("com.google.android.googlequicksearchbox")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (googleAppIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(googleAppIntent)
                return
            }
        }

        // Attempt 3: If fallback text query is available, launch Google Search Custom Tab
        if (!queryFallback.isNullOrBlank()) {
            val searchUri = Uri.parse("https://www.google.com/search?q=${Uri.encode(queryFallback)}")
            val customTabsIntent = CustomTabsIntent.Builder().build()
            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            customTabsIntent.launchUrl(context, searchUri)
            return
        }

        // Attempt 4: Fallback to Google Lens Web
        try {
            val lensWeb = Uri.parse("https://lens.google.com/")
            val customTabsIntent = CustomTabsIntent.Builder().build()
            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            customTabsIntent.launchUrl(context, lensWeb)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not launch Google Lens", Toast.LENGTH_SHORT).show()
        }
    }
}
