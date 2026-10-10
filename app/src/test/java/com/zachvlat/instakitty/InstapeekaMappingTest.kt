package com.zachvlat.instakitty

import com.zachvlat.instakitty.data.remote.InstaProfileResponse
import com.zachvlat.instakitty.data.remote.InstaPostsResponse
import com.zachvlat.instakitty.data.remote.toPost
import com.zachvlat.instakitty.data.remote.toUser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstapeekaMappingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun profileMapsToUser() {
        val response = json.decodeFromString<InstaProfileResponse>(
            """
            {
              "profile": {
                "username": "marisa.katsa",
                "displayName": "Marisa",
                "isPublic": false,
                "isVerified": true,
                "profilePictureUrl": "https://cdn/pic.jpg"
              }
            }
            """.trimIndent()
        )

        val user = response.profile!!.toUser()
        assertEquals("marisa.katsa", user.username)
        assertEquals("Marisa", user.displayName)
        assertTrue(user.isPrivate == true)
        assertTrue(user.isVerified == true)
        assertEquals("https://cdn/pic.jpg", user.profilePicture)
        assertEquals("https://cdn/pic.jpg", user.profilePicUrl)
    }

    @Test
    fun imagePostMapsToPost() {
        val response = json.decodeFromString<InstaPostsResponse>(
            """
            {
              "hasMore": true,
              "posts": [
                {
                  "id": "3952839284159437201",
                  "code": "DbbTmLKiFmR",
                  "caption": "A little more magic",
                  "likeCount": 162,
                  "commentCount": 10,
                  "takenAt": 1785435184,
                  "mediaType": "image",
                  "thumbnailUrl": "https://cdn/cover.jpg"
                }
              ]
            }
            """.trimIndent()
        )

        val post = response.posts.single().toPost()
        assertEquals("DbbTmLKiFmR", post.shortcode)
        assertEquals(1785435184L, post.timestamp)
        assertEquals(162, post.likes)
        assertEquals(10, post.commentCount)
        assertEquals("https://cdn/cover.jpg", post.imageUrl)
        assertEquals("https://cdn/cover.jpg", post.thumbnail)
        assertNull(post.videoUrl)
        assertNull(post.images)
    }

    @Test
    fun videoPostMapsToPost() {
        val response = json.decodeFromString<InstaPostsResponse>(
            """
            {
              "hasMore": false,
              "posts": [
                {
                  "id": "3949641645995131161",
                  "code": "DbP8iajoQEZ",
                  "likeCount": 60,
                  "commentCount": 1,
                  "takenAt": 1785054277,
                  "mediaType": "video",
                  "thumbnailUrl": "https://cdn/cover.jpg",
                  "videoUrl": "https://cdn/video.mp4"
                }
              ]
            }
            """.trimIndent()
        )

        val post = response.posts.single().toPost()
        assertEquals("https://cdn/video.mp4", post.videoUrl)
        assertEquals("https://cdn/cover.jpg", post.videoThumbnail)
        assertNull(post.imageUrl)
        assertNull(post.images)
        assertEquals("reel", post.kind)
    }

    @Test
    fun carouselPostMapsChildrenToMediaItems() {
        val response = json.decodeFromString<InstaPostsResponse>(
            """
            {
              "hasMore": false,
              "posts": [
                {
                  "id": "3948487996093272477",
                  "code": "DbL2OmXCJGd",
                  "mediaType": "carousel",
                  "thumbnailUrl": "https://cdn/first.jpg",
                  "carousel": [
                    { "id": "1", "mediaType": "image", "thumbnailUrl": "https://cdn/one.jpg" },
                    { "id": "2", "mediaType": "video", "thumbnailUrl": "https://cdn/two.jpg", "videoUrl": "https://cdn/two.mp4" }
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        val post = response.posts.single().toPost()
        val media = post.images!!
        assertEquals(2, media.size)
        assertEquals("https://cdn/one.jpg", media[0].imageUrl)
        assertNull(media[0].videoUrl)
        assertNull(media[1].imageUrl)
        assertEquals("https://cdn/two.mp4", media[1].videoUrl)
        // Single-post fields stay on the post itself, not the carousel video.
        assertEquals("https://cdn/first.jpg", post.imageUrl)
        assertNull(post.videoUrl)
    }
}
