package com.lecturameter

// StatsWidgetConfigScreen: configuración del widget Pro de estadísticas (comparativa
// mensual). Antes eran 4 switches inline en Ajustes > Widget; ahora es una pantalla
// propia con el mismo patrón de cabecera que el resto de pantallas secundarias.

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StatsWidgetConfigScreen(prefs: android.content.SharedPreferences, theme: Theme, onBack: () -> Unit) {
    val context = LocalContext.current
    val acc = accentForTheme(theme)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        // Header, mismo patrón que el resto de pantallas secundarias
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 32.dp, bottom = 24.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = theme.textMain) }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.sw_config_entry_title), color = theme.textMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.sw_config_entry_subtitle), color = theme.textMuted, fontSize = 12.sp)
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = theme.surface,
            border = BorderStroke(1.dp, theme.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.sw_config_bars_title), color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.sw_config_bars_subtitle), color = theme.textMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                val barKeys = listOf(
                    "sw_hide_pages" to R.string.sw_label_pages_short,
                    "sw_hide_time" to R.string.sw_label_time_short,
                    "sw_hide_streak" to R.string.sw_label_streak_short,
                    "sw_hide_sessions" to R.string.sw_label_sessions_short
                )
                barKeys.forEach { (key, labelRes) ->
                    var shown by remember { mutableStateOf(!prefs.getBoolean(key, false)) }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(labelRes), color = theme.textMain, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = shown,
                            onCheckedChange = { newVal ->
                                shown = newVal
                                prefs.edit().putBoolean(key, !newVal).apply()
                                com.lecturameter.widget.requestStatsWidgetUpdate(context)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = acc, checkedTrackColor = acc.copy(alpha = 0.4f))
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}
