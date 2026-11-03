package com.example.docscanner.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.graphics.pdf.PdfRenderer
import com.example.docscanner.util.ImageFilter
import com.example.docscanner.util.ImageFormat
import com.example.docscanner.util.ImageTools
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Kind of document. Decides defaults such as the starting filter and the export layout. */
enum class DocType(val label: String) {
    DOCUMENT("Document"),
    ID_CARD("ID card"),
    BOOK("Book"),
    WHITEBOARD("Whiteboard"),
    IMPORTED("Imported")
}

/** One page: the untouched image file plus the edits applied on top of it. */
data class PageParams(
    val file: String,
    val rotation: Int = 0,
    val filter: ImageFilter = ImageFilter.ORIGINAL,
    val brightness: Float = 0f,
    val contrast: Float = 1f
)

data class ScanDoc(
    val id: String,
    val name: String,
    val createdAt: Long,
    val type: DocType,
    val pages: List<PageParams>
)

/**
 * Library on disk: filesDir/library/<id>/{meta.json, <page>.jpg ...}
 * Original page images are never changed by filters, so edits can always be undone.
 */
object DocStore {

    private fun root(context: Context) = File(context.filesDir, "library").apply { mkdirs() }
    fun dir(context: Context, id: String) = File(root(context), id)
    fun pageFile(context: Context, doc: ScanDoc, index: Int) = File(dir(context, doc.id), doc.pages[index].file)

    fun defaultName(): String =
        "Scan " + SimpleDateFormat("dd MMM yyyy HH.mm", Locale.getDefault()).format(Date())

    // ---------------------------------------------------------------- read / write meta

    fun save(context: Context, doc: ScanDoc) {
        val d = dir(context, doc.id).apply { mkdirs() }
        val json = JSONObject().apply {
            put("name", doc.name)
            put("createdAt", doc.createdAt)
            put("type", doc.type.name)
            put("pages", JSONArray().also { arr ->
                doc.pages.forEach { p ->
                    arr.put(JSONObject().apply {
                        put("file", p.file)
                        put("rotation", p.rotation)
                        put("filter", p.filter.name)
                        put("brightness", p.brightness.toDouble())
                        put("contrast", p.contrast.toDouble())
                    })
                }
            })
        }
        // write to a temp file first so a crash never leaves a half written meta.json
        val tmp = File(d, "meta.tmp")
        tmp.writeText(json.toString())
        tmp.renameTo(File(d, "meta.json"))
    }

    fun load(context: Context, id: String): ScanDoc? {
        val f = File(dir(context, id), "meta.json")
        if (!f.exists()) return null
        return try {
            val o = JSONObject(f.readText())
            val arr = o.getJSONArray("pages")
            val pages = (0 until arr.length()).map { i ->
                val p = arr.getJSONObject(i)
                PageParams(
                    file = p.getString("file"),
                    rotation = p.optInt("rotation", 0),
                    filter = runCatching { ImageFilter.valueOf(p.optString("filter")) }.getOrDefault(ImageFilter.ORIGINAL),
                    brightness = p.optDouble("brightness", 0.0).toFloat(),
                    contrast = p.optDouble("contrast", 1.0).toFloat()
                )
            }.filter { File(dir(context, id), it.file).exists() }
            ScanDoc(
                id = id,
                name = o.optString("name", "Untitled"),
                createdAt = o.optLong("createdAt", f.lastModified()),
                type = runCatching { DocType.valueOf(o.optString("type")) }.getOrDefault(DocType.DOCUMENT),
                pages = pages
            )
        } catch (e: Exception) {
            null
        }
    }

    /** All documents, newest first. */
    fun list(context: Context): List<ScanDoc> =
        (root(context).listFiles() ?: emptyArray())
            .filter { it.isDirectory }
            .mapNotNull { load(context, it.name) }
            .sortedByDescending { it.createdAt }

    fun delete(context: Context, id: String) {
        dir(context, id).deleteRecursively()
    }

    fun storageBytes(context: Context): Long =
        root(context).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    // ---------------------------------------------------------------- create

    private fun defaultFilter(type: DocType, autoEnhance: Boolean): ImageFilter = when (type) {
        DocType.WHITEBOARD -> ImageFilter.WHITEBOARD
        DocType.DOCUMENT, DocType.BOOK -> if (autoEnhance) ImageFilter.MAGIC else ImageFilter.ORIGINAL
        else -> ImageFilter.ORIGINAL
    }

    /** Copy an image into the document folder (JPEG, rotated upright, max 3200 px). Returns the new file name. */
    private fun storeImage(context: Context, docId: String, source: Uri): String? {
        val bmp = ImageTools.decode(context, source, 3200) ?: return null
        val name = "p_" + UUID.randomUUID().toString().take(8) + ".jpg"
        ImageTools.save(bmp, File(dir(context, docId).apply { mkdirs() }, name), ImageFormat.JPG, 92)
        return name
    }

    /** Runs blocking image work, so call it from a background thread. */
    fun createFromImages(
        context: Context,
        uris: List<Uri>,
        type: DocType,
        autoEnhance: Boolean,
        name: String = defaultName()
    ): ScanDoc {
        val id = UUID.randomUUID().toString()
        val filter = defaultFilter(type, autoEnhance)
        val pages = uris.mapNotNull { storeImage(context, id, it) }.map { PageParams(it, filter = filter) }
        require(pages.isNotEmpty()) { "No readable images" }
        val doc = ScanDoc(id, name, System.currentTimeMillis(), type, pages)
        save(context, doc)
        return doc
    }

    /** Import every page of a PDF as an image page (first 100 pages). */
    fun createFromPdf(context: Context, pdf: Uri, name: String): ScanDoc {
        val id = UUID.randomUUID().toString()
        val d = dir(context, id).apply { mkdirs() }
        val pages = mutableListOf<PageParams>()
        val pfd = context.contentResolver.openFileDescriptor(pdf, "r") ?: error("Cannot open PDF")
        pfd.use {
            val renderer = PdfRenderer(it)
            try {
                for (i in 0 until minOf(renderer.pageCount, 100)) {
                    val page = renderer.openPage(i)
                    val scale = 2f
                    val bmp = Bitmap.createBitmap(
                        (page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888
                    )
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    val fileName = "p_" + UUID.randomUUID().toString().take(8) + ".jpg"
                    ImageTools.save(bmp, File(d, fileName), ImageFormat.JPG, 90)
                    bmp.recycle()
                    pages += PageParams(fileName)
                }
            } finally {
                renderer.close()
            }
        }
        require(pages.isNotEmpty()) { "This PDF has no pages" }
        val doc = ScanDoc(id, name, System.currentTimeMillis(), DocType.IMPORTED, pages)
        save(context, doc)
        return doc
    }

    /** Append more pages (from a scan or the gallery) to an existing document. */
    fun addPages(context: Context, doc: ScanDoc, uris: List<Uri>, autoEnhance: Boolean): ScanDoc {
        val filter = defaultFilter(doc.type, autoEnhance)
        val added = uris.mapNotNull { storeImage(context, doc.id, it) }.map { PageParams(it, filter = filter) }
        val updated = doc.copy(pages = doc.pages + added)
        save(context, updated)
        return updated
    }

    // ---------------------------------------------------------------- render

    /** The page exactly as it will be exported, scaled down to [maxDim] for previews. */
    fun render(context: Context, doc: ScanDoc, index: Int, maxDim: Int): Bitmap? {
        val p = doc.pages.getOrNull(index) ?: return null
        val src = ImageTools.decode(context, Uri.fromFile(File(dir(context, doc.id), p.file)), maxDim) ?: return null
        return ImageTools.process(src, p.rotation, p.filter, p.brightness, p.contrast)
    }

    /** The untouched page image, used by the crop screen. */
    fun original(context: Context, doc: ScanDoc, index: Int, maxDim: Int): Bitmap? {
        val p = doc.pages.getOrNull(index) ?: return null
        return ImageTools.decode(context, Uri.fromFile(File(dir(context, doc.id), p.file)), maxDim)
    }

    /**
     * Replace a page's image after cropping. The crop is made on the unrotated original,
     * so the page keeps its rotation, filter and adjustments.
     */
    fun replaceOriginal(context: Context, doc: ScanDoc, index: Int, bmp: Bitmap) {
        val p = doc.pages[index]
        ImageTools.save(bmp, File(dir(context, doc.id), p.file), ImageFormat.JPG, 92)
    }

    fun deletePageFile(context: Context, docId: String, page: PageParams) {
        File(dir(context, docId), page.file).delete()
    }
}
