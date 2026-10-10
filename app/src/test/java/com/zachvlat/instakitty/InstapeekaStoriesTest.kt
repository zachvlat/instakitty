package com.zachvlat.instakitty

import com.zachvlat.instakitty.data.remote.InstaHighlightItemsResponse
import com.zachvlat.instakitty.data.remote.InstaProfileResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstapeekaStoriesTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun profileResponseDecodesStories() {
        val response = json.decodeFromString<InstaProfileResponse>(
            """
            {
              "profile": { "username": "nasa", "displayName": "NASA" },
              "stories": [
                {
                  "id": "1",
                  "mediaType": "image",
                  "imageUrl": "https://cdn/one.jpg",
                  "timestamp": 1791561444
                },
                {
                  "id": "2",
                  "mediaType": "video",
                  "imageUrl": "https://cdn/two.jpg",
                  "videoUrl": "https://cdn/two.mp4",
                  "timestamp": 1791561500
                }
              ]
            }
            """.trimIndent()
        )

        val stories = response.stories
        assertEquals(2, stories.size)
        assertEquals("https://cdn/one.jpg", stories[0].imageUrl)
        assertNull(stories[0].videoUrl)
        assertEquals("video", stories[1].mediaType)
        assertEquals("https://cdn/two.mp4", stories[1].videoUrl)
        assertEquals(1791561500L, stories[1].timestamp)
    }

    @Test
    fun highlightItemsResponseDecodesStories() {
        val response = json.decodeFromString<InstaHighlightItemsResponse>(
            """
            {
              "stories": [
                {
                  "id": "3975089864169193671",
                  "mediaType": "video",
                  "imageUrl": "https://cdn/cover.jpg",
                  "videoUrl": "https://cdn/clip.mp4",
                  "timestamp": 1790000000
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(1, response.stories.size)
        assertEquals("https://cdn/clip.mp4", response.stories.single().videoUrl)
    }
}
