package com.lecturameter

import android.app.Activity
import androidx.activity.ComponentActivity
import android.content.Intent
import android.os.Bundle
import android.util.Size
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Activity de escaneo de ISBN usando CameraX + ML Kit Barcode.
 * Devuelve el ISBN escaneado via setResult(RESULT_OK, Intent().putExtra("isbn", value)).
 * Se lanza desde MainActivity con scanIsbnLauncher.
 */
class ScannerActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private var scanned = false

    @androidx.camera.core.ExperimentalGetImage
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Layout programático para no necesitar xml de recursos extra
        val root = FrameLayout(this)
        root.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        root.setBackgroundColor(0xFF000000.toInt())

        // Preview de cámara
        val previewView = PreviewView(this)
        previewView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
        root.addView(previewView)

        // Overlay de instrucciones — fondo semitransparente separado del botón
        val hint = TextView(this)
        // NO prometer ISSN aquí: ninguna fuente gratuita resuelve un ISSN. Comic Vine, que
        // era la candidata, no indexa ISBN, ISSN ni UPC (verificado el 21-07-2026 contra su
        // documentación de campos). Además un ISSN es un EAN-13 que empieza por 977 y pasa
        // el mismo dígito de control que un ISBN-13, así que el escáner lo acepta y luego
        // no lo encuentra en ninguna fuente: prometerlo sería garantizar la decepción.
        // Extraído a string resource de paso: estaba hardcodeado en español y a un usuario
        // en inglés le salía en español.
        hint.text = getString(R.string.scanner_hint_isbn)
        hint.setTextColor(0xFFFFFFFF.toInt())   // blanco (#3)
        hint.textSize = 14f
        hint.setPadding(32, 20, 32, 20)
        hint.setBackgroundColor(0xCC000000.toInt())
        val hintParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        hintParams.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
        hintParams.bottomMargin = 200   // espacio suficiente para el botón debajo (#5)
        hint.gravity = android.view.Gravity.CENTER
        hint.layoutParams = hintParams
        root.addView(hint)

        // Botón salir — anclado al fondo, franja propia sin solapar (#5)
        val closeBtn = TextView(this)
        closeBtn.text = "✕  " + getString(R.string.scanner_exit)   // (#4)
        closeBtn.setTextColor(0xFFFF4444.toInt())   // rojo (#3)
        closeBtn.textSize = 14f
        closeBtn.setPadding(60, 28, 60, 28)
        closeBtn.setBackgroundColor(0xCC000000.toInt())
        val closeParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        closeParams.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
        closeParams.bottomMargin = 100   // margen barra navegación (#5)
        closeBtn.gravity = android.view.Gravity.CENTER
        closeBtn.layoutParams = closeParams
        closeBtn.setOnClickListener { setResult(RESULT_CANCELED); finish() }
        root.addView(closeBtn)

        setContentView(root)
        startCamera(previewView)
    }

    @androidx.camera.core.ExperimentalGetImage
    private fun startCamera(previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            // B-027: sin opciones, ML Kit busca TODOS los formatos (QR, ITF, Code-128,
            // Aztec…). Los libros solo llevan EAN-13 (el ISBN) y, como mucho, EAN-8; el
            // resto de códigos de la contraportada son del distribuidor y no deben
            // competir. Restringirlo además acelera el reconocimiento.
            val scanner = BarcodeScanning.getClient(
                com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8)
                    .build()
            )

            imageAnalysis.setAnalyzer(cameraExecutor, BarcodeAnalyzer(
                scanner,
                onIsbn = { isbn ->
                    if (!scanned) {
                        scanned = true
                        val result = Intent().putExtra("isbn", isbn)
                        setResult(RESULT_OK, result)
                        finish()
                    }
                },
                onIssn = { issn ->
                    // Codigo ISSN (revista/grapa, prefijo 977): la app escanea ISBN y ninguna
                    // fuente gratuita resuelve ISSN de comic. Se devuelve a la app (extra "issn")
                    // para que muestre un aviso CLARO alli, en vez de cambiar el texto del propio
                    // escaner. El usuario ve el mensaje al volver a la app.
                    if (!scanned) {
                        scanned = true
                        setResult(RESULT_OK, Intent().putExtra("issn", issn))
                        finish()
                    }
                }
            ))

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                setResult(RESULT_CANCELED)
                finish()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

/**
 * B-027: valida un ISBN de verdad (longitud, prefijo y dígito de control).
 * Un EAN-13 mal leído casi nunca supera el checksum, así que esto filtra la mayoría
 * de lecturas parciales y de códigos que no son ISBN.
 */
internal fun isValidIsbn(raw: String): Boolean {
    val s = raw.uppercase()
    return when {
        s.length == 13 && s.all { it.isDigit() } && (s.startsWith("978") || s.startsWith("979")) -> {
            val sum = s.take(12).mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 1 else 3 }.sum()
            (10 - sum % 10) % 10 == s[12] - '0'
        }
        // Ojo: "0000000000" cuadra el mod 11 (suma 0) pero no es un ISBN — se descarta.
        s.length == 10 && s.take(9).all { it.isDigit() } && (s[9].isDigit() || s[9] == 'X') &&
        s.take(9).any { it != '0' } -> {
            val sum = s.take(9).mapIndexed { i, c -> (c - '0') * (10 - i) }.sum() +
                      (if (s[9] == 'X') 10 else s[9] - '0')
            sum % 11 == 0
        }
        else -> false
    }
}

@androidx.camera.core.ExperimentalGetImage
private class BarcodeAnalyzer(
    private val scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    private val onIsbn: (String) -> Unit,
    private val onIssn: (String) -> Unit = {}
) : ImageAnalysis.Analyzer {

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) { imageProxy.close(); return }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                // B-027: antes se aceptaba el PRIMER código que cuadrase de forma laxa:
                // cualquier cadena de 10 caracteres (¡incluso con letras!) pasaba por
                // "ISBN-10", y no se validaba el dígito de control. Con el escáner sin
                // restringir formatos, un código del distribuidor (ITF/Code-128) podía
                // colarse como ISBN. Ahora: solo códigos de libro, checksum obligatorio, y
                // si en la contraportada hay varios se elige el que de verdad es un ISBN.
                val cleaned = barcodes.mapNotNull { it.rawValue?.replace("-", "")?.replace(" ", "") }
                val isbn = cleaned.firstOrNull { isValidIsbn(it) }
                if (isbn != null) onIsbn(isbn)
                // Fleco ISSN (22-07): un ISSN es un EAN-13 con prefijo 977 que pasa el checksum
                // pero NO es un ISBN, asi que isValidIsbn lo rechaza. Antes se ignoraba en
                // silencio; ahora se devuelve a la app para avisar de que se anada a mano.
                // El EAN de una revista (977) puede venir con suplemento (add-on de 2/5 digitos):
                // rawValue podria traer mas de 13 chars. Se mira el prefijo 977 y los 13 primeros.
                else cleaned.firstOrNull { it.startsWith("977") && it.length >= 13 && it.take(13).all { c -> c.isDigit() } }?.let { onIssn(it.take(13)) }
            }
            .addOnCompleteListener { imageProxy.close() }
    }
}
