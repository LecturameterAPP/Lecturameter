package com.lecturameter

// PrivacyPolicyScreen: pantalla in-app con la política de privacidad de Lecturameter.
// Añadida el 16-07-2026 (rama lanzamiento, TAREA 1).

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Un bloque = título de sección + cuerpo del párrafo.
private data class PrivacySection(val titleRes: Int, val bodyRes: Int)

@Composable
fun PrivacyPolicyScreen(theme: Theme, onBack: () -> Unit) {
    val sections = listOf(
        PrivacySection(R.string.privacy_policy_section_storage_title, R.string.privacy_policy_storage_body),
        PrivacySection(R.string.privacy_policy_section_analytics_title, R.string.privacy_policy_analytics_body),
        PrivacySection(R.string.privacy_policy_section_search_title, R.string.privacy_policy_search_body),
        PrivacySection(R.string.privacy_policy_section_feedback_title, R.string.privacy_policy_feedback_body),
        PrivacySection(R.string.privacy_policy_section_pro_title, R.string.privacy_policy_pro_body),
        PrivacySection(R.string.privacy_policy_section_permissions_title, R.string.privacy_policy_permissions_body),
        PrivacySection(R.string.privacy_policy_section_contact_title, R.string.privacy_policy_contact_body),
        PrivacySection(R.string.privacy_policy_section_changes_title, R.string.privacy_policy_changes_body)
    )

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        // Header, mismo patrón que el resto de pantallas secundarias
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 32.dp, bottom = 24.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = theme.textMain) }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.privacy_policy_title), color = theme.textMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.privacy_policy_updated), color = theme.textMuted, fontSize = 12.sp)
            }
        }

        Text(
            stringResource(R.string.privacy_policy_intro),
            color = theme.textMuted, fontSize = 13.sp, lineHeight = 18.sp,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        sections.forEach { section ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = theme.surface,
                border = BorderStroke(1.dp, theme.border),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(section.titleRes), color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(section.bodyRes), color = theme.textMuted, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}
