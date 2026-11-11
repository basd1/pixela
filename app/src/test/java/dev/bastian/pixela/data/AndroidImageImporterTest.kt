package dev.bastian.pixela.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.bastian.pixela.data.io.AndroidImageImporter
import dev.bastian.pixela.domain.AppDispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AndroidImageImporterTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val importer = AndroidImageImporter(
        ApplicationProvider.getApplicationContext<Context>(),
        AppDispatchers(io = dispatcher, default = dispatcher),
    )

    private fun pngOf(width: Int, height: Int, color: Int): String {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        val file = File(folder.root, "source.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Uri.fromFile(file).toString()
    }

    @Test
    fun `a larger image is reduced to the canvas size`() = runTest(dispatcher) {
        val pixels = importer.import(pngOf(128, 128, Color.RED), 16, 16).getOrThrow()

        assertEquals(16 * 16, pixels.size)
        assertTrue(pixels.all { it == Color.RED })
    }

    @Test
    fun `a missing file is reported as a failure`() = runTest(dispatcher) {
        val missing = Uri.fromFile(File(folder.root, "nope.png")).toString()

        assertTrue(importer.import(missing, 16, 16).isFailure)
    }
}
