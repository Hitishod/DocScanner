package com.example.docscanner.data

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.docscanner.util.PageSize

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

enum class ExportQuality(val label: String, val jpeg: Int, val scale: Float) {
    SMALL("Small file", 55, 1.2f),
    MEDIUM("Balanced", 75, 1.6f),
    HIGH("High quality", 90, 2.2f)
}

enum class OcrLanguage(val label: String) {
    LATIN("English / Latin"),
    DEVANAGARI("Hindi / Devanagari")
}

/** User settings. Values are Compose state, so screens update instantly, and each change is saved. */
class AppPrefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private inline fun <reified E : Enum<E>> read(key: String, default: E): E =
        runCatching { enumValueOf<E>(sp.getString(key, default.name) ?: default.name) }.getOrDefault(default)

    var theme by mutableStateOf(read("theme", ThemeMode.SYSTEM))
        private set
    var quality by mutableStateOf(read("quality", ExportQuality.MEDIUM))
        private set
    var pageSize by mutableStateOf(read("pageSize", PageSize.A4))
        private set
    var ocrLanguage by mutableStateOf(read("ocrLanguage", OcrLanguage.LATIN))
        private set
    var autoEnhance by mutableStateOf(sp.getBoolean("autoEnhance", true))
        private set

    fun updateTheme(v: ThemeMode) { theme = v; sp.edit().putString("theme", v.name).apply() }
    fun updateQuality(v: ExportQuality) { quality = v; sp.edit().putString("quality", v.name).apply() }
    fun updatePageSize(v: PageSize) { pageSize = v; sp.edit().putString("pageSize", v.name).apply() }
    fun updateOcrLanguage(v: OcrLanguage) { ocrLanguage = v; sp.edit().putString("ocrLanguage", v.name).apply() }
    fun updateAutoEnhance(v: Boolean) { autoEnhance = v; sp.edit().putBoolean("autoEnhance", v).apply() }
}

val LocalPrefs = compositionLocalOf<AppPrefs> { error("AppPrefs not provided") }
