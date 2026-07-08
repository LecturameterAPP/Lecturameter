package com.lecturameter

import android.app.Application
import com.lecturameter.utils.AppLogger

class LecturameterApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        EditionCache.init(this)
        AppLogger.init(this)
        AppLogger.log("App iniciada — ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    }
}
