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
        hint.text = "📷  Apunta al código de barras del libro"
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
        closeBtn.text = "✕  Salir"   // (#4)
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

            val scanner = BarcodeScanning.getClient()

            imageAnalysis.setAnalyzer(cameraExecutor, BarcodeAnalyzer(scanner) { isbn ->
                if (!scanned) {
                    scanned = true
                    val result = Intent().putExtra("isbn", isbn)
                    setResult(RESULT_OK, result)
                    finish()
                }
            })

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

@androidx.camera.core.ExperimentalGetImage
private class BarcodeAnalyzer(
    private val scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    private val onIsbn: (String) -> Unit
) : ImageAnalysis.Analyzer {

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) { imageProxy.close(); return }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    val raw = barcode.rawValue ?: continue
                    val clean = raw.replace("-", "").replace(" ", "")
                    val isIsbn = (clean.length == 13 && (clean.startsWith("978") || clean.startsWith("979"))) ||
                                 (clean.length == 10)
                    if (isIsbn) { onIsbn(clean); break }
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }
}
