package com.lecturameter

// v2.4 rework: componente reutilizable de swipe para marcar/desmarcar favorito.
// - Swipe izquierda o derecha alterna isFavorite (la dirección no importa)
// - Franja izquierda de 4dp #CC4A4A visible solo cuando el libro es favorito
// - Corazón animado (scale + alpha) que aparece durante el swipe
// - El feedback (Snackbar) lo emite el llamador vía onToggleFavorite

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Rojo de favorito del mockup (franja + fondo de acción). */
val FavoriteRed = Color(0xFFCC4A4A)

@Composable
fun FavoriteSwipe(
    isFavorite: Boolean,
    cornerRadius: Int = 16,
    onToggleFavorite: () -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    // Umbral y desplazamiento máximo en px
    val maxDragPx = with(density) { 120.dp.toPx() }
    val thresholdPx = with(density) { 56.dp.toPx() }
    var toggledThisDrag by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { toggledThisDrag = false },
                    onDragEnd = {
                        val passed = kotlin.math.abs(offsetX.value) >= thresholdPx
                        if (passed && !toggledThisDrag) {
                            toggledThisDrag = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleFavorite()
                        }
                        scope.launch { offsetX.animateTo(0f, tween(280)) }
                    },
                    onDragCancel = {
                        scope.launch { offsetX.animateTo(0f, tween(280)) }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val newValue = (offsetX.value + dragAmount).coerceIn(-maxDragPx, maxDragPx)
                        scope.launch { offsetX.snapTo(newValue) }
                    }
                )
            }
    ) {
        // ── Capa de acción (detrás): fondo rojo + corazón animado durante el swipe ──
        val progress = (kotlin.math.abs(offsetX.value) / thresholdPx).coerceIn(0f, 1f)
        if (progress > 0.01f) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(cornerRadius.dp))
                    .background(FavoriteRed.copy(alpha = 0.25f + 0.55f * progress)),
                contentAlignment = if (offsetX.value < 0) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Text(
                    if (isFavorite) "\uD83D\uDC94" else "\u2764\uFE0F",   // 💔 al desmarcar, ❤️ al marcar
                    fontSize = 26.sp,
                    modifier = Modifier
                        .padding(horizontal = 22.dp)
                        .scale(0.5f + 0.5f * progress)
                        .alpha(progress)
                )
            }
        }

        // ── Capa de contenido (tarjeta) desplazada + franja de favorito ──────────
        Box(
            Modifier
                .graphicsLayer { translationX = offsetX.value }
                .then(
                    if (isFavorite) Modifier.drawWithContent {
                        drawContent()
                        // v2.5: barra roja curvada — clipPath con RoundRect idéntico
                        // al shape de la card. drawRect dentro del clip sigue las curvas.
                        val barW = 5.dp.toPx()
                        val r = cornerRadius.dp.toPx()
                        clipPath(Path().apply {
                            addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r)))
                        }) {
                            drawRect(
                                color = FavoriteRed,
                                topLeft = Offset.Zero,
                                size = Size(barW, size.height)
                            )
                        }
                    } else Modifier
                )
        ) {
            content()
        }
    }
}
