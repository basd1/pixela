package dev.bastian.pixela.data.repo

import dev.bastian.pixela.data.db.ProjectDao
import dev.bastian.pixela.data.db.ProjectEntity
import dev.bastian.pixela.data.db.toSummary
import dev.bastian.pixela.domain.AppDispatchers
import dev.bastian.pixela.domain.Clock
import dev.bastian.pixela.domain.Document
import dev.bastian.pixela.domain.LoadedProject
import dev.bastian.pixela.domain.ProjectCodec
import dev.bastian.pixela.domain.ProjectRepository
import dev.bastian.pixela.domain.ProjectSnapshot
import dev.bastian.pixela.domain.ProjectSummary
import dev.bastian.pixela.domain.ThumbnailWriter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Projects as JSON files in app storage plus a Room index; thumbnails sit next to them. */
class RoomProjectRepository(
    private val dao: ProjectDao,
    private val directory: File,
    private val thumbnails: ThumbnailWriter,
    private val clock: Clock,
    private val dispatchers: AppDispatchers,
    private val newId: () -> String,
) : ProjectRepository {

    override val projects: Flow<List<ProjectSummary>> = dao.observeAll().map { rows -> rows.map { it.toSummary() } }

    private fun projectFile(id: String) = File(directory, "$id.json")
    private fun thumbnailFile(id: String) = File(directory, "$id.png")

    override suspend fun create(name: String, size: Int, firstLayerName: String): String {
        val id = newId()
        save(id, ProjectSnapshot.of(Document.blank(size, firstLayerName)), name)
        return id
    }

    override suspend fun load(id: String): LoadedProject? = withContext(dispatchers.io) {
        val row = dao.get(id) ?: return@withContext null
        val text = try {
            projectFile(id).readText()
        } catch (e: IOException) {
            return@withContext null
        }
        ProjectCodec.decode(text)?.let { LoadedProject(row.name, it) }
    }

    override suspend fun save(id: String, snapshot: ProjectSnapshot) {
        val name = dao.get(id)?.name ?: id
        save(id, snapshot, name)
    }

    private suspend fun save(id: String, snapshot: ProjectSnapshot, name: String) = withContext(dispatchers.io) {
        directory.mkdirs()
        val target = projectFile(id)
        // Write to a temporary file and rename, so a crash mid-save never leaves a half-written project.
        val temp = File(directory, "$id.json.tmp")
        temp.writeText(snapshot.json)
        if (!temp.renameTo(target)) {
            target.delete()
            if (!temp.renameTo(target)) throw IOException("Could not replace ${target.name}")
        }
        thumbnails.write(thumbnailFile(id).path, snapshot.preview, snapshot.width, snapshot.height)
        dao.upsert(
            ProjectEntity(id, name, snapshot.width, clock.now(), target.path, thumbnailFile(id).path),
        )
    }

    override suspend fun rename(id: String, name: String) {
        val row = dao.get(id) ?: return
        dao.upsert(row.copy(name = name, modifiedAt = clock.now()))
    }

    override suspend fun duplicate(id: String, newName: String): String? = withContext(dispatchers.io) {
        val row = dao.get(id) ?: return@withContext null
        val copyId = newId()
        projectFile(id).copyTo(projectFile(copyId), overwrite = true)
        val thumb = thumbnailFile(id)
        if (thumb.exists()) thumb.copyTo(thumbnailFile(copyId), overwrite = true)
        dao.upsert(
            row.copy(
                id = copyId,
                name = newName,
                modifiedAt = clock.now(),
                projectPath = projectFile(copyId).path,
                thumbnailPath = thumbnailFile(copyId).path,
            ),
        )
        copyId
    }

    override suspend fun delete(id: String) = withContext(dispatchers.io) {
        dao.delete(id)
        projectFile(id).delete()
        thumbnailFile(id).delete()
        Unit
    }
}
