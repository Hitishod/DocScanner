package com.example.docscanner.util

import android.graphics.Bitmap
import com.example.docscanner.data.OcrLanguage
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** On device text recognition. All functions block, so call them from a background thread. */
object OcrTools {

    /** Read the text of each page. Result has one string per page, in order. */
    fun recognizePages(language: OcrLanguage, pages: Sequence<Bitmap?>): List<String> {
        val recognizer = when (language) {
            OcrLanguage.LATIN -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            OcrLanguage.DEVANAGARI -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        }
        try {
            return pages.map { bmp ->
                if (bmp == null) "" else Tasks.await(recognizer.process(InputImage.fromBitmap(bmp, 0))).text
            }.toList()
        } finally {
            recognizer.close()
        }
    }
}
