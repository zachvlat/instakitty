package com.zachvlat.instakitty.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class InstaProfileResponse(
    val profile: InstaProfile? = null,
    val stories: List<InstaStory> = emptyList()
)

@Serializable
data class InstaProfile(
    val username: String? = null,
    val displayName: String? = null,
    val isPublic: Boolean? = null,
    val isVerified: Boolean? = null,
    val profilePictureUrl: String? = null
)

@Serializable
data class InstaPostsResponse(
    val hasMore: Boolean? = null,
    val posts: List<InstaPost> = emptyList()
)

@Serializable
data class InstaPost(
    val id: String? = null,
    val code: String? = null,
    val caption: String? = null,
    val likeCount: Int? = null,
    val commentCount: Int? = null,
    val takenAt: Long? = null,
    val mediaType: String? = null,
    val thumbnailUrl: String? = null,
    val videoUrl: String? = null,
    val carousel: List<InstaCarouselItem> = emptyList()
)

@Serializable
data class InstaCarouselItem(
    val id: String? = null,
    val mediaType: String? = null,
    val thumbnailUrl: String? = null,
    val videoUrl: String? = null
)

@Serializable
data class InstaRelatedResponse(
    val profiles: List<InstaRelatedProfile> = emptyList()
)

@Serializable
data class InstaRelatedProfile(
    val username: String? = null,
    val fullName: String? = null,
    val isVerified: Boolean? = null,
    val profilePicUrl: String? = null
)

@Serializable
data class InstaHighlightsResponse(
    val highlights: List<InstaHighlight> = emptyList()
)

@Serializable
data class InstaHighlight(
    val id: String? = null,
    val title: String? = null,
    val coverUrl: String? = null,
    val mediaCount: Int? = null
)

@Serializable
data class InstaHighlightItemsResponse(
    val stories: List<InstaStory> = emptyList()
)

@Serializable
data class InstaStory(
    val id: String? = null,
    val mediaType: String? = null,
    val imageUrl: String? = null,
    val videoUrl: String? = null,
    val timestamp: Long? = null
)
