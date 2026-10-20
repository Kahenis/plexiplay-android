package tv.plexiplay.plex

import kotlin.test.Test
import kotlin.test.assertEquals

class PrefValueTest {
    @Test
    fun readsJsonAfterALongSummary() {
        val summary = "x".repeat(2000)
        val raw = """{"id":"CinemaTrailersPrerollID","summary":"$summary","value":"/media/bonus/a.mp4;/media/bonus/b.mp4"}"""
        assertEquals("/media/bonus/a.mp4;/media/bonus/b.mp4", PrefValue.extract(raw, "CinemaTrailersPrerollID"))
    }

    @Test
    fun readsXml() {
        val raw = """<Setting id="CinemaTrailersPrerollID" label="Bonus" value="/media/bonus/a.mp4,/media/bonus/b.mp4" type="text"/>"""
        assertEquals("/media/bonus/a.mp4,/media/bonus/b.mp4", PrefValue.extract(raw, "CinemaTrailersPrerollID"))
    }
}
