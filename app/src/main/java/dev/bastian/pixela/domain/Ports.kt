package dev.bastian.pixela.domain

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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

/** Dispatchers are injected so logic that switches threads can be tested. */
class AppDispatchers(
    val io: CoroutineDispatcher = Dispatchers.IO,
    val default: CoroutineDispatcher = Dispatchers.Default,
)

data class ProjectSummary(
    val id: String,
    val name: String,
    val size: Int,
    val modifiedAt: Long,
    val thumbnailPath: String,
)

data class LoadedProject(val name: String, val document: Document)

/** What gets written on save: the encoded project plus the composition for the thumbnail. */
class ProjectSnapshot(val json: String, val preview: IntArray, val width: Int, val height: Int) {
    companion object {
        fun of(document: Document) = ProjectSnapshot(
            json = ProjectCodec.encode(document),
            preview = composite(document),
            width = document.width,
            height = document.height,
        )
    }
}

interface ProjectRepository {
    val projects: Flow<List<ProjectSummary>>

    /** Creates an empty project and returns its id. */
    suspend fun create(name: String, size: Int, firstLayerName: String): String

    /** Null when the project does not exist or its file is damaged. */
    suspend fun load(id: String): LoadedProject?

    suspend fun save(id: String, snapshot: ProjectSnapshot)
    suspend fun rename(id: String, name: String)

    /** Copies a project under [newName]; returns the new id, or null if the original is gone. */
    suspend fun duplicate(id: String, newName: String): String?
    suspend fun delete(id: String)
}

/** Writes the small PNG shown in the gallery. */
fun interface ThumbnailWriter {
    fun write(path: String, pixels: IntArray, width: Int, height: Int)
}

/** Exports and shares the image. Failures are results, never thrown to the UI. */
interface ImageExporter {
    /** Saves a PNG to the device gallery; the result is the content Uri as text. */
    suspend fun saveToGallery(name: String, pixels: IntArray, width: Int, height: Int, scale: Int): Result<String>

    /** Writes a PNG that can be shared and returns its content Uri as text. */
    suspend fun prepareShare(name: String, pixels: IntArray, width: Int, height: Int, scale: Int): Result<String>
}

/** Reads an image chosen by the user and reduces it to [width] x [height] pixels. */
fun interface ImageImporter {
    suspend fun import(uri: String, width: Int, height: Int): Result<IntArray>
}
