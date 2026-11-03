package com.example.docscanner.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.min

enum class ImageFilter(val label: String) {
    ORIGINAL("Original"),
    MAGIC("Auto color"),
    NO_SHADOW("No shadow"),
    GRAYSCALE("Grayscale"),
    BLACK_WHITE("Black & white"),
    WHITEBOARD("Whiteboard")
}

enum class ImageFormat(val label: String, val ext: String) {
    JPG("JPG", "jpg"),
    PNG("PNG", "png")
}

object ImageTools {

    /** Decode an image from a URI, downsampled so the longest side is at most [maxDim], with EXIF rotation applied. */
    fun decode(context: Context, uri: Uri, maxDim: Int = 2400): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxDim * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

        val rotation = try {
            resolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (e: Exception) {
            0f
        }
        if (rotation != 0f) bmp = rotate(bmp, rotation)

        val longest = max(bmp.width, bmp.height)
        if (longest > maxDim) bmp = scaleToMaxDim(bmp, maxDim)
        return bmp
    }

    fun rotate(src: Bitmap, degrees: Float): Bitmap {
        val m = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    fun scaleToMaxDim(src: Bitmap, maxDim: Int): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= maxDim) return src
        val ratio = maxDim.toFloat() / longest
        return Bitmap.createScaledBitmap(
            src, max(1, (src.width * ratio).toInt()), max(1, (src.height * ratio).toInt()), true
        )
    }

    /** Width and height of an image without loading it into memory. */
    fun dimensions(context: Context, uri: Uri): Pair<Int, Int>? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        return if (bounds.outWidth > 0 && bounds.outHeight > 0) bounds.outWidth to bounds.outHeight else null
    }

    /** Scale so the longest side is exactly [longest] pixels. Can make the image bigger or smaller. */
    fun scaleToLongest(src: Bitmap, longest: Int): Bitmap {
        val target = longest.coerceIn(16, MAX_SIDE)
        val ratio = target.toFloat() / max(src.width, src.height)
        return Bitmap.createScaledBitmap(
            src, max(1, (src.width * ratio).toInt()), max(1, (src.height * ratio).toInt()), true
        )
    }

    /** Scale by a percentage (10 = tiny, 100 = same, 200 = twice as big). Output is capped at [MAX_SIDE]. */
    fun scaleByPercent(src: Bitmap, percent: Int): Bitmap {
        val r = percent.coerceIn(5, 400) / 100f
        val longest = max(src.width, src.height) * r
        return scaleToLongest(src, longest.toInt())
    }

    const val MAX_SIDE = 8000

    fun resizeTo(src: Bitmap, width: Int, height: Int): Bitmap =
        Bitmap.createScaledBitmap(src, max(1, width), max(1, height), true)

    /** Crop using fractions (0f..1f) of the bitmap size. */
    fun cropFraction(src: Bitmap, left: Float, top: Float, right: Float, bottom: Float): Bitmap {
        val l = (src.width * left.coerceIn(0f, 0.95f)).toInt()
        val t = (src.height * top.coerceIn(0f, 0.95f)).toInt()
        val r = (src.width * right.coerceIn(0.05f, 1f)).toInt()
        val b = (src.height * bottom.coerceIn(0.05f, 1f)).toInt()
        val w = max(1, r - l)
        val h = max(1, b - t)
        return Bitmap.createBitmap(src, l, t, min(w, src.width - l), min(h, src.height - t))
    }

    fun applyFilter(src: Bitmap, filter: ImageFilter): Bitmap {
        if (filter == ImageFilter.ORIGINAL) return src
        val matrix = ColorMatrix()
        when (filter) {
            ImageFilter.GRAYSCALE -> matrix.setSaturation(0f)
            ImageFilter.MAGIC -> {
                // Slight contrast + brightness boost makes paper look cleaner
                val c = 1.35f
                val b = -25f
                matrix.set(
                    floatArrayOf(
                        c, 0f, 0f, 0f, b,
                        0f, c, 0f, 0f, b,
                        0f, 0f, c, 0f, b,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            ImageFilter.BLACK_WHITE -> {
                val gray = ColorMatrix().apply { setSaturation(0f) }
                val c = 6f
                val b = -(c * 128f) + 128f + 20f
                val contrast = ColorMatrix(
                    floatArrayOf(
                        c, 0f, 0f, 0f, b,
                        0f, c, 0f, 0f, b,
                        0f, 0f, c, 0f, b,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                gray.postConcat(contrast)
                matrix.set(gray)
            }
            ImageFilter.NO_SHADOW -> matrix.set(contrastMatrix(1.15f, 35f))
            ImageFilter.WHITEBOARD -> {
                val sat = ColorMatrix().apply { setSaturation(1.3f) }
                sat.postConcat(ColorMatrix(contrastMatrix(1.6f, -30f)))
                matrix.set(sat)
            }
            else -> {}
        }
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    private fun contrastMatrix(c: Float, offset: Float) = floatArrayOf(
        c, 0f, 0f, 0f, offset,
        0f, c, 0f, 0f, offset,
        0f, 0f, c, 0f, offset,
        0f, 0f, 0f, 1f, 0f
    )

    /**
     * Full page pipeline used by the editor and by every export:
     * rotate, then filter, then brightness (-100..100) and contrast (0.5..2.0).
     */
    fun process(
        src: Bitmap,
        rotation: Int,
        filter: ImageFilter,
        brightness: Float,
        contrast: Float
    ): Bitmap {
        var bmp = if (rotation % 360 != 0) rotate(src, rotation.toFloat()) else src
        bmp = applyFilter(bmp, filter)
        if (brightness == 0f && contrast == 1f) return bmp
        // contrast pivots around mid grey, brightness shifts every channel
        val offset = brightness * 2.55f + 128f * (1f - contrast)
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(contrastMatrix(contrast, offset)))
        }
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(bmp, 0f, 0f, paint)
        return out
    }

    fun toJpegBytes(bmp: Bitmap, quality: Int): ByteArray {
        val flat = flattenOnWhite(bmp)
        val out = ByteArrayOutputStream()
        flat.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), out)
        return out.toByteArray()
    }

    /** JPEG has no alpha, so draw transparent images over white first. */
    fun flattenOnWhite(src: Bitmap): Bitmap {
        if (!src.hasAlpha()) return src
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.WHITE)
        c.drawBitmap(src, 0f, 0f, null)
        return out
    }

    fun save(bmp: Bitmap, file: File, format: ImageFormat, quality: Int = 90): File {
        file.outputStream().use { out ->
            if (format == ImageFormat.PNG) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            } else {
                flattenOnWhite(bmp).compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), out)
            }
        }
        return file
    }

    /**
     * Shrink an image until the JPEG is at or below [targetKb].
     * First lowers quality, then reduces dimensions. Returns the JPEG bytes.
     */
    fun compressToTarget(src: Bitmap, targetKb: Int): ByteArray {
        val target = targetKb * 1024
        var current = src
        var scale = 1.0f
        repeat(8) {
            var q = 92
            while (q >= 30) {
                val bytes = toJpegBytes(current, q)
                if (bytes.size <= target) return bytes
                q -= 10
            }
            scale *= 0.8f
            current = Bitmap.createScaledBitmap(
                src, max(1, (src.width * scale).toInt()), max(1, (src.height * scale).toInt()), true
            )
        }
        return toJpegBytes(current, 30)
    }

    /** Make white (and near white) pixels transparent. Used for signatures taken from a photo. */
    fun whiteToTransparent(src: Bitmap, threshold: Int = 235): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val px = IntArray(out.width * out.height)
        out.getPixels(px, 0, out.width, 0, 0, out.width, out.height)
        for (i in px.indices) {
            val p = px[i]
            if (Color.red(p) > threshold && Color.green(p) > threshold && Color.blue(p) > threshold) {
                px[i] = Color.TRANSPARENT
            }
        }
        out.setPixels(px, 0, out.width, 0, 0, out.width, out.height)
        return out
    }
}
