package com.zachvlat.instakitty.data.remote

internal fun InstaProfile.toUser(): User = User(
    username = username,
    displayName = displayName,
    profilePicture = profilePictureUrl,
    profilePicUrl = profilePictureUrl,
    isVerified = isVerified,
    isPrivate = isPublic == false
)

internal fun InstaRelatedProfile.toUser(): User = User(
    username = username,
    displayName = fullName,
    profilePicture = profilePicUrl,
    profilePicUrl = profilePicUrl,
    isVerified = isVerified
)

internal fun InstaPost.toPost(): Post {
    val carouselMedia = carousel.map { item ->
        MediaItem(
            imageUrl = item.thumbnailUrl.takeUnless { item.mediaType == "video" },
            videoUrl = item.videoUrl,
            videoThumbnail = item.thumbnailUrl
        )
    }
    val isVideo = mediaType == "video"
    return Post(
        shortcode = code,
        id = id,
        caption = caption,
        likes = likeCount,
        commentCount = commentCount,
        timestamp = takenAt,
        kind = if (isVideo) "reel" else mediaType,
        thumbnail = thumbnailUrl,
        images = carouselMedia.ifEmpty { null },
        videoUrl = videoUrl.takeIf { carousel.isEmpty() },
        videoThumbnail = thumbnailUrl,
        imageUrl = thumbnailUrl.takeIf { !(carousel.isEmpty() && isVideo) }
    )
}
