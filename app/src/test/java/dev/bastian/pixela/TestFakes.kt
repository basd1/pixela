package dev.bastian.pixela

import dev.bastian.pixela.domain.Document
import dev.bastian.pixela.domain.ImageExporter
import dev.bastian.pixela.domain.ImageImporter
import dev.bastian.pixela.domain.LoadedProject
import dev.bastian.pixela.domain.ProjectCodec
import dev.bastian.pixela.domain.ProjectRepository
import dev.bastian.pixela.domain.ProjectSnapshot
import dev.bastian.pixela.domain.ProjectSummary
import dev.bastian.pixela.domain.RecentColorsStore
import dev.bastian.pixela.ui.Strings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeStrings : Strings {
    override fun layerName(number: Int) = "Layer $number"
    override fun newProjectName(number: Int) = "Drawing $number"
    override fun copyOf(name: String) = "$name copy"
}

class FakeRecents : RecentColorsStore {
    val state = MutableStateFlow<List<Int>>(emptyList())
    override val colors: Flow<List<Int>> = state
    override suspend fun save(colors: List<Int>) {
        state.value = colors
    }
}

/** An in-memory repository that keeps the encoded JSON, like the real one keeps files. */
class FakeProjectRepository(size: Int = 16) : ProjectRepository {
    val json = mutableMapOf<String, String>()
    val names = mutableMapOf<String, String>()
    val saves = mutableListOf<String>()
    private val list = MutableStateFlow<List<ProjectSummary>>(emptyList())
    override val projects: Flow<List<ProjectSummary>> = list

    init {
        json["p1"] = ProjectCodec.encode(Document.blank(size, "Layer 1"))
        names["p1"] = "Test"
    }

    override suspend fun create(name: String, size: Int, firstLayerName: String): String {
        val id = "p${json.size + 1}"
        json[id] = ProjectCodec.encode(Document.blank(size, firstLayerName))
        names[id] = name
        return id
    }

    override suspend fun load(id: String): LoadedProject? =
        json[id]?.let { text -> ProjectCodec.decode(text)?.let { LoadedProject(names.getValue(id), it) } }

    override suspend fun save(id: String, snapshot: ProjectSnapshot) {
        json[id] = snapshot.json
        saves += id
    }

    override suspend fun rename(id: String, name: String) {
        names[id] = name
    }

    override suspend fun duplicate(id: String, newName: String): String? = null

    override suspend fun delete(id: String) {
        json.remove(id)
    }
}

class FakeExporter : ImageExporter {
    var fail = false
    val calls = mutableListOf<String>()

    override suspend fun saveToGallery(name: String, pixels: IntArray, width: Int, height: Int, scale: Int): Result<String> {
        calls += "save:$name:$scale:${width * scale}x${height * scale}"
        return if (fail) Result.failure(java.io.IOException("disk full")) else Result.success("content://gallery/1")
    }

    override suspend fun prepareShare(name: String, pixels: IntArray, width: Int, height: Int, scale: Int): Result<String> {
        calls += "share:$name:$scale"
        return Result.success("content://share/1")
    }
}

class FakeImporter(private val result: Result<IntArray>) : ImageImporter {
    override suspend fun import(uri: String, width: Int, height: Int): Result<IntArray> = result
}
