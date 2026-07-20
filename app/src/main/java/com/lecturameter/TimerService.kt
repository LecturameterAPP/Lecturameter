package com.lecturameter

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import kotlinx.coroutines.*

/**
 * Servicio foreground que mantiene vivo el cronómetro de lectura cuando la pantalla
 * está bloqueada o la app en segundo plano. Muestra una notificación persistente con
 * acciones Pause/Resume/Stop accesibles desde la pantalla de bloqueo.
 *
 * Estado expuesto vía objeto singleton TimerStateHolder para que el Composable
 * de MainActivity lo lea reactivamente.
 */
class TimerService : Service() {

    private val binder = LocalBinder()
    private var tickJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // v1.4: el Service corre con el locale del sistema; fuerza el idioma elegido en la app
    private fun lctx(): Context = try {
        val prefs = getSharedPreferences("lecturameter", Context.MODE_PRIVATE)
        val lang = com.lecturameter.utils.LanguageHelper.resolveLanguage(prefs)
        val config = android.content.res.Configuration(resources.configuration)
        config.setLocale(java.util.Locale(lang))
        createConfigurationContext(config)
    } catch (_: Exception) { this }

    inner class LocalBinder : Binder() {
        fun getService(): TimerService = this@TimerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START  -> {
                val bookId = intent.getLongExtra(EXTRA_BOOK_ID, -1L)
                val bookTitle = intent.getStringExtra(EXTRA_BOOK_TITLE) ?: ""
                if (bookId > 0L) TimerStateHolder.activeBookId = bookId
                if (bookTitle.isNotBlank()) TimerStateHolder.activeBookTitle = bookTitle
                if (TimerStateHolder.seconds == 0L) {
                    val runPrefs = getSharedPreferences(TIMER_PREFS, MODE_PRIVATE)
                    val savedSecs = runPrefs.getLong("running_seconds", 0L)
                    val savedBook = runPrefs.getLong("running_book_id", -1L)
                    if (savedSecs > 0L && (savedBook == bookId || savedBook < 0L)) {
                        TimerStateHolder.seconds = savedSecs
                    }
                }
                // Restore title from prefs if not provided in intent
                if (TimerStateHolder.activeBookTitle.isBlank()) {
                    val runPrefs = getSharedPreferences(TIMER_PREFS, MODE_PRIVATE)
                    TimerStateHolder.activeBookTitle = runPrefs.getString("running_book_title", "") ?: ""
                }
                startTimer()
            }
            null -> {
                val runPrefs = getSharedPreferences(TIMER_PREFS, MODE_PRIVATE)
                if (runPrefs.getBoolean("is_running", false)) {
                    val savedBook = runPrefs.getLong("running_book_id", -1L)
                    val savedSecs = runPrefs.getLong("running_seconds", 0L)
                    if (savedBook > 0L && savedSecs > 0L) {
                        TimerStateHolder.activeBookId = savedBook
                        TimerStateHolder.seconds = savedSecs
                        TimerStateHolder.activeBookTitle = runPrefs.getString("running_book_title", "") ?: ""
                        if (runPrefs.getBoolean("is_paused", false)) {
                            // Restore in paused state -- do not resume counting
                            TimerStateHolder.running = true
                            TimerStateHolder.paused = true
                            ensureForeground()
                        } else {
                            startTimer()
                        }
                    }
                }
            }
            ACTION_PAUSE  -> pauseTimer()
            ACTION_RESUME -> startTimer()
            ACTION_STOP   -> stopTimer(intent.getBooleanExtra(EXTRA_SHOW_END_NOTIFICATION, true))
        }
        return START_STICKY
    }

    private fun startTimer() {
        val runPrefs = getSharedPreferences(TIMER_PREFS, MODE_PRIVATE)
        val pausedSeconds = TimerStateHolder.seconds
        TimerStateHolder.startElapsedRealtime = SystemClock.elapsedRealtime()

        runPrefs.edit()
            .putBoolean("is_running", true)
            .putBoolean("is_paused", false)
            .putLong("running_book_id", TimerStateHolder.activeBookId)
            .putLong("running_seconds", pausedSeconds)
            .putString("running_book_title", TimerStateHolder.activeBookTitle)
            .apply()

        TimerStateHolder.running = true
        TimerStateHolder.paused = false
        TimerStateHolder.chronometerBase = System.currentTimeMillis() - pausedSeconds * 1000

        ensureForeground()
        tickJob?.cancel()
        tickJob = scope.launch {
            var tickCount = 0
            while (isActive && TimerStateHolder.running && !TimerStateHolder.paused) {
                delay(1000)
                val elapsed = (SystemClock.elapsedRealtime() - TimerStateHolder.startElapsedRealtime) / 1000L
                TimerStateHolder.seconds = pausedSeconds + elapsed
                tickCount++
                if (tickCount % 30 == 0) {
                    runPrefs.edit().putLong("running_seconds", TimerStateHolder.seconds).apply()
                }
                updateNotification()
            }
        }
    }

    private fun pauseTimer() {
        TimerStateHolder.paused = true
        tickJob?.cancel()
        getSharedPreferences(TIMER_PREFS, MODE_PRIVATE).edit()
            .putLong("running_seconds", TimerStateHolder.seconds)
            .putBoolean("is_paused", true)
            .apply()
        updateNotification()
    }

    private fun stopTimer(showEndNotification: Boolean = true) {
        TimerStateHolder.running = false
        TimerStateHolder.paused = false
        TimerStateHolder.shouldOpenDialog = showEndNotification
        tickJob?.cancel()
        getSharedPreferences(TIMER_PREFS, MODE_PRIVATE).edit()
            .remove("is_running")
            .remove("is_paused")
            .remove("running_book_id")
            .remove("running_seconds")
            .apply()

        if (showEndNotification) {
            getSharedPreferences(TIMER_PREFS, MODE_PRIVATE).edit()
                .putBoolean("pending", true)
                .putLong("book_id", TimerStateHolder.activeBookId)
                .putLong("seconds", TimerStateHolder.seconds)
                .apply()

            val openIntent = Intent(this, MainActivity::class.java).apply {
                action = ACTION_OPEN_SESSION_DIALOG
                putExtra(EXTRA_BOOK_ID, TimerStateHolder.activeBookId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val openPI = PendingIntent.getActivity(
                this, 200, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (nm.getNotificationChannel(SESSION_END_CHANNEL_ID) == null) {
                    val ch = NotificationChannel(
                        SESSION_END_CHANNEL_ID,
                        lctx().getString(R.string.notif_channel_session_end),
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = lctx().getString(R.string.notif_channel_session_end_desc)
                        setShowBadge(true)
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                        enableVibration(false)
                        setSound(null, null)
                    }
                    nm.createNotificationChannel(ch)
                }
            }
            val notif = NotificationCompat.Builder(this, SESSION_END_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(launcherIconBitmap())
                .setContentTitle(lctx().getString(R.string.notif_session_done))
                .setContentText(lctx().getString(R.string.txt_390674e3))
                .setContentIntent(openPI)
                .setFullScreenIntent(openPI, true)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setSilent(true)
                .build()
            nm.notify(SESSION_END_NOTIF_ID, notif)
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun ensureForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    lctx().getString(R.string.notif_channel_timer),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = lctx().getString(R.string.notif_channel_timer_desc)
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    enableVibration(false)
                    setSound(null, null)
                }
                nm.createNotificationChannel(channel)
            }
        }
        startForeground(NOTIF_ID, buildNotification())
    }

    private fun updateNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification())
    }

    private fun cleanBookTitleForNotification(title: String): String {
        var t = title.trim()
        // Remove saga/series info in parentheses: "El nombre del viento (El Nombre del Viento, #1)" → "El nombre del viento"
        t = t.replace(Regex("""\s*\([^)]+\)\s*$"""), "").trim()
        // Remove "#N" or ",N" trailing numbering
        t = t.replace(Regex("""\s*[,#]\s*\d+\s*$"""), "").trim()
        // Remove "Vol. N" or "vol N"
        t = t.replace(Regex("""\s+[Vv]ol\.?\s*\d+\s*$"""), "").trim()
        // Handle "Saga: Título real" — if prefix ≤ 4 words and suffix ≥ 2 words, keep suffix
        val ci = t.indexOf(':')
        if (ci > 0) {
            val before = t.substring(0, ci).trim()
            val after  = t.substring(ci + 1).trim()
            val beforeWords = before.split(Regex("""\s+""")).size
            val afterWords  = after.split(Regex("""\s+""")).size
            t = when {
                beforeWords <= 4 && afterWords >= 2 -> after
                beforeWords > 4 -> before
                else -> t
            }
        }
        return t.trim().ifBlank { title }
    }

    private fun buildNotification(): Notification {
        val secs = TimerStateHolder.seconds
        val h = secs / 3600
        val m = (secs % 3600) / 60
        val s = secs % 60
        val timeText = if (h > 0) String.format("%d:%02d:%02d", h, m, s)
                       else       String.format("%d:%02d", m, s)
        val statusText = if (TimerStateHolder.paused) lctx().getString(R.string.notif_status_paused, timeText)
                         else                          lctx().getString(R.string.notif_status_reading, timeText)

        val rawTitle = TimerStateHolder.activeBookTitle
        val cleanedTitle = if (rawTitle.isNotBlank()) cleanBookTitleForNotification(rawTitle) else ""

        val openAppPI = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleAction = if (TimerStateHolder.paused) {
            NotificationCompat.Action(R.drawable.ic_play_triangle, "▶", buildActionPendingIntent(ACTION_RESUME, 1))
        } else {
            NotificationCompat.Action(android.R.drawable.ic_media_pause, "⏸", buildActionPendingIntent(ACTION_PAUSE, 2))
        }

        val stopPendingIntent = PendingIntent.getActivity(
            this, 3,
            Intent(this, MainActivity::class.java).apply {
                action = ACTION_OPEN_SESSION_DIALOG
                putExtra(EXTRA_BOOK_ID, TimerStateHolder.activeBookId)
                putExtra("stop_session", true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopAction = NotificationCompat.Action(R.drawable.ic_stop_square, "⏹", stopPendingIntent)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(launcherIconBitmap())
            .setContentTitle(if (cleanedTitle.isNotBlank()) cleanedTitle else "Lecturameter")
            .setContentText(statusText)
            .setStyle(
                MediaNotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1)
            )
            .setContentIntent(openAppPI)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSilent(true)
            .addAction(toggleAction)
            .addAction(stopAction)
            .build()
    }

    private fun buildActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, TimerService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun launcherIconBitmap(): android.graphics.Bitmap {
        val prefs = getSharedPreferences("lecturameter", Context.MODE_PRIVATE)
        val iconRes = if (prefs.getString("app_icon", "classic") == "gold") R.mipmap.ic_launcher_pro else R.mipmap.ic_launcher
        return android.graphics.BitmapFactory.decodeResource(resources, iconRes)
    }

    companion object {
        const val TIMER_PREFS = "lecturameter_timer_session"
        const val CHANNEL_ID = "lecturameter_timer"
        const val NOTIF_ID   = 1001
        const val SESSION_END_CHANNEL_ID = "lecturameter_session_end"
        const val SESSION_END_NOTIF_ID   = 1002

        const val ACTION_START  = "com.lecturameter.timer.START"
        const val ACTION_PAUSE  = "com.lecturameter.timer.PAUSE"
        const val ACTION_RESUME = "com.lecturameter.timer.RESUME"
        const val ACTION_STOP   = "com.lecturameter.timer.STOP"

        const val ACTION_OPEN_SESSION_DIALOG = "com.lecturameter.OPEN_SESSION_DIALOG"
        const val EXTRA_BOOK_ID = "session_book_id"
        const val EXTRA_BOOK_TITLE = "session_book_title"
        const val EXTRA_SHOW_END_NOTIFICATION = "show_end_notification"

        fun start(context: Context, bookId: Long, bookTitle: String = "") {
            com.lecturameter.utils.AppLogger.log("Timer iniciado — libro $bookId")
            val i = Intent(context, TimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_BOOK_ID, bookId)
                putExtra(EXTRA_BOOK_TITLE, bookTitle)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun pause(context: Context) {
            com.lecturameter.utils.AppLogger.log("Timer pausado")
            context.startService(Intent(context, TimerService::class.java).apply { action = ACTION_PAUSE })
        }

        fun resume(context: Context) {
            com.lecturameter.utils.AppLogger.log("Timer reanudado")
            context.startService(Intent(context, TimerService::class.java).apply { action = ACTION_RESUME })
        }

        fun stop(context: Context, showEndNotification: Boolean = true) {
            com.lecturameter.utils.AppLogger.log("Timer detenido, sesión guardada")
            context.startService(Intent(context, TimerService::class.java).apply {
                action = ACTION_STOP
                putExtra(EXTRA_SHOW_END_NOTIFICATION, showEndNotification)
            })
        }

        fun cancelSessionEndNotification(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(SESSION_END_NOTIF_ID)
        }
    }
}

/**
 * Estado global del timer. El Composable de MainActivity lee estos valores
 * a través de un poll regular (LaunchedEffect con delay) o un flow.
 */
object TimerStateHolder {
    @Volatile var running: Boolean = false
    @Volatile var paused: Boolean = false
    @Volatile var seconds: Long = 0L
    @Volatile var activeBookId: Long = -1L
    @Volatile var activeBookTitle: String = ""
    @Volatile var chronometerBase: Long = 0L
    @Volatile var startElapsedRealtime: Long = 0L
    @Volatile var shouldOpenDialog: Boolean = false

    fun reset() {
        running = false
        paused = false
        seconds = 0L
        startElapsedRealtime = 0L
        shouldOpenDialog = false
        activeBookId = -1L
        activeBookTitle = ""
        chronometerBase = 0L
    }
}
