package com.lecturameter

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.signin.GoogleSignIn

/**
 * Worker que se ejecuta automáticamente cada 2 horas para subir
 * una copia de seguridad a Google Drive (solo si hay cuenta conectada).
 *
 * NO usamos DriveBackupManager.getSignedInAccount() aquí porque ese método
 * hace un check de GoogleSignIn.hasPermissions() que puede devolver false
 * en contexto de Worker/background aunque el usuario esté correctamente
 * autenticado. En su lugar, usamos getLastSignedInAccount directamente
 * y dejamos que la llamada a la API falle si los permisos realmente no son
 * válidos.
 */
class DriveBackupWorker(
    private val ctx: Context,
    private val workerParams: WorkerParameters
) : CoroutineWorker(ctx, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = ctx.getSharedPreferences("lecturameter", Context.MODE_PRIVATE)

        // Backup local en Descargas — solo si está habilitado
        val localEnabled = prefs.getBoolean("local_backup_enabled", true)
        if (localEnabled) {
            JsonBackupWorker(ctx, workerParams).doWork()
        }

        // Backup Drive — solo si está habilitado
        val driveEnabled = prefs.getBoolean("drive_backup_enabled", true)
        if (!driveEnabled) return Result.success()

        val account = GoogleSignIn.getLastSignedInAccount(ctx)
            ?: return Result.success()  // Sin cuenta conectada.

        return DriveBackupManager.backupWithAccount(ctx, account, prefs).fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }
}
