package com.lecturameter.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import com.lecturameter.R
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

// v2.4 rework: pantalla de configuración real del widget.
// Se abre al colocar el widget (y al reconfigurar en launchers compatibles) y
// permite elegir qué chips mostrar y si llevan emojis. Persistencia por appWidgetId.
class WidgetConfigActivity : ComponentActivity() {

    // v2.5: mismo mecanismo de idioma que MainActivity (contexto inmutable)
    override fun attachBaseContext(newBase: android.content.Context) {
        val lang = newBase.getSharedPreferences("lecturameter", android.content.Context.MODE_PRIVATE)
            .getString("app_language", "es") ?: "es"
        val locale = java.util.Locale(lang)
        java.util.Locale.setDefault(locale)
        val config = android.content.res.Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

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

        // v2.5: también se puede abrir desde Ajustes → WIDGET (sin appWidgetId) para
        // editar la configuración global sin quitar/volver a poner el widget.
        val fromSettings = appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID

        if (!fromSettings) {
            // Cancelado por defecto: si el usuario sale sin guardar al COLOCAR el widget,
            // el sistema lo descarta. Al pulsar Guardar devolvemos RESULT_OK.
            setResult(Activity.RESULT_CANCELED, Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            })
        }

        val appCtx = applicationContext

        setContent {
            WidgetConfigScreen(
                initial = loadWidgetDisplayConfig(appCtx, appWidgetId),
                onSave = { cfg ->
                    saveWidgetDisplayConfig(appCtx, appWidgetId, cfg)
                    if (!fromSettings) {
                        setResult(Activity.RESULT_OK, Intent().apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        })
                    }
                    lifecycleScope.launch {
                        updateBookWidgets(appCtx)
                        finish()
                    }
                }
            )
        }
    }
}

@Composable
private fun WidgetConfigSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color(0xFFF0F0F0), fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF6366F1),
                uncheckedTrackColor = Color(0xFF3D4166)
            )
        )
    }
}

@Composable
fun WidgetConfigScreen(initial: WidgetDisplayConfig, onSave: (WidgetDisplayConfig) -> Unit) {
    var showEmojis by remember { mutableStateOf(initial.showEmojis) }
    var showDays by remember { mutableStateOf(initial.showDays) }
    var showTime by remember { mutableStateOf(initial.showTime) }
    var showSessions by remember { mutableStateOf(initial.showSessions) }
    var showPages by remember { mutableStateOf(initial.showPages) }
    var showPercent by remember { mutableStateOf(initial.showPercent) }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A2E)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())
        ) {
            Text("📚", fontSize = 40.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.widget_config_title),
                color = Color(0xFFF0F0F0), fontSize = 20.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.widget_choose_book_help),
                color = Color(0xFF8899AA), fontSize = 13.sp
            )
            Spacer(Modifier.height(20.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF232342),
                border = BorderStroke(1.dp, Color(0xFF3D4166)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    // Feedback 14-07 (F2): el toggle de emojis deja de ser "master" (B-011
                    // revertido) — es un toggle independiente: solo controla si los chips
                    // llevan emoji, no qué chips se muestran
                    WidgetConfigSwitchRow(stringResource(R.string.widget_cfg_emojis), showEmojis) { showEmojis = it }
                    WidgetConfigSwitchRow(stringResource(R.string.widget_cfg_days), showDays) { showDays = it }
                    WidgetConfigSwitchRow(stringResource(R.string.widget_cfg_time), showTime) { showTime = it }
                    WidgetConfigSwitchRow(stringResource(R.string.widget_cfg_sessions), showSessions) { showSessions = it }
                    WidgetConfigSwitchRow(stringResource(R.string.widget_cfg_pages), showPages) { showPages = it }
                    WidgetConfigSwitchRow(stringResource(R.string.widget_cfg_percent), showPercent) { showPercent = it }
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    onSave(
                        WidgetDisplayConfig(
                            showEmojis = showEmojis,
                            showDays = showDays,
                            showTime = showTime,
                            showSessions = showSessions,
                            showPages = showPages,
                            showPercent = showPercent
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(stringResource(R.string.widget_cfg_save), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}
