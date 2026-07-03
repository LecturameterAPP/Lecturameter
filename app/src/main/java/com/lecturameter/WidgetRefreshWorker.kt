package com.lecturameter

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lecturameter.widget.updateBookWidgets

/**
 * Worker periódico que refresca el widget según el intervalo elegido en Ajustes
 * (widget_refresh_minutes: 30/60/90/120). Complementa al updatePeriodMillis del
 * sistema (3h fijo) que MIUI a menudo retrasa o ignora.
 */
class WidgetRefreshWorker(
    private val ctx: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(ctx, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            updateBookWidgets(ctx)
            Result.success()
        } catch (_: Exception) {
            Result.success()  // No reintentar — el siguiente ciclo lo corrige
        }
    }
}
