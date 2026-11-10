package dev.bastian.pixela.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class RecentColorsTest {
    @Test
    fun `newest color goes first and duplicates move to the front`() {
        val list = emptyList<Int>().withRecent(1).withRecent(2).withRecent(3).withRecent(1)
        assertEquals(listOf(1, 3, 2), list)
    }

    @Test
    fun `list is capped at the limit`() {
        var list = emptyList<Int>()
        repeat(20) { list = list.withRecent(it) }
        assertEquals(RECENT_LIMIT, list.size)
        assertEquals(19, list.first())
    }
}
