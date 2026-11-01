package com.zachvlat.instakitty.data.local

import com.zachvlat.instakitty.data.remote.Post
import kotlinx.serialization.Serializable

/**
 * Self-contained backup of the local state. Avatars are embedded as base64 so a restore does
 * not have to hit Instagram's CDN again (its URLs expire quickly anyway). Saved posts keep
 * their metadata and media URLs, but the image/video files themselves are not included.
 */
@Serializable
data class ExportBundle(
    val version: Int = 2,
    val exportedAt: Long = 0L,
    val following: List<String> = emptyList(),
    /** username -> base64 encoded JPEG bytes */
    val avatars: Map<String, String> = emptyMap(),
    val savedPosts: List<Post> = emptyList(),
    /** Preserved even when the metadata for a saved post could not be loaded. */
    val savedShortcodes: List<String> = emptyList()
)
