package ir.mohitban.app.data.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

/**
 * All pictures (signatures, receipts, profile and vehicle photos) live in the app's private
 * storage. Paths stored in the database are relative ("media/xyz.png") so backups can be
 * restored on another phone.
 */
class FileStore(private val context: Context) {

    private val mediaDir: File get() = File(context.filesDir, MEDIA).apply { mkdirs() }
    val cacheDir: File get() = File(context.cacheDir, "out").apply { mkdirs() }

    fun resolve(path: String?): File? {
        if (path.isNullOrBlank()) return null
        val f = if (path.startsWith("/")) File(path) else File(context.filesDir, path)
        return f.takeIf { it.exists() }
    }

    private fun relative(file: File): String = "$MEDIA/${file.name}"

    fun newMediaFile(prefix: String, ext: String): File = File(mediaDir, "${prefix}_${UUID.randomUUID()}.$ext")

    /** Temporary file the camera app writes into. */
    fun newCameraFile(): File = File(cacheDir, "camera_${System.currentTimeMillis()}.jpg")

    fun savePng(bitmap: Bitmap, prefix: String): String {
        val f = newMediaFile(prefix, "png")
        FileOutputStream(f).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return relative(f)
    }

    fun saveJpeg(bitmap: Bitmap, prefix: String, quality: Int = 88): String {
        val f = newMediaFile(prefix, "jpg")
        FileOutputStream(f).use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        return relative(f)
    }

    /** Copies a picture from the gallery / camera into private storage (rotated upright, max 2000 px). */
    fun importImage(uri: Uri, prefix: String, maxSize: Int = 2000): String? {
        val bmp = decodeUri(uri, maxSize) ?: return null
        return saveJpeg(bmp, prefix)
    }

    fun decodeUri(uri: Uri, maxSize: Int = 2000): Bitmap? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val sample = sampleSize(bounds.outWidth, bounds.outHeight, maxSize)
        val bmp = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val rotation = resolver.openInputStream(uri)?.use { exifRotation(ExifInterface(it)) } ?: 0
        rotate(bmp, rotation)
    }.getOrNull()

    fun decodeFile(file: File, maxSize: Int = 2000): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val sample = sampleSize(bounds.outWidth, bounds.outHeight, maxSize)
        val bmp = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        rotate(bmp, exifRotation(ExifInterface(file.absolutePath)))
    }.getOrNull()

    fun loadBitmap(path: String?, maxSize: Int = 1600): Bitmap? = resolve(path)?.let { decodeFile(it, maxSize) }

    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        val f = if (path.startsWith("/")) File(path) else File(context.filesDir, path)
        runCatching { f.delete() }
    }

    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Every file under media/ (for backups). */
    fun allMedia(): List<File> = mediaDir.listFiles()?.toList().orEmpty()
    fun mediaRoot(): File = mediaDir

    private fun sampleSize(w: Int, h: Int, maxSize: Int): Int {
        var s = 1
        val longest = max(w, h)
        while (longest / (s * 2) >= maxSize) s *= 2
        return s
    }

    private fun exifRotation(exif: ExifInterface): Int =
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

    private fun rotate(bmp: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bmp
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    companion object {
        const val MEDIA = "media"
    }
}
