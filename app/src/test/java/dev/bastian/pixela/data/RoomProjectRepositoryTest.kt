package dev.bastian.pixela.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import dev.bastian.pixela.data.db.PixelaDatabase
import dev.bastian.pixela.data.repo.RoomProjectRepository
import dev.bastian.pixela.domain.AppDispatchers
import dev.bastian.pixela.domain.Clock
import dev.bastian.pixela.domain.Document
import dev.bastian.pixela.domain.ProjectSnapshot
import dev.bastian.pixela.domain.ThumbnailWriter
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class RoomProjectRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var db: PixelaDatabase
    private lateinit var repository: RoomProjectRepository
    private lateinit var projectsDir: File
    private var now = 1_000L
    private var counter = 0
    private val thumbnails = mutableListOf<String>()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PixelaDatabase::class.java).allowMainThreadQueries().build()
        projectsDir = folder.newFolder("projects")
        val dispatcher = UnconfinedTestDispatcher()
        repository = RoomProjectRepository(
            dao = db.projects(),
            directory = projectsDir,
            thumbnails = ThumbnailWriter { path, _, _, _ ->
                File(path).writeText("png")
                thumbnails += path
            },
            clock = Clock { now },
            dispatchers = AppDispatchers(io = dispatcher, default = dispatcher),
            newId = { "id${++counter}" },
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `a created project can be listed and loaded`() = runTest {
        val id = repository.create("Cat", 16, "Layer 1")
        repository.projects.test {
            val item = awaitItem().single()
            assertEquals("Cat", item.name)
            assertEquals(16, item.size)
            assertEquals(1_000L, item.modifiedAt)
            assertTrue(File(item.thumbnailPath).exists())
        }
        val loaded = assertNotNull(repository.load(id))
        assertEquals("Cat", loaded.name)
        assertEquals(16, loaded.document.width)
    }

    @Test
    fun `saving keeps the name, updates the date and survives a new repository instance`() = runTest {
        val id = repository.create("Cat", 16, "Layer 1")
        val doc = repository.load(id)!!.document
        doc.layers[0].buffer[2, 2] = 0xFF123456.toInt()
        now = 5_000
        repository.save(id, ProjectSnapshot.of(doc))

        val secondRepository = RoomProjectRepository(
            db.projects(), projectsDir, { _, _, _, _ -> }, { now }, AppDispatchers(UnconfinedTestDispatcher(), UnconfinedTestDispatcher()), { "x" },
        )
        val reloaded = assertNotNull(secondRepository.load(id))
        assertEquals("Cat", reloaded.name)
        assertEquals(0xFF123456.toInt(), reloaded.document.layers[0].buffer[2, 2])
        secondRepository.projects.test { assertEquals(5_000L, awaitItem().single().modifiedAt) }
    }

    @Test
    fun `the newest project is listed first`() = runTest {
        repository.create("Old", 16, "L")
        now = 2_000
        repository.create("New", 16, "L")
        repository.projects.test { assertEquals(listOf("New", "Old"), awaitItem().map { it.name }) }
    }

    @Test
    fun `rename changes only the name`() = runTest {
        val id = repository.create("Cat", 16, "L")
        repository.rename(id, "Dog")
        assertEquals("Dog", repository.load(id)!!.name)
    }

    @Test
    fun `duplicate copies pixels and files under a new id`() = runTest {
        val id = repository.create("Cat", 16, "L")
        val doc = repository.load(id)!!.document
        doc.layers[0].buffer[1, 1] = 0xFFABCDEF.toInt()
        repository.save(id, ProjectSnapshot.of(doc))

        val copyId = assertNotNull(repository.duplicate(id, "Cat copy"))
        val copy = assertNotNull(repository.load(copyId))
        assertEquals("Cat copy", copy.name)
        assertEquals(0xFFABCDEF.toInt(), copy.document.layers[0].buffer[1, 1])
        repository.projects.test { assertEquals(2, awaitItem().size) }
        assertNull(repository.duplicate("missing", "x"))
    }

    @Test
    fun `delete removes the row and the files`() = runTest {
        val id = repository.create("Cat", 16, "L")
        repository.delete(id)
        assertNull(repository.load(id))
        repository.projects.test { assertTrue(awaitItem().isEmpty()) }
        assertFalse(File(projectsDir, "$id.json").exists())
        assertFalse(File(projectsDir, "$id.png").exists())
    }

    @Test
    fun `a damaged project file loads as null and does not crash`() = runTest {
        val id = repository.create("Cat", 16, "L")
        File(projectsDir, "$id.json").writeText("{ broken")
        assertNull(repository.load(id))
        File(projectsDir, "$id.json").delete()
        assertNull(repository.load(id))
    }

    @Test
    fun `saving leaves no temporary file behind`() = runTest {
        val id = repository.create("Cat", 16, "L")
        repository.save(id, ProjectSnapshot.of(Document.blank(16, "L")))
        assertEquals(setOf("$id.json", "$id.png"), projectsDir.list()!!.toSet())
    }
}
