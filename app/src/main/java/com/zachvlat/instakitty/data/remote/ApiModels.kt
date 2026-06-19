package com.zachvlat.instakitty.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── Errors ─────────────────────────────────────────────────

@Serializable
data class ApiError(
    @SerialName("has_errors") val hasErrors: Boolean = false,
    @SerialName("error_type") val errorType: String? = null,
    @SerialName("error_info") val errorInfo: ErrorInfo? = null
)

@Serializable
data class ErrorInfo(
    val message: String? = null,
    val blob: String? = null
)

// ─── Status / Info ──────────────────────────────────────────

@Serializable
data class ApiStatus(
    val redis: Boolean,
    val ratelimits: Map<String, String>? = null,
    @SerialName("request_counts") val requestCounts: Map<String, Int>? = null
)

@Serializable
data class ApiInfo(
    val about: String? = null,
    @SerialName("atom_enabled") val atomEnabled: Boolean = false
)

// ─── User ───────────────────────────────────────────────────

@Serializable
data class User(
    val username: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val id: String? = null,
    @SerialName("profile_picture") val profilePicture: String? = null,
    @SerialName("profile_pic_url") val profilePicUrl: String? = null,
    @SerialName("is_verified") val isVerified: Boolean? = null,
    @SerialName("follower_count") val followerCount: Int? = null,
    @SerialName("following_count") val followingCount: Int? = null,
    @SerialName("media_count") val mediaCount: Int? = null,
    val biography: String? = null,
    @SerialName("is_private") val isPrivate: Boolean? = null,
    @SerialName("is_business") val isBusiness: Boolean? = null,
    val pronouns: Map<String, String>? = null
)

// ─── Media ──────────────────────────────────────────────────

@Serializable
data class MediaItem(
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("video_url") val videoUrl: String? = null,
    @SerialName("video_thumbnail") val videoThumbnail: String? = null,
    @SerialName("alt_text") val altText: String? = null
)

// ─── Post ───────────────────────────────────────────────────

@Serializable
data class Post(
    val shortcode: String? = null,
    @SerialName("alt_text") val altText: String? = null,
    val timestamp: Long? = null,
    val id: String? = null,
    val user: User? = null,
    val likes: Int? = null,
    val caption: String? = null,
    @SerialName("video_url") val videoUrl: String? = null,
    @SerialName("video_thumbnail") val videoThumbnail: String? = null,
    @SerialName("view_count") val viewCount: Int? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    val images: List<MediaItem>? = null,
    @SerialName("comment_count") val commentCount: Int? = null,
    @SerialName("has_errors") val hasErrors: Boolean? = null,
    @SerialName("error_type") val errorType: String? = null,
    @SerialName("error_info") val errorInfo: ErrorInfo? = null
)

// ─── User Profile Response ──────────────────────────────────

@Serializable
data class UserProfileResponse(
    val posts: List<Post>? = null,
    @SerialName("user_info") val userInfo: User? = null,
    @SerialName("end_cursor") val endCursor: String? = null,
    @SerialName("has_errors") val hasErrors: Boolean? = null,
    @SerialName("error_type") val errorType: String? = null,
    @SerialName("error_info") val errorInfo: ErrorInfo? = null
)
