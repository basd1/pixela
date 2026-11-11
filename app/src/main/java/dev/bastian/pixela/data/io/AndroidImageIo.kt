package dev.bastian.pixela.data.io

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import dev.bastian.pixela.domain.AppDispatchers
import dev.bastian.pixela.domain.ImageExporter
import dev.bastian.pixela.domain.ImageImporter
import dev.bastian.pixela.domain.ThumbnailWriter
import dev.bastian.pixela.domain.fitInside
import dev.bastian.pixela.domain.placeOnCanvas
import dev.bastian.pixela.domain.scaleNearest
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

private fun pngBitmap(pixels: IntArray, width: Int, height: Int, scale: Int): Bitmap {
    val scaled = scaleNearest(pixels, width, height, scale)
    return Bitmap.createBitmap(scaled, width * scale, height * scale, Bitmap.Config.ARGB_8888)
}

private fun writePng(bitmap: Bitmap, out: OutputStream) {
    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw IOException("PNG encoding failed")
}

/** Gallery thumbnails are enlarged with nearest neighbor so a 16 pixel drawing is still sharp. */
class AndroidThumbnailWriter : ThumbnailWriter {
    override fun write(path: String, pixels: IntArray, width: Int, height: Int) {
        val factor = maxOf(1, THUMBNAIL_PX / maxOf(width, height))
        val bitmap = pngBitmap(pixels, width, height, factor)
        FileOutputStream(path).use { writePng(bitmap, it) }
        bitmap.recycle()
    }

    private companion object {
        const val THUMBNAIL_PX = 128
    }
}

class AndroidImageExporter(
    private val context: Context,
    private val dispatchers: AppDispatchers,
) : ImageExporter {

    override suspend fun saveToGallery(name: String, pixels: IntArray, width: Int, height: Int, scale: Int): Result<String> =
        withContext(dispatchers.io) {
            runCatching {
                val bitmap = pngBitmap(pixels, width, height, scale)
                try {
                    val fileName = fileName(name, scale)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveWithMediaStore(bitmap, fileName) else saveLegacy(bitmap, fileName)
                } finally {
                    bitmap.recycle()
                }
            }
        }

    override suspend fun prepareShare(name: String, pixels: IntArray, width: Int, height: Int, scale: Int): Result<String> =
        withContext(dispatchers.io) {
            runCatching {
                val bitmap = pngBitmap(pixels, width, height, scale)
                try {
                    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                    val file = File(dir, fileName(name, scale))
                    FileOutputStream(file).use { writePng(bitmap, it) }
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).toString()
                } finally {
                    bitmap.recycle()
                }
            }
        }

    private fun saveWithMediaStore(bitmap: Bitmap, fileName: String): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$GALLERY_FOLDER")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("The gallery did not accept the image")
        try {
            resolver.openOutputStream(uri)?.use { writePng(bitmap, it) } ?: throw IOException("Could not open the gallery entry")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } catch (e: IOException) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri.toString()
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(bitmap: Bitmap, fileName: String): String {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) throw SecurityException("Storage permission was not granted")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), GALLERY_FOLDER)
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Could not create ${dir.path}")
        val file = File(dir, fileName)
        FileOutputStream(file).use { writePng(bitmap, it) }
        MediaScannerConnection.scanFile(context, arrayOf(file.path), arrayOf("image/png"), null)
        return Uri.fromFile(file).toString()
    }

    private fun fileName(name: String, scale: Int): String {
        val safe = name.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifEmpty { "pixela" }
        return "${safe}_${scale}x.png"
    }

    private companion object {
        const val GALLERY_FOLDER = "Pixela"
    }
}

class AndroidImageImporter(
    private val context: Context,
    private val dispatchers: AppDispatchers,
) : ImageImporter {

    override suspend fun import(uri: String, width: Int, height: Int): Result<IntArray> = withContext(dispatchers.io) {
        runCatching {
            val source = Uri.parse(uri)
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            // With inJustDecodeBounds the decoder returns null by design; only the stream can be missing.
            val stream = resolver.openInputStream(source) ?: throw IOException("Could not open the image")
            stream.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("The file is not an image")
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= SAMPLE_TARGET * width && bounds.outHeight / (sample * 2) >= SAMPLE_TARGET * height) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: throw IOException("Could not decode the image")
            try {
                val rect = fitInside(decoded.width, decoded.height, width, height)
                val scaled = Bitmap.createScaledBitmap(decoded, rect.width, rect.height, true)
                val pixels = IntArray(rect.width * rect.height)
                scaled.getPixels(pixels, 0, rect.width, 0, 0, rect.width, rect.height)
                if (scaled !== decoded) scaled.recycle()
                placeOnCanvas(pixels, rect, width, height)
            } finally {
                decoded.recycle()
            }
        }
    }

    private companion object {
        const val SAMPLE_TARGET = 4
    }
}
