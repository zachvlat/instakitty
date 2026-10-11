package com.zachvlat.instakitty

import com.zachvlat.instakitty.util.InstagramLinkParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstagramLinkParserTest {

    @Test
    fun viewProfileUrl() {
        assertEquals("user/nasa", InstagramLinkParser.routeFromUrl("https://www.instagram.com/nasa/?igsh=abc"))
        assertEquals("user/nasa", InstagramLinkParser.routeFromUrl("https://instagram.com/nasa"))
    }

    @Test
    fun viewPostAndReelUrl() {
        assertEquals("post/ABC123", InstagramLinkParser.routeFromUrl("https://www.instagram.com/p/ABC123/"))
        assertEquals("post/XYZ", InstagramLinkParser.routeFromUrl("https://www.instagram.com/reel/XYZ?igsh=q"))
    }

    @Test
    fun viewStoriesUrl() {
        assertEquals("user/nasa", InstagramLinkParser.routeFromUrl("https://www.instagram.com/stories/nasa/1234567890/"))
    }

    @Test
    fun ignoresNonInstagramAndUnknown() {
        assertNull(InstagramLinkParser.routeFromUrl("https://example.com/nasa"))
        assertNull(InstagramLinkParser.routeFromUrl("https://www.instagram.com/"))
        assertNull(InstagramLinkParser.routeFromUrl(null))
    }

    @Test
    fun sharedProfileTextWithProse() {
        assertEquals(
            "user/nasa",
            InstagramLinkParser.routeFromSharedText("Check out NASA on Instagram: https://www.instagram.com/nasa?igsh=abc")
        )
    }

    @Test
    fun sharedProfileTextNoScheme() {
        assertEquals("user/nasa", InstagramLinkParser.routeFromSharedText("instagram.com/nasa"))
    }

    @Test
    fun sharedPostTextWithTrailingPunctuation() {
        assertEquals(
            "post/ABC123",
            InstagramLinkParser.routeFromSharedText("Look at this (https://www.instagram.com/p/ABC123/).")
        )
    }

    @Test
    fun sharedTextWithoutInstagramLink() {
        assertNull(InstagramLinkParser.routeFromSharedText("just some text with no link"))
        assertNull(InstagramLinkParser.routeFromSharedText(null))
    }
}
