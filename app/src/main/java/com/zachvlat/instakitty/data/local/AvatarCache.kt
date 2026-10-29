package com.zachvlat.instakitty.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Stores profile pictures of followed accounts on disk. Instagram CDN links expire after a
 * few days, so the image itself is kept rather than its URL; the Following page then shows
 * these files without hitting the network, however many accounts are followed.
 */
object AvatarCache {

    private val client by lazy { OkHttpClient() }

    fun fileFor(context: Context, username: String): File =
        File(File(context.filesDir, "avatars"), "${username.lowercase()}.jpg")

    /** Downloads [url] for [username]; returns the stored file's path, or null on failure. */
    suspend fun save(context: Context, username: String, url: String): String? =
        withContext(Dispatchers.IO) {
            try {
                client.newCall(Request.Builder().url(url).build()).execute().use { res ->
                    val body = res.body
                    if (!res.isSuccessful || body == null) return@withContext null
                    val file = fileFor(context, username)
                    file.parentFile?.mkdirs()
                    val tmp = File(file.path + ".tmp")
                    tmp.outputStream().use { body.byteStream().copyTo(it) }
                    if (!tmp.renameTo(file)) {
                        file.delete()
                        if (!tmp.renameTo(file)) return@withContext null
                    }
                    file.path
                }
            } catch (_: Exception) {
                null
            }
        }
}
