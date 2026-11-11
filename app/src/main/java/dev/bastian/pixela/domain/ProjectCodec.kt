package dev.bastian.pixela.domain

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Compact text form of a pixel array: run-length encoded (count, color) pairs, then Base64. */
object PixelCodec {
    fun encode(pixels: IntArray): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            var i = 0
            while (i < pixels.size) {
                var run = 1
                while (i + run < pixels.size && pixels[i + run] == pixels[i] && run < MAX_RUN) run++
                out.writeShort(run)
                out.writeInt(pixels[i])
                i += run
            }
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    /** Throws [IllegalArgumentException] when the text is not a valid encoding of exactly [size] pixels. */
    fun decode(text: String, size: Int): IntArray {
        val data = try {
            Base64.getDecoder().decode(text)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Pixel data is not valid Base64", e)
        }
        val out = IntArray(size)
        var filled = 0
        DataInputStream(ByteArrayInputStream(data)).use { input ->
            while (input.available() > 0) {
                val run = input.readUnsignedShort()
                val color = input.readInt()
                require(run > 0 && filled + run <= size) { "Pixel data does not fit the canvas" }
                out.fill(color, filled, filled + run)
                filled += run
            }
        }
        require(filled == size) { "Pixel data is incomplete" }
        return out
    }

    private const val MAX_RUN = 0xFFFF
}

@Serializable
data class LayerFile(
    val id: Int,
    val name: String,
    val visible: Boolean,
    val opacity: Float,
    val pixels: String,
)

@Serializable
data class ProjectFile(
    val version: Int = CURRENT_VERSION,
    val width: Int,
    val height: Int,
    val activeLayerId: Int,
    val nextLayerId: Int,
    val palette: List<Int>,
    val layers: List<LayerFile>,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

/** Converts a [Document] to and from the JSON project file format. */
object ProjectCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(document: Document): String {
        val file = ProjectFile(
            width = document.width,
            height = document.height,
            activeLayerId = document.activeLayerId,
            nextLayerId = document.nextLayerId,
            palette = document.palette,
            layers = document.layers.map {
                LayerFile(it.id, it.name, it.visible, it.opacity, PixelCodec.encode(it.buffer.pixels))
            },
        )
        return json.encodeToString(ProjectFile.serializer(), file)
    }

    /** Null when the text is not a valid project (corrupt, truncated or from a newer version). */
    fun decode(text: String): Document? = try {
        val file = json.decodeFromString(ProjectFile.serializer(), text)
        require(file.version <= ProjectFile.CURRENT_VERSION) { "Unsupported project version" }
        require(file.width in 1..MAX_SIZE && file.height in 1..MAX_SIZE) { "Invalid canvas size" }
        val layers = file.layers.map {
            Layer(it.id, it.name, it.visible, it.opacity.coerceIn(0f, 1f), PixelBuffer(file.width, file.height, PixelCodec.decode(it.pixels, file.width * file.height)))
        }
        Document(file.width, file.height, layers, file.activeLayerId, file.palette, file.nextLayerId)
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    const val MAX_SIZE = 64
}
