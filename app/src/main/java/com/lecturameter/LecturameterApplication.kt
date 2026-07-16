package com.lecturameter

import android.app.Application
import com.lecturameter.utils.AppLogger

class LecturameterApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        EditionCache.init(this)
        AppLogger.init(this)
        // A6: fija el fichero de prefs local del trial/lockout y migra las claves viejas.
        com.lecturameter.utils.Pro.init(this)
        AppLogger.log("App iniciada — ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    }
}
