package com.lecturameter.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import com.lecturameter.R
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

// Esta Activity solo se llama cuando el sistema coloca el widget por primera vez.
// La selección del libro se hace desde los detalles del libro en la app principal.
// Devuelve RESULT_OK inmediatamente y abre la app para que el usuario elija.
class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    companion object {
        const val EXTRA_FROM_WIDGET = "from_widget"
        const val EXTRA_GLANCE_ID   = "glance_id_str"
        const val PREFS_WIDGET      = "widget_book_selection"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // Devolver OK para que el sistema coloque el widget
        setResult(Activity.RESULT_OK, Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        })

        val appCtx = applicationContext

        setContent { PlacedScreen() }

        // Actualizar el widget en background y cerrar
        lifecycleScope.launch {
            updateBookWidgets(appCtx)
            finish()
        }
    }
}

@Composable
fun PlacedScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text("📚", fontSize = 48.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.widget_added),
                color = Color(0xFFF0F0F0),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.widget_choose_book_help),
                color = Color(0xFF8899AA),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
