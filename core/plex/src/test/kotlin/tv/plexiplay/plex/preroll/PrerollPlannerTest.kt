package tv.plexiplay.plex.preroll

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrerollPlannerTest {
    @Test
    fun emptyIsNull() {
        assertNull(PrerollPlanner.parse(null))
        assertNull(PrerollPlanner.parse("  "))
    }

    @Test
    fun commaPlaysAllInOrder() {
        val spec = PrerollPlanner.parse("11,22,33")!!
        assertEquals(PrerollMode.SEQUENTIAL, spec.mode)
        assertEquals(listOf("11", "22", "33"), PrerollPlanner.select(spec))
    }

    @Test
    fun semicolonPicksOne() {
        val spec = PrerollPlanner.parse("11;22;33")!!
        assertEquals(PrerollMode.RANDOM, spec.mode)
        assertEquals(listOf("22"), PrerollPlanner.select(spec) { 1 })
    }

    @Test
    fun mixedStages() {
        val spec = PrerollPlanner.parse("11;12,21")!!
        assertEquals(PrerollMode.STAGED, spec.mode)
        assertEquals(listOf("12", "21"), PrerollPlanner.select(spec) { 1 })
    }

    @Test
    fun queuePutsPrerollBeforeTrailersAndFeature() {
        val queue = PrerollPlanner.queue(listOf("p"), listOf("t1", "t2"), "film", 1)
        assertEquals(listOf(ClipKind.PREROLL, ClipKind.TRAILER, ClipKind.FEATURE), queue.map { it.kind })
        assertEquals("film", queue.last().sourceId)
    }

    @Test
    fun audienceFollowsServer() {
        assertEquals(PrerollAudience.ALL, PrerollPlanner.audience("0"))
        assertEquals(PrerollAudience.ALL, PrerollPlanner.audience("false"))
        assertEquals(PrerollAudience.UNWATCHED, PrerollPlanner.audience("1"))
        assertEquals(PrerollAudience.ALL, PrerollPlanner.audience(null))
        assertTrue(PrerollPlanner.isLocalFile("/media/bonus/intro.mp4"))
        assertEquals(false, PrerollPlanner.isLocalFile("12345"))
    }

    @Test
    fun startedFilmSkipsBonus() {
        assertFalse(PrerollPlanner.allowed(PrerollAudience.ALL, 0, 120_000))
        assertFalse(PrerollPlanner.allowed(PrerollAudience.UNWATCHED, 0, 120_000))
    }

    @Test
    fun unwatchedOnlySkipsSeenFilms() {
        assertFalse(PrerollPlanner.allowed(PrerollAudience.UNWATCHED, 1, 0))
        assertTrue(PrerollPlanner.allowed(PrerollAudience.UNWATCHED, 0, 0))
        assertTrue(PrerollPlanner.allowed(PrerollAudience.ALL, 1, 0))
    }

    @Test
    fun zeroTrailersDropsThem() {
        val queue = PrerollPlanner.queue(listOf("p"), listOf("t1"), "film", 0)
        assertEquals(listOf(ClipKind.PREROLL, ClipKind.FEATURE), queue.map { it.kind })
    }
}
