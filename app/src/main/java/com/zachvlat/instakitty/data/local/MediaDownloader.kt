package com.zachvlat.instakitty.data.local

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Saves post media (image or video) to the public Downloads folder. No storage permission is
 * required: MediaStore handles it on Android 10+, and older versions fall back to the app's
 * external Downloads directory.
 */
object MediaDownloader {

    private val client by lazy { OkHttpClient() }

    /** Returns a human-readable location on success. [baseName] has no extension. */
    suspend fun download(
        context: Context,
        url: String,
        baseName: String,
        fallbackIsVideo: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                val body = response.body
                if (!response.isSuccessful || body == null) {
                    return@withContext Result.failure(
                        IOException("HTTP ${response.code}")
                    )
                }
                val mimeType = body.contentType()
                    ?.toString()
                    ?.substringBefore(';')
                    ?.trim()
                    .takeUnless { it.isNullOrBlank() }
                    ?: guessMimeType(url, fallbackIsVideo)
                val extension = extensionFor(mimeType, url, fallbackIsVideo)
                val fileName = "$baseName.$extension"

                val location = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveToMediaStore(context, body.byteStream(), fileName, mimeType)
                } else {
                    saveToAppDownloads(context, body.byteStream(), fileName)
                }
                Result.success(location)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun saveToMediaStore(
        context: Context,
        input: java.io.InputStream,
        fileName: String,
        mimeType: String
    ): String {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/InstaKitty"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: throw IOException("Could not create download entry")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                input.use { it.copyTo(output) }
            } ?: throw IOException("Could not open download entry")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return "Downloads/InstaKitty/$fileName"
    }

    @Suppress("DEPRECATION")
    private fun saveToAppDownloads(
        context: Context,
        input: java.io.InputStream,
        fileName: String
    ): String {
        val root = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val dir = File(root, "InstaKitty").apply { mkdirs() }
        val file = uniqueFile(dir, fileName)
        input.use { source -> file.outputStream().use { source.copyTo(it) } }
        return file.absolutePath
    }

    private fun uniqueFile(dir: File, fileName: String): File {
        var candidate = File(dir, fileName)
        if (!candidate.exists()) return candidate
        val dot = fileName.lastIndexOf('.')
        val stem = if (dot > 0) fileName.substring(0, dot) else fileName
        val suffix = if (dot > 0) fileName.substring(dot) else ""
        var index = 1
        while (candidate.exists()) {
            candidate = File(dir, "$stem ($index)$suffix")
            index += 1
        }
        return candidate
    }

    private fun guessMimeType(url: String, isVideo: Boolean): String = when (urlExtension(url)) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        else -> if (isVideo) "video/mp4" else "image/jpeg"
    }

    private fun extensionFor(mimeType: String, url: String, isVideo: Boolean): String {
        val fromUrl = urlExtension(url)
        if (fromUrl in KNOWN_EXTENSIONS) return fromUrl
        return when {
            mimeType == "image/png" -> "png"
            mimeType == "image/webp" -> "webp"
            mimeType == "image/gif" -> "gif"
            mimeType == "video/mp4" -> "mp4"
            mimeType == "video/webm" -> "webm"
            mimeType == "video/quicktime" -> "mov"
            mimeType.startsWith("video/") || isVideo -> "mp4"
            else -> "jpg"
        }
    }

    private fun urlExtension(url: String): String =
        url.substringBefore('?')
            .substringAfterLast('.', "")
            .lowercase()

    private val KNOWN_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "mp4", "webm", "mov")
}
