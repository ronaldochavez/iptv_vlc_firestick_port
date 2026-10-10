package com.tvabierta.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {

    @Test
    fun parsesWorldIpTvEntries() {
        val text = """
            #EXTM3U
            #EXTINF:-1 tvg-logo="https://i.imgur.com/Og17U9N.png" group-title="Mexico",ADN 40 (720p)
            https://mdstrm.com/live-stream-playlist/60b578b060947317de7b57ac.m3u8
            #EXTINF:-1 tvg-logo="https://i.imgur.com/snIU1UA.jpg" group-title="Mexico",AMX Noticias (720p) [Not 24/7]
            https://example.com/amx.m3u8
        """.trimIndent()

        val channels = M3uParser.parse(text, origin = "🇲🇽 México")

        assertEquals(2, channels.size)
        with(channels[0]) {
            assertEquals("ADN 40", name)
            assertEquals("https://mdstrm.com/live-stream-playlist/60b578b060947317de7b57ac.m3u8", url)
            assertEquals("https://i.imgur.com/Og17U9N.png", logo)
            assertEquals("Mexico", group)
            assertEquals("720p", quality)
            assertFalse(notAlways)
            assertEquals("🇲🇽 México", origin)
        }
        with(channels[1]) {
            assertEquals("AMX Noticias", name)
            assertTrue(notAlways)
        }
    }

    @Test
    fun keepsCommasInsideAttributesOutOfTheTitle() {
        val text = """
            #EXTINF:-1 tvg-id="x" group-title="News, Sports" tvg-logo="",Canal 5 (Mexico) [Geo-blocked]
            http://host/stream.m3u8
        """.trimIndent()

        val channel = M3uParser.parse(text).single()

        assertEquals("Canal 5 (Mexico)", channel.name)
        assertEquals("News, Sports", channel.group)
        assertNull(channel.logo)
        assertTrue(channel.geoBlocked)
    }

    @Test
    fun turnsVlcOptionsAndHeadersIntoMediaOptions() {
        val text = """
            #EXTM3U
            #EXTINF:-1 http-referrer="https://ref.example/",Uno
            #EXTVLCOPT:http-user-agent=Mozilla/5.0
            http://host/uno.m3u8
            #EXTINF:-1,Dos
            http://host/dos.m3u8|User-Agent=Kodi&Referer=https://site/
        """.trimIndent()

        val (uno, dos) = M3uParser.parse(text)

        assertEquals(listOf(":http-user-agent=Mozilla/5.0", ":http-referrer=https://ref.example/"), uno.options)
        assertEquals("http://host/dos.m3u8", dos.url)
        assertEquals(listOf(":http-user-agent=Kodi", ":http-referrer=https://site/"), dos.options)
    }

    @Test
    fun namesBareUrlsAfterTheirFile() {
        val channels = M3uParser.parse("http://host/live/canal-uno.m3u8?token=1\n")

        assertEquals("canal-uno.m3u8", channels.single().name)
    }
}
