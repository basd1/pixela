package dev.bastian.pixela.domain

import kotlinx.coroutines.flow.Flow

/** Persistence of the most recently used colors (newest first). */
interface RecentColorsStore {
    val colors: Flow<List<Int>>
    suspend fun save(colors: List<Int>)
}

/** Keeps a newest-first list of distinct colors, capped at [limit]. */
fun List<Int>.withRecent(color: Int, limit: Int = RECENT_LIMIT): List<Int> =
    (listOf(color) + filter { it != color }).take(limit)

const val RECENT_LIMIT = 8
