package tv.plexiplay.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeliveryPlannerTest {
    @Test
    fun serverDirectWhenDeviceCan() {
        val choice = DeliveryPlanner.choose(Engine.SERVER, PlayerKind.EXO, "hevc", "aac", { true }, false)
        assertEquals(Delivery.DIRECT, choice.delivery)
    }

    @Test
    fun serverTranscodesOnlyWhenNeeded() {
        val choice = DeliveryPlanner.choose(Engine.SERVER, PlayerKind.EXO, "hevc", "truehd", { false }, false)
        assertEquals(Delivery.TRANSCODE, choice.delivery)
    }

    @Test
    fun mpvDoesNotTranscode() {
        val choice = DeliveryPlanner.choose(Engine.SERVER, PlayerKind.MPV, "hevc", "truehd", { false }, false)
        assertEquals(Delivery.DIRECT, choice.delivery)
    }

    @Test
    fun tvDirectWhenDeviceDecodes() {
        val choice = DeliveryPlanner.choose(Engine.TV, PlayerKind.EXO, "hevc", "aac", { it == "hevc" || it == "aac" }, false)
        assertEquals(Delivery.DIRECT, choice.delivery)
    }

    @Test
    fun tvFallsBackWhenPatentCodecMissing() {
        val choice = DeliveryPlanner.choose(Engine.TV, PlayerKind.EXO, "h264", "truehd", { it == "h264" }, true)
        assertEquals(Delivery.TRANSCODE, choice.delivery)
    }

    @Test
    fun directUrlKeepsToken() {
        val url = StreamUrlFactory.direct("http://192.168.1.2:32400", "/library/parts/9/file", "abc")
        assertTrue(url.startsWith("http://192.168.1.2:32400/library/parts/9/file?"))
        assertTrue(url.contains("X-Plex-Token=abc"))
    }
}
