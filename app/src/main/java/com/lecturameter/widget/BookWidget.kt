package com.lecturameter.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lecturameter.model.*
import com.lecturameter.utils.WidgetStats
import com.lecturameter.utils.computeWidgetStats
import com.lecturameter.MainActivity
import com.lecturameter.R
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val WIDGET_PREFS = "widget_book_selection"
const val WIDGET_BOOK_KEY = "selected_book_id"

// ── v2.4 rework: personalización de chips por appWidgetId ─────────────────────
data class WidgetDisplayConfig(
    val showEmojis: Boolean = true,
    val showDays: Boolean = true,
    val showTime: Boolean = true,
    val showSessions: Boolean = true,
    val showPages: Boolean = true,
    val showPercent: Boolean = true,
    // D: barra de progreso del widget. ON por defecto para no romper widgets ya configurados.
    val showProgressBar: Boolean = true
)

// v2.5: la configuración de chips pasa a ser GLOBAL (una sola para todos los widgets),
// editable desde Ajustes → WIDGET sin quitar/volver a poner el widget.
// Lectura con fallback legacy per-appWidgetId (configs guardadas en v2.4).
fun loadWidgetDisplayConfig(context: Context, appWidgetId: Int = 0): WidgetDisplayConfig {
    val p = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
    fun g(name: String): Boolean = when {
        p.contains("cfg_global_$name") -> p.getBoolean("cfg_global_$name", true)
        appWidgetId != 0 && p.contains("cfg_${appWidgetId}_$name") -> p.getBoolean("cfg_${appWidgetId}_$name", true)
        else -> true
    }
    return WidgetDisplayConfig(
        showEmojis   = g("emojis"),
        showDays     = g("days"),
        showTime     = g("time"),
        showSessions = g("sessions"),
        showPages    = g("pages"),
        showPercent  = g("percent"),
        showProgressBar = g("progress")
    )
}

fun saveWidgetDisplayConfig(context: Context, appWidgetId: Int, cfg: WidgetDisplayConfig) {
    val p = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
    p.edit()
        .putBoolean("cfg_global_emojis",   cfg.showEmojis)
        .putBoolean("cfg_global_days",     cfg.showDays)
        .putBoolean("cfg_global_time",     cfg.showTime)
        .putBoolean("cfg_global_sessions", cfg.showSessions)
        .putBoolean("cfg_global_pages",    cfg.showPages)
        .putBoolean("cfg_global_percent",  cfg.showPercent)
        .putBoolean("cfg_global_progress", cfg.showProgressBar)
        .apply()
}

private const val WIDGET_COVER_MAX_SIZE = 300
private val widgetUpdateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

// ── Tema del widget (Bug fix v21.14): el widget no seguía el tema de la app,
// siempre se veía oscuro. Lee el mismo "theme_mode" que usa MainActivity y
// aplica fondo + colores de texto a juego, sin tocar el layout XML base. ──
// Feedback 17-07 (Bloque 2): el acento del widget también sigue al tema — los overlays
// índigo translúcidos sobre el marrón de Cuero se veían "azul sucio". Cuero va en oro y
// Aurora en morado; Claro/Oscuro/OLED conservan el índigo y la barra sky de siempre.
internal data class WidgetThemeColors(
    val bgDrawable: Int,
    val textMain: Int,
    val textMuted: Int,
    val accentChipDrawable: Int = R.drawable.widget_accent_bg_chip,
    val accentCoverDrawable: Int = R.drawable.widget_accent_bg_cover,
    val progressColor: Int = 0xFF0EA5E9.toInt()
)

internal fun resolveWidgetTheme(context: Context): WidgetThemeColors {
    val prefs = context.getSharedPreferences("lecturameter", Context.MODE_PRIVATE)
    val saved = prefs.getString("theme_mode", "dark") ?: "dark"
    // A7: si el trial caducó sin que el usuario abriera la app, el widget no puede seguir
    // mostrando un tema de pago. Si el tema guardado ya no está permitido, se cae a oscuro.
    val mode = com.lecturameter.ThemeMode.values().firstOrNull { it.value == saved }
    val effective = if (mode != null && !com.lecturameter.utils.Pro.themeAllowed(prefs, mode)) "dark" else saved
    return when (effective) {
        // Claro r2 "Papel" (18-07): textos en marrón tinta y acento verde botánico, a juego
        // con el rediseño del tema (antes: textos azul pizarra y overlays índigo)
        "light"  -> WidgetThemeColors(
            R.drawable.widget_background_light, 0xFF22201B.toInt(), 0xFF5A5648.toInt(),
            R.drawable.widget_accent_bg_chip_light, R.drawable.widget_accent_bg_cover_light, 0xFF166534.toInt()
        )
        // Fase 3 (Aurora C): textos teal claro a juego con el rediseño teal→púrpura
        "aurora" -> WidgetThemeColors(
            R.drawable.widget_background_aurora, 0xFFF0FDFB.toInt(), 0xFF9CCFC8.toInt(),
            R.drawable.widget_accent_bg_chip_aurora, R.drawable.widget_accent_bg_cover_aurora, 0xFFB794F6.toInt()
        )
        // QA r2 12-07: Dinámico eliminado — "dynamic" residual cae al else (oscuro)
        // 18-07: en AMOLED los azules pasan a gris (decisión de Víctor)
        "amoled" -> WidgetThemeColors(
            R.drawable.widget_background_amoled, 0xFFF1F5F9.toInt(), 0xFF94A3B8.toInt(),
            R.drawable.widget_accent_bg_chip_amoled, R.drawable.widget_accent_bg_cover_amoled, 0xFFD4D4D8.toInt()
        )
        // D-015 (Cuero): fondo marrón cuero + textos crema del mockup r3, acento oro
        "cuero"  -> WidgetThemeColors(
            R.drawable.widget_background_cuero, 0xFFFAF3E3.toInt(), 0xFFD6C7A5.toInt(),
            R.drawable.widget_accent_bg_chip_cuero, R.drawable.widget_accent_bg_cover_cuero, 0xFFD9AC5C.toInt()
        )
        else     -> WidgetThemeColors(R.drawable.widget_background_dark, 0xFFF1F5F9.toInt(), 0xFF94A3B8.toInt())
    }
}

fun saveWidgetBook(context: Context, bookId: Long) {
    context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putLong(WIDGET_BOOK_KEY, bookId)
        .commit()
}

fun loadWidgetBook(context: Context): Long =
    context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
        .getLong(WIDGET_BOOK_KEY, -1L)

fun clearWidgetCoverCache(context: Context) {
    try {
        context.applicationContext.cacheDir
            .listFiles { file -> file.name.startsWith("widget_cover_") }
            ?.forEach { it.delete() }
    } catch (_: Exception) {
    }
}

fun requestBookWidgetUpdate(context: Context) {
    val appContext = context.applicationContext
    widgetUpdateScope.launch {
        try {
            updateBookWidgets(appContext)
        } catch (_: Exception) {
        }
    }
}

// Fase 2: el refresco delega en Glance.
// B-009 r2: updateAll() por sí solo NO recompone una sesión viva — Glance solo invalida
// la composición cuando cambia su ESTADO (LocalState); leer SharedPreferences dentro de
// la composición no es observable. Por eso el widget solo se actualizaba al quitarlo y
// volverlo a poner (sesión nueva). Ahora, antes de update(), se escribe un tick en el
// estado Glance de cada instancia; la composición lo lee con currentState() y cada
// escritura fuerza la recomposición (que relee prefs, libro, sesiones, tema y config).
val WIDGET_REFRESH_TICK = androidx.datastore.preferences.core.longPreferencesKey("refresh_tick")

suspend fun updateBookWidgets(context: Context) = withContext(Dispatchers.IO) {
    val appContext = context.applicationContext
    val widget = BookGlanceWidget()
    val ids = androidx.glance.appwidget.GlanceAppWidgetManager(appContext)
        .getGlanceIds(BookGlanceWidget::class.java)
    ids.forEach { id ->
        androidx.glance.appwidget.state.updateAppWidgetState(appContext, id) { prefs ->
            prefs[WIDGET_REFRESH_TICK] = System.currentTimeMillis()
        }
        widget.update(appContext, id)
    }
}

// Fase 2: mismo nombre de componente que en 2.7 (declarado en el manifest) para que
// los widgets ya colocados en el escritorio sobrevivan a la actualización.
class BookWidgetReceiver : androidx.glance.appwidget.GlanceAppWidgetReceiver() {

    override val glanceAppWidget: androidx.glance.appwidget.GlanceAppWidget = BookGlanceWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // El broadcast propio de la app (TimerService, Ajustes…) sigue funcionando
        if (intent.action == ACTION_REFRESH_WIDGET) {
            requestBookWidgetUpdate(context)
        }
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.lecturameter.REFRESH_WIDGET"
    }
}

// v21.41: el widget corre fuera del proceso de la app; fuerza el locale elegido por el usuario
internal fun appLocalizedContext(context: Context): Context {
    return try {
        val prefs = context.getSharedPreferences("lecturameter", Context.MODE_PRIVATE)
        val lang = prefs.getString("app_language", "es") ?: "es"
        val locale = java.util.Locale(lang)
        val config = android.content.res.Configuration(context.resources.configuration)
        config.setLocale(locale)
        context.createConfigurationContext(config)
    } catch (_: Exception) {
        context
    }
}

// Fase 2: el render RemoteViews de 2.7 vive como fallback en LegacyBookWidgetReceiver.kt

// B-009: versión bloqueante para usar DENTRO de la composición de Glance (que no es
// suspend). Glance compone fuera del main thread; la portada casi siempre viene de la
// caché de disco y la descarga tiene timeout de 2,5 s.
internal fun loadCoverBitmapBlocking(context: Context, coverUrl: String): Bitmap? =
    kotlinx.coroutines.runBlocking { loadCoverBitmap(context, coverUrl) }

suspend fun loadCoverBitmap(context: Context, coverUrl: String): Bitmap? =
    withContext(Dispatchers.IO) {
        val normalizedUrl = coverUrl.trim()
        try {
            when {
                normalizedUrl.startsWith("/") -> {
                    val file = File(normalizedUrl)
                    if (file.exists()) decodeWidgetBitmapFile(file) else null
                }
                normalizedUrl.startsWith("file://") -> {
                    val path = Uri.parse(normalizedUrl).path ?: return@withContext null
                    val file = File(path)
                    if (file.exists()) decodeWidgetBitmapFile(file) else null
                }
                normalizedUrl.startsWith("content://") -> {
                    val bytes = context.contentResolver.openInputStream(Uri.parse(normalizedUrl))
                        ?.use { it.readBytes() }
                    bytes?.let { decodeWidgetBitmapBytes(it) }
                }
                normalizedUrl.startsWith("http://") || normalizedUrl.startsWith("https://") -> {
                    val cacheFile = File(context.cacheDir, "widget_cover_${normalizedUrl.hashCode()}.jpg")
                    val bytes: ByteArray? = if (cacheFile.exists() && cacheFile.length() > 0) {
                        cacheFile.readBytes()
                    } else {
                        val conn = URL(normalizedUrl).openConnection() as HttpURLConnection
                        try {
                            conn.connectTimeout = 2500
                            conn.readTimeout = 2500
                            // Seguridad: rechazar descargas > 5 MB para evitar OOM
                            val contentLength = conn.contentLengthLong
                            if (contentLength > 5_000_000L) {
                                null
                            } else {
                                conn.inputStream.use { it.readBytes() }
                                    .also { cacheFile.writeBytes(it) }
                            }
                        } finally {
                            conn.disconnect()
                        }
                    }
                    bytes?.let { decodeWidgetBitmapBytes(it) }
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

private fun decodeWidgetBitmapFile(file: File): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateWidgetSampleSize(bounds.outWidth, bounds.outHeight)
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    return BitmapFactory.decodeFile(file.absolutePath, options)
}

private fun decodeWidgetBitmapBytes(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateWidgetSampleSize(bounds.outWidth, bounds.outHeight)
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

private fun calculateWidgetSampleSize(width: Int, height: Int): Int {
    if (width <= 0 || height <= 0) return 1

    var sampleSize = 1
    var scaledWidth = width
    var scaledHeight = height
    while (scaledWidth > WIDGET_COVER_MAX_SIZE || scaledHeight > WIDGET_COVER_MAX_SIZE) {
        sampleSize *= 2
        scaledWidth /= 2
        scaledHeight /= 2
    }
    return sampleSize
}

// Fase 2: WidgetStats y computeWidgetStats() viven en utils/WidgetStatsUtils.kt
// (compartidos entre el widget y TimerService, como marca el plan).

fun currentTime(): String =
    SimpleDateFormat("HH:mm · dd/MM/yyyy", Locale.getDefault()).format(Date())

fun formatMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h == 0) "${m}m" else if (m == 0) "${h}h" else "${h}h${m}m"
}

fun loadBookById(context: Context, bookId: Long): Book? {
    if (bookId == -1L) return null
    return try {
        val prefs = context.getSharedPreferences("lecturameter", Context.MODE_PRIVATE)
        val json = prefs.getString("books", null) ?: return null
        val books: List<Book> = Gson().fromJson(
            json,
            object : TypeToken<List<Book>>() {}.type
        ) ?: return null
        // Gson devuelve null en campos List con default en Kotlin si el JSON es de version anterior
        books.firstOrNull { it.id == bookId }?.let { b ->
            b.copy(
                title      = b.title      ?: "",
                author     = b.author     ?: "",
                genres     = b.genres     ?: emptyList(),
                editions   = b.editions   ?: emptyList(),
                dateEvents = b.dateEvents ?: emptyList()   // v19.9 fix: Gson pone null en campo nuevo
            )
        }
    } catch (_: Exception) {
        null
    }
}

fun loadSessions(context: Context): List<ReadingSession> {
    return try {
        val prefs = context.getSharedPreferences("lecturameter", Context.MODE_PRIVATE)
        val json = prefs.getString("sessions", null) ?: return emptyList()
        Gson().fromJson(
            json,
            object : TypeToken<List<ReadingSession>>() {}.type
        ) ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }
}
