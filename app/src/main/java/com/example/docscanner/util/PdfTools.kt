package com.example.docscanner.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import kotlin.math.max
import kotlin.math.min

enum class PageSize(val label: String, val w: Int, val h: Int) {
    A4("A4", 595, 842),
    LETTER("Letter", 612, 792),
    FIT("Fit to image", 0, 0)
}

/** One entry when rebuilding a PDF: which source page, and how much to rotate it. */
data class PageEdit(val sourceIndex: Int, val rotation: Int = 0)

object PdfTools {

    // ---------- helpers ----------

    private inline fun <T> withRenderer(context: Context, uri: Uri, block: (PdfRenderer) -> T): T {
        val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("Cannot open PDF")
        pfd.use {
            val renderer = PdfRenderer(it)
            try {
                return block(renderer)
            } finally {
                renderer.close()
            }
        }
    }

    fun pageCount(context: Context, uri: Uri): Int =
        withRenderer(context, uri) { it.pageCount }

    /** Render one page to a bitmap [scale] times its natural size (72 dpi * scale). */
    private fun renderPage(renderer: PdfRenderer, index: Int, scale: Float): Bitmap {
        val page = renderer.openPage(index)
        try {
            val w = max(1, (page.width * scale).toInt())
            val h = max(1, (page.height * scale).toInt())
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bmp
        } finally {
            page.close()
        }
    }

    /** Thumbnail / preview for the UI. */
    fun renderPreview(context: Context, uri: Uri, index: Int, widthPx: Int = 600): Bitmap =
        withRenderer(context, uri) { r ->
            val page = r.openPage(index)
            val scale = widthPx.toFloat() / page.width
            page.close()
            renderPage(r, index, scale)
        }

    private fun drawFit(page: PdfDocument.Page, bmp: Bitmap, margin: Float) {
        val canvas = page.canvas
        canvas.drawColor(Color.WHITE)
        val pw = page.info.pageWidth - margin * 2
        val ph = page.info.pageHeight - margin * 2
        val ratio = min(pw / bmp.width, ph / bmp.height)
        val w = bmp.width * ratio
        val h = bmp.height * ratio
        val left = (page.info.pageWidth - w) / 2f
        val top = (page.info.pageHeight - h) / 2f
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(bmp, null, RectF(left, top, left + w, top + h), paint)
    }

    private fun writeDoc(doc: PdfDocument, out: File): File {
        out.outputStream().use { doc.writeTo(it) }
        doc.close()
        return out
    }

    // ---------- images -> PDF ----------

    /**
     * Build a PDF with one image per page.
     * [jpegQuality] re-encodes each image, which also shrinks the file.
     */
    fun imagesToPdf(
        context: Context,
        bitmaps: List<Bitmap>,
        out: File,
        pageSize: PageSize = PageSize.A4,
        margin: Float = 0f,
        jpegQuality: Int = 90
    ): File {
        val doc = PdfDocument()
        bitmaps.forEachIndexed { i, original ->
            // Round trip through JPEG so quality setting actually changes file size
            val bytes = ImageTools.toJpegBytes(original, jpegQuality)
            val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            val (pw, ph) = when {
                pageSize == PageSize.FIT -> bmp.width to bmp.height
                // landscape images get landscape pages
                bmp.width > bmp.height -> pageSize.h to pageSize.w
                else -> pageSize.w to pageSize.h
            }
            val info = PdfDocument.PageInfo.Builder(pw, ph, i + 1).create()
            val page = doc.startPage(info)
            drawFit(page, bmp, if (pageSize == PageSize.FIT) 0f else margin)
            doc.finishPage(page)
        }
        return writeDoc(doc, out)
    }

    /**
     * Same as [imagesToPdf] but loads one page at a time, so big documents do not fill the phone's memory.
     * [loader] returns the bitmap for page index i (or null to skip it).
     */
    fun pagesToPdf(
        count: Int,
        out: File,
        pageSize: PageSize,
        jpegQuality: Int,
        loader: (Int) -> Bitmap?
    ): File {
        val doc = PdfDocument()
        var number = 1
        for (i in 0 until count) {
            val original = loader(i) ?: continue
            val bytes = ImageTools.toJpegBytes(original, jpegQuality)
            original.recycle()
            val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            val (pw, ph) = when {
                pageSize == PageSize.FIT -> bmp.width to bmp.height
                bmp.width > bmp.height -> pageSize.h to pageSize.w
                else -> pageSize.w to pageSize.h
            }
            val page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, number++).create())
            drawFit(page, bmp, 0f)
            doc.finishPage(page)
            bmp.recycle()
        }
        require(number > 1) { "No readable pages" }
        return writeDoc(doc, out)
    }

    fun imageUrisToPdf(
        context: Context,
        uris: List<Uri>,
        out: File,
        pageSize: PageSize = PageSize.A4,
        margin: Float = 0f,
        jpegQuality: Int = 90,
        filter: ImageFilter = ImageFilter.ORIGINAL
    ): File {
        val bitmaps = uris.mapNotNull { ImageTools.decode(context, it, 2600) }
            .map { ImageTools.applyFilter(it, filter) }
        require(bitmaps.isNotEmpty()) { "No readable images" }
        return imagesToPdf(context, bitmaps, out, pageSize, margin, jpegQuality)
    }

    // ---------- PDF -> images ----------

    fun pdfToImages(
        context: Context,
        pdf: Uri,
        outDir: File,
        baseName: String,
        scale: Float = 2f,
        format: ImageFormat = ImageFormat.JPG,
        quality: Int = 90,
        pages: List<Int>? = null
    ): List<File> = withRenderer(context, pdf) { r ->
        outDir.mkdirs()
        val wanted = pages ?: (0 until r.pageCount).toList()
        wanted.filter { it in 0 until r.pageCount }.map { idx ->
            val bmp = renderPage(r, idx, scale)
            val f = File(outDir, "${baseName}_p${idx + 1}_${FileStore.timestamp()}.${format.ext}")
            ImageTools.save(bmp, f, format, quality)
            bmp.recycle()
            f
        }
    }

    // ---------- rebuild: merge, split, reorder, rotate, delete ----------

    /** Merge several PDFs in order. Pages are re-rendered, so very big PDFs take a moment. */
    fun merge(context: Context, pdfs: List<Uri>, out: File, scale: Float = 2f, quality: Int = 90): File {
        val doc = PdfDocument()
        var number = 1
        for (uri in pdfs) {
            withRenderer(context, uri) { r ->
                for (i in 0 until r.pageCount) {
                    val p = r.openPage(i)
                    val pw = p.width
                    val ph = p.height
                    p.close()
                    val bmp = renderPage(r, i, scale)
                    val info = PdfDocument.PageInfo.Builder(pw, ph, number++).create()
                    val page = doc.startPage(info)
                    drawFit(page, bmp, 0f)
                    doc.finishPage(page)
                    bmp.recycle()
                }
            }
        }
        return writeDoc(doc, out)
    }

    /** Build a new PDF from the given list of page edits (select / reorder / rotate / delete pages). */
    fun rebuild(
        context: Context,
        pdf: Uri,
        edits: List<PageEdit>,
        out: File,
        scale: Float = 2f
    ): File {
        require(edits.isNotEmpty()) { "Nothing to save" }
        val doc = PdfDocument()
        withRenderer(context, pdf) { r ->
            edits.forEachIndexed { n, e ->
                val src = renderPage(r, e.sourceIndex, scale)
                val bmp = if (e.rotation % 360 != 0) ImageTools.rotate(src, e.rotation.toFloat()) else src
                // keep point size = pixels / scale
                val pw = max(1, (bmp.width / scale).toInt())
                val ph = max(1, (bmp.height / scale).toInt())
                val page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, n + 1).create())
                drawFit(page, bmp, 0f)
                doc.finishPage(page)
            }
        }
        return writeDoc(doc, out)
    }

    /** Parse text like "1-3, 5, 8-10" into zero-based page indexes. Invalid parts are ignored. */
    fun parsePageRanges(text: String, pageCount: Int): List<Int> {
        val result = linkedSetOf<Int>()
        text.split(',', ';', ' ').map { it.trim() }.filter { it.isNotEmpty() }.forEach { part ->
            if (part.contains('-')) {
                val bits = part.split('-')
                val a = bits.getOrNull(0)?.toIntOrNull()
                val b = bits.getOrNull(1)?.toIntOrNull()
                if (a != null && b != null) {
                    val lo = min(a, b).coerceAtLeast(1)
                    val hi = max(a, b).coerceAtMost(pageCount)
                    for (p in lo..hi) result.add(p - 1)
                }
            } else {
                part.toIntOrNull()?.let { if (it in 1..pageCount) result.add(it - 1) }
            }
        }
        return result.toList()
    }

    fun extractPages(context: Context, pdf: Uri, pages: List<Int>, out: File): File =
        rebuild(context, pdf, pages.map { PageEdit(it) }, out)

    // ---------- ID card (front + back on one A4 page) ----------

    fun idCardToPdf(front: Bitmap, back: Bitmap?, out: File, jpegQuality: Int = 90): File {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val canvas = page.canvas
        canvas.drawColor(Color.WHITE)
        val border = Paint().apply {
            style = Paint.Style.STROKE
            color = Color.LTGRAY
            strokeWidth = 1f
        }
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        var top = 110f
        for (src in listOfNotNull(front, back)) {
            val bytes = ImageTools.toJpegBytes(src, jpegQuality)
            val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            val boxW = 340f
            val boxH = boxW * bmp.height / bmp.width
            val left = (595f - boxW) / 2f
            val rect = RectF(left, top, left + boxW, top + boxH)
            canvas.drawBitmap(bmp, null, rect, paint)
            canvas.drawRect(rect, border)
            top += boxH + 50f
        }
        doc.finishPage(page)
        return writeDoc(doc, out)
    }

    // ---------- compress ----------

    /**
     * Reduce PDF size by re-rendering every page as a JPEG.
     * [scale] 1.0 = 72 dpi, 1.5 = ~108 dpi, 2.0 = 144 dpi.
     */
    fun compress(context: Context, pdf: Uri, out: File, scale: Float, jpegQuality: Int): File {
        val doc = PdfDocument()
        withRenderer(context, pdf) { r ->
            for (i in 0 until r.pageCount) {
                val p = r.openPage(i)
                val pw = p.width
                val ph = p.height
                p.close()
                val bmp = renderPage(r, i, scale)
                val bytes = ImageTools.toJpegBytes(bmp, jpegQuality)
                val small = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                val page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, i + 1).create())
                drawFit(page, small, 0f)
                doc.finishPage(page)
                bmp.recycle()
            }
        }
        return writeDoc(doc, out)
    }

    /** Try progressively stronger settings until the output is at most [targetKb]. */
    fun compressToTarget(context: Context, pdf: Uri, out: File, targetKb: Int): File {
        val steps = listOf(
            2.0f to 85, 1.6f to 75, 1.3f to 65, 1.0f to 55, 0.8f to 45, 0.6f to 35
        )
        var last = out
        for ((scale, q) in steps) {
            last = compress(context, pdf, out, scale, q)
            if (last.length() <= targetKb * 1024L) break
        }
        return last
    }

    // ---------- insert image / signature / watermark ----------

    /**
     * Place an image on a page. Position and size are fractions of the page:
     * [xFrac]/[yFrac] = top-left corner, [widthFrac] = image width, height keeps aspect ratio.
     * [pageIndexes] lets you stamp several pages at once.
     */
    fun insertImage(
        context: Context,
        pdf: Uri,
        overlay: Bitmap,
        pageIndexes: Set<Int>,
        xFrac: Float,
        yFrac: Float,
        widthFrac: Float,
        out: File,
        scale: Float = 2f
    ): File {
        val doc = PdfDocument()
        withRenderer(context, pdf) { r ->
            for (i in 0 until r.pageCount) {
                val p = r.openPage(i)
                val pw = p.width
                val ph = p.height
                p.close()
                val bmp = renderPage(r, i, scale)
                val page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, i + 1).create())
                drawFit(page, bmp, 0f)
                if (i in pageIndexes) {
                    val w = pw * widthFrac
                    val h = w * overlay.height / overlay.width
                    val left = (pw * xFrac).coerceIn(0f, pw - w)
                    val top = (ph * yFrac).coerceIn(0f, max(0f, ph - h))
                    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                    page.canvas.drawBitmap(overlay, null, RectF(left, top, left + w, top + h), paint)
                }
                doc.finishPage(page)
                bmp.recycle()
            }
        }
        return writeDoc(doc, out)
    }

    /** Diagonal text watermark on every page. */
    fun addTextWatermark(
        context: Context,
        pdf: Uri,
        text: String,
        out: File,
        opacityPercent: Int = 25,
        scale: Float = 2f
    ): File {
        val doc = PdfDocument()
        withRenderer(context, pdf) { r ->
            for (i in 0 until r.pageCount) {
                val p = r.openPage(i)
                val pw = p.width
                val ph = p.height
                p.close()
                val bmp = renderPage(r, i, scale)
                val page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, i + 1).create())
                drawFit(page, bmp, 0f)
                val canvas: Canvas = page.canvas
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.GRAY
                    alpha = (255 * opacityPercent / 100f).toInt()
                    textSize = pw / 7f
                    textAlign = Paint.Align.CENTER
                    isFakeBoldText = true
                }
                canvas.save()
                canvas.rotate(-35f, pw / 2f, ph / 2f)
                canvas.drawText(text, pw / 2f, ph / 2f, paint)
                canvas.restore()
                doc.finishPage(page)
                bmp.recycle()
            }
        }
        return writeDoc(doc, out)
    }

    /** Add "Page n of N" at the bottom of every page. */
    fun addPageNumbers(context: Context, pdf: Uri, out: File, scale: Float = 2f): File {
        val doc = PdfDocument()
        withRenderer(context, pdf) { r ->
            val total = r.pageCount
            for (i in 0 until total) {
                val p = r.openPage(i)
                val pw = p.width
                val ph = p.height
                p.close()
                val bmp = renderPage(r, i, scale)
                val page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, i + 1).create())
                drawFit(page, bmp, 0f)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.DKGRAY
                    textSize = 11f
                    textAlign = Paint.Align.CENTER
                }
                page.canvas.drawText("Page ${i + 1} of $total", pw / 2f, ph - 18f, paint)
                doc.finishPage(page)
                bmp.recycle()
            }
        }
        return writeDoc(doc, out)
    }
}
