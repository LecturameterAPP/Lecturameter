package com.lecturameter.bookquest

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.Base64
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.lecturameter.BooksViewModel

// Bridge expuesto al JS bajo window.Android.
// Convierte URIs locales (content://, rutas absolutas) a base64 para que el
// WebView pueda mostrarlas como <img src="data:image/jpeg;base64,...">
// También persiste la biblioteca entre sesiones de Book Quest.
class BookCoverBridge(private val context: Context, private val onExit: (() -> Unit)? = null) {

    private val prefs by lazy {
        context.getSharedPreferences("bookquest_prefs", Context.MODE_PRIVATE)
    }

    @JavascriptInterface
    fun getCoverBase64(path: String): String {
        return try {
            val bytes: ByteArray? = when {
                path.startsWith("content://") -> {
                    context.contentResolver.openInputStream(android.net.Uri.parse(path))
                        ?.use { it.readBytes() }
                }
                path.startsWith("/") -> {
                    // Seguridad: solo permitir leer archivos dentro de los directorios
                    // privados de la app (filesDir / cacheDir). Bloquea path traversal a
                    // /data/data/<otros>/, /sdcard/, /etc/, etc.
                    val canonical = java.io.File(path).canonicalPath
                    val allowedPrefixes = listOfNotNull(
                        context.filesDir.canonicalPath,
                        context.cacheDir.canonicalPath,
                        context.getExternalFilesDir(null)?.canonicalPath
                    )
                    val inAllowed = allowedPrefixes.any { canonical.startsWith(it + java.io.File.separator) || canonical == it }
                    if (!inAllowed) return ""
                    val file = java.io.File(canonical)
                    if (file.exists() && file.length() < 5_000_000L) file.readBytes() else null
                }
                else -> null
            }
            if (bytes != null) Base64.encodeToString(bytes, Base64.NO_WRAP)
            else ""
        } catch (_: Exception) { "" }
    }

    
    @JavascriptInterface
    fun openJsonPicker() {
        // Placeholder bridge to ensure Android interface exists for file picker support.
    }

    /** Llamado desde el botón "Salir del juego" en el HTML para volver a la app. */
    @JavascriptInterface
    fun exitGame() {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.post { onExit?.invoke() }
    }


    /** Guarda el JSON de la biblioteca para la próxima sesión. */
    @JavascriptInterface
    fun saveLibrary(json: String) {
        try { prefs.edit().putString("library_json", json).apply() }
        catch (_: Exception) {}
    }

    /** Devuelve el JSON guardado de la sesión anterior, o "" si no hay nada. */
    @JavascriptInterface
    fun loadLibrary(): String = try {
        prefs.getString("library_json", "") ?: ""
    } catch (_: Exception) { "" }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BookQuestScreen(
    vm: BooksViewModel,
    onExit: () -> Unit
) {

    val fileChooserCallback = remember { arrayOf<ValueCallback<Array<Uri>>?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        val cb = fileChooserCallback[0]
        if (uri != null) cb?.onReceiveValue(arrayOf(uri))
        else cb?.onReceiveValue(null)
        fileChooserCallback[0] = null
    }

    BackHandler {
        onExit()
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),

        factory = { context ->

            WebView(context).apply {

                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                // =========================
                // WEBVIEW CLIENT
                // =========================

                webViewClient = object : WebViewClient() {

                    override fun onPageFinished(
                        view: WebView?,
                        url: String?
                    ) {
                        super.onPageFinished(view, url)

                        // Idioma actual de la app -> JS (window.setBookQuestLanguage)
                        val lang = context.getSharedPreferences("lecturameter", Context.MODE_PRIVATE)
                            .getString("app_language", "es") ?: "es"
                        view?.evaluateJavascript(
                            """
                            (function() {
                                if (window.setBookQuestLanguage) {
                                    window.setBookQuestLanguage('$lang');
                                } else {
                                    setTimeout(function() {
                                        if (window.setBookQuestLanguage) window.setBookQuestLanguage('$lang');
                                    }, 200);
                                }
                            })();
                            """.trimIndent(),
                            null
                        )

                        if (com.lecturameter.BuildConfig.DEBUG) {
                            Log.d(
                                "BOOKQUEST",
                                "HTML cargado correctamente: $url"
                            )
                        }
                    }
                }

                // =========================
                // JS CONSOLE
                // =========================

                webChromeClient = object : WebChromeClient() {

                    override fun onConsoleMessage(
                        consoleMessage: ConsoleMessage
                    ): Boolean {

                        if (com.lecturameter.BuildConfig.DEBUG) {
                            Log.d(
                                "BOOKQUEST_JS",
                                consoleMessage.message()
                            )
                        }

                        return true
                    }

                    override fun onShowFileChooser(
                        webView: WebView?,
                        filePathCallbackParam: ValueCallback<Array<Uri>>?,
                        fileChooserParams: FileChooserParams?
                    ): Boolean {
                        return try {
                            fileChooserCallback[0]?.onReceiveValue(null)
                            fileChooserCallback[0] = filePathCallbackParam

                            filePickerLauncher.launch("application/json")
                            true
                        } catch (_: Exception) {
                            filePathCallbackParam?.onReceiveValue(null)
                            false
                        }
                    }
                }

                // =========================
                // SETTINGS
                // =========================

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true

                // Seguridad: el HTML es local en assets y solo carga imágenes vía el bridge
                // o URLs HTTPS de OpenLibrary. Bloqueamos acceso a file:// y content://
                // desde el propio HTML para reducir superficie de ataque.
                settings.allowFileAccess = false
                settings.allowContentAccess = false

                settings.loadsImagesAutomatically = true

                settings.cacheMode =
                    WebSettings.LOAD_NO_CACHE

                // COMPATIBILITY_MODE en vez de ALWAYS_ALLOW: bloquea recursos HTTP
                // activos desde un origen HTTPS, pero permite pasivos (imágenes).
                settings.mixedContentMode =
                    WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                // =========================
                // JAVASCRIPT INTERFACE
                // =========================

                // Expone window.Android.getCoverBase64(path) al HTML.
                // Permite cargar portadas guardadas localmente (content://, rutas absolutas)
                // que el WebView no puede cargar directamente desde HTML.
                addJavascriptInterface(BookCoverBridge(context, onExit), "Android")

                // =========================
                // VISUAL
                // =========================

                setBackgroundColor(Color.BLACK)

                // =========================
                // DEBUG (solo en builds de debug)
                // =========================

                if (com.lecturameter.BuildConfig.DEBUG) {
                    WebView.setWebContentsDebuggingEnabled(true)
                }
                // =========================
                // LOAD HTML
                // =========================

                loadUrl(
                    "file:///android_asset/book_quest.html"
                )
            }
        }
    )
}