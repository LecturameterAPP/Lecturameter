package com.lecturameter

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lecturameter.repository.BingoRepository
import com.lecturameter.repository.BookRepository
import com.lecturameter.repository.ChallengeRepository
import com.lecturameter.repository.SessionRepository
import com.lecturameter.utils.computeMonthlyRecap
import com.lecturameter.utils.computeWeeklyRecap
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Worker periódico diario que notifica los resúmenes cuando están disponibles:
 *  - Semanal: el domingo, si la semana tiene sesiones (misma regla que la tarjeta in-app).
 *  - Mensual: los primeros días del mes, si el mes cerrado tiene sesiones (regla 6.4).
 *  - Wrapped anual: al abrirse la ventana (26-dic → 26-ene), si el año tiene sesiones.
 * Cada aviso se emite UNA vez por periodo (claves recap_notified_* en prefs).
 * La notificación abre la pantalla correspondiente vía lecturameter://recap/{tipo}.
 */
class RecapNotificationWorker(
    private val ctx: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(ctx, workerParams) {

    override suspend fun doWork(): Result {
        try {
            if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return Result.success()

            val prefs = ctx.getSharedPreferences("lecturameter", Context.MODE_PRIVATE)
            val books = BookRepository.loadOrNull(prefs) ?: return Result.success()
            val sessions = SessionRepository.loadOrNull(prefs) ?: emptyList()
            val cal = Calendar.getInstance()
            val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)

            // ── Semanal: domingo, semana con sesiones ──
            if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                val recap = computeWeeklyRecap(
                    books, sessions,
                    BingoRepository.loadOrNull(prefs),
                    ChallengeRepository.loadOrNull(prefs) ?: emptyList(),
                    todayIso
                )
                if (recap != null && prefs.getString("recap_notified_week", null) != recap.weekStartIso) {
                    notify(
                        NOTIF_ID_WEEKLY, "weekly",
                        ctx.getString(R.string.recap_notif_weekly_title),
                        ctx.getString(R.string.recap_notif_weekly_body, recap.pages, recap.sessionsCount)
                    )
                    prefs.edit().putString("recap_notified_week", recap.weekStartIso).apply()
                }
            }

            // ── Mensual: primeros 7 días del mes, mes cerrado con sesiones ──
            if (cal.get(Calendar.DAY_OF_MONTH) <= 7) {
                val recap = computeMonthlyRecap(books, sessions, todayIso)
                if (recap != null && prefs.getString("recap_notified_month", null) != recap.monthKey) {
                    notify(
                        NOTIF_ID_MONTHLY, "monthly",
                        ctx.getString(R.string.recap_notif_monthly_title),
                        ctx.getString(R.string.recap_notif_monthly_body, recap.pages)
                    )
                    prefs.edit().putString("recap_notified_month", recap.monthKey).apply()
                }
            }

            // ── Wrapped anual: ventana 26-dic → 26-ene, año con sesiones ──
            val wrapYear = wrappedWindowYear()
            if (wrapYear != -1 &&
                prefs.getInt("recap_notified_wrapped", -1) != wrapYear &&
                sessions.any { it.date.startsWith("$wrapYear-") }
            ) {
                notify(
                    NOTIF_ID_WRAPPED, "wrapped/$wrapYear",
                    ctx.getString(R.string.recap_notif_wrapped_title, wrapYear),
                    ctx.getString(R.string.recap_notif_wrapped_body)
                )
                prefs.edit().putInt("recap_notified_wrapped", wrapYear).apply()
            }
        } catch (_: Exception) {
            // No reintentar — el siguiente ciclo diario lo corrige
        }
        return Result.success()
    }

    private fun notify(id: Int, deepLinkPath: String, title: String, body: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, ctx.getString(R.string.recap_notif_channel), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("lecturameter://recap/$deepLinkPath")).apply {
            setClass(ctx, MainActivity::class.java)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pi = PendingIntent.getActivity(
            ctx, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, notif)
        } catch (_: SecurityException) {
            // Permiso revocado entre el check y el notify — silencioso
        }
    }

    companion object {
        const val CHANNEL_ID = "recaps"
        const val NOTIF_ID_WEEKLY = 201
        const val NOTIF_ID_MONTHLY = 202
        const val NOTIF_ID_WRAPPED = 203
    }
}
