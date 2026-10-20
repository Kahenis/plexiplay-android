package tv.plexiplay.plex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DecisionReplyTest {
    @Test
    fun keepsHttpTranscodeKey() {
        val raw = """{"MediaContainer":{"Metadata":[{"key":"/library/metadata/1"}],"Transcode":[{"key":"/video/:/transcode/universal/session/abc/base/index.m3u8"}]}}"""
        assertEquals(
            "/video/:/transcode/universal/session/abc/base/index.m3u8",
            DecisionReply.transcodeKey(raw),
        )
    }

    @Test
    fun rejectsFilePath() {
        assertNull(DecisionReply.transcodeKey("""{"key":"file:///volume1/bonus.mp4"}"""))
    }
}
