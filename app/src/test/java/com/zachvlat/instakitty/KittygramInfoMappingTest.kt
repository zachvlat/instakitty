package com.zachvlat.instakitty

import com.zachvlat.instakitty.data.remote.UserProfileGridResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class KittygramInfoMappingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun gridInfoCountKeysMapToUser() {
        val response = json.decodeFromString<UserProfileGridResponse>(
            """
            {
              "info": {
                "username": "annacramling",
                "display_name": "Anna Cramling",
                "id": "123456",
                "followers": 900000,
                "following": 120,
                "media_count": 850,
                "profile_picture": "https://cdn/pic.jpg",
                "biography": "Chess player",
                "is_private": false,
                "is_verified": true
              },
              "grid": { "items": [], "cursor": null },
              "status": "ok"
            }
            """.trimIndent()
        )

        val info = response.info
        assertNotNull(info)
        assertEquals(900000, info!!.followerCount)
        assertEquals(120, info.followingCount)
        assertEquals(850, info.mediaCount)
        assertEquals("Anna Cramling", info.displayName)
        assertEquals("Chess player", info.biography)
    }

    @Test
    fun legacyUserCountKeysStillMap() {
        val user = json.decodeFromString<com.zachvlat.instakitty.data.remote.User>(
            """
            {
              "username": "annacramling",
              "follower_count": 42,
              "following_count": 7,
              "media_count": 3
            }
            """.trimIndent()
        )

        assertEquals(42, user.followerCount)
        assertEquals(7, user.followingCount)
        assertEquals(3, user.mediaCount)
    }
}
