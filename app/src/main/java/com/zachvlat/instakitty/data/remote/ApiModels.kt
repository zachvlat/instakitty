package com.zachvlat.instakitty.data.remote

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder

@Serializable
data class ExploreItem(
    val shortcode: String? = null,
    val caption: String? = null,
    @SerialName("play_count") val playCount: Long? = null,
    val user: ExploreUser? = null,
    val thumbnail: String? = null,
    @SerialName("video_url") val videoUrl: String? = null
)

@Serializable
data class ExploreUser(
    val username: String? = null,
    val id: String? = null,
    @SerialName("is_verified") val isVerified: Boolean? = null,
    @SerialName("profile_picture") val profilePicture: String? = null
)

@Serializable
data class ExploreResponse(
    val status: String? = null,
    val items: List<ExploreItem> = emptyList(),
    @SerialName("end_cursor") val endCursor: String? = null
)

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
    @SerialName("is_business") val isBusiness: Boolean? = null
)

@Serializable
data class MediaItem(
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("video_url") val videoUrl: String? = null,
    @SerialName("video_thumbnail") val videoThumbnail: String? = null,
    @SerialName("alt_text") val altText: String? = null
)

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

object EmptyObjectAsSearchUserListSerializer : KSerializer<List<SearchUser>> {
    private val delegate = ListSerializer(SearchUser.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): List<SearchUser> {
        val input = decoder as JsonDecoder
        val element = input.decodeJsonElement()
        return if (element is JsonArray) {
            input.json.decodeFromJsonElement(delegate, element)
        } else {
            emptyList()
        }
    }

    override fun serialize(encoder: Encoder, value: List<SearchUser>) {
        encoder.encodeSerializableValue(delegate, value)
    }
}

@Serializable
data class SearchResponse(
    @SerialName("search_source") val searchSource: String? = null,
    @Serializable(with = EmptyObjectAsSearchUserListSerializer::class)
    val users: List<SearchUser> = emptyList(),
    @SerialName("has_errors") val hasErrors: Boolean? = null,
    @SerialName("error_type") val errorType: String? = null,
    @SerialName("error_info") val errorInfo: ErrorInfo? = null
)

@Serializable
data class SearchUser(
    val username: String? = null,
    @SerialName("profile_picture") val profilePicture: String? = null,
    val id: String? = null,
    @SerialName("is_verified") val isVerified: Boolean? = null
)

@Serializable
data class UserProfileResponse(
    val posts: List<Post>? = null,
    @SerialName("user_info") val userInfo: User? = null,
    @SerialName("end_cursor") val endCursor: String? = null,
    @SerialName("has_errors") val hasErrors: Boolean? = null,
    @SerialName("error_type") val errorType: String? = null,
    @SerialName("error_info") val errorInfo: ErrorInfo? = null
)

@Serializable
data class Comment(
    val text: String? = null,
    val timestamp: Long? = null,
    val likes: Int? = null,
    @SerialName("gif_media") val gifMedia: String? = null,
    val user: User? = null
)

@Serializable
data class CommentsResponse(
    val items: List<Comment> = emptyList(),
    @SerialName("end_cursor") val endCursor: String? = null
)
