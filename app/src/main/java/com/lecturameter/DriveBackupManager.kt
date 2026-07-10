package com.lecturameter

/**
 * CONFIGURACIÓN NECESARIA (una sola vez):
 * 1. Google Cloud Console → crear proyecto → habilitar "Google Drive API"
 * 2. Credenciales → Crear credencial → ID de cliente OAuth → Android
 *    Nombre del paquete: com.lecturameter
 *    SHA-1: obtener con `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`
 * 3. No es necesario descargar ningún archivo: Google Play Services gestiona el OAuth.
 *
 * SCOPE: DRIVE_FILE → los archivos son visibles en drive.google.com.
 * NO usamos appDataFolder porque es una carpeta oculta; el usuario no podría
 * verificar ni gestionar su backup desde Drive.
 */

import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

object DriveBackupManager {

    const val PREF_LAST_BACKUP_MS = "drive_last_backup_ms"
    private const val APP_NAME = "Lecturameter"
    private const val CLIENT_ID = "1045574439348-5j45e0ub4spquv7est81bgmpi4qfmhhq.apps.googleusercontent.com"

    private fun filePrefix(context: Context): String {
        // Fase 0 QA: prefijo propio del refrac — el lookup/rename por prefijo en Drive
        // no debe tocar los backups de la app 2.7 original.
        return "Backup_Refrac_"
    }

    // DRIVE_FILE: acceso a archivos visibles en el Drive del usuario.
    // Con DRIVE_APPDATA los archivos quedan ocultos y el usuario no puede verlos.
    val REQUIRED_SCOPE = Scope(DriveScopes.DRIVE_FILE)

    /** Nombre del archivo: Backup_Refrac_DDMMYY.json (fecha de hoy). */
    private fun backupFileName(context: Context): String {
        val sdf = SimpleDateFormat("ddMMyy", Locale.getDefault())
        return "${filePrefix(context)}${sdf.format(Date())}.json"
    }

    // ── Cuenta ────────────────────────────────────────────────────────────────

    fun getSignedInAccount(context: Context): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)?.takeIf {
            GoogleSignIn.hasPermissions(it, REQUIRED_SCOPE)
        }

    fun buildSignInOptions(): GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(REQUIRED_SCOPE)
            .build()

    // ── Servicio Drive ────────────────────────────────────────────────────────

    private fun buildDriveService(context: Context, account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential
            .usingOAuth2(context, listOf(DriveScopes.DRIVE_FILE))
            .apply { selectedAccount = account.account }
        return Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName(APP_NAME)
            .build()
    }

    // ── Búsqueda del archivo ──────────────────────────────────────────────────
    // Buscamos por prefijo para encontrar el backup sin importar la fecha en el
    // nombre. El archivo vive en el Drive raíz del usuario (visible en Drive).
    // NO usamos spaces/appDataFolder porque el scope es DRIVE_FILE.
    //
    // v1.7: la búsqueda distingue FALLO (red/token) de NO-EXISTE. Antes, un fallo
    // transitorio devolvía null → el caller creaba archivo NUEVO → duplicados.
    // Ahora un fallo aborta el backup (el worker reintenta). Además devuelve TODOS
    // los ids del prefijo para poder limpiar duplicados ya existentes.

    private class DriveSearchException(cause: Throwable) : Exception(cause)

    /** Devuelve lista de ids ordenada por modifiedTime DESC (más reciente primero).
     *  Puede estar vacía = no existe backup.
     *  Lanza DriveSearchException si la búsqueda en sí falla. */
    private suspend fun findBackupFileIds(drive: Drive, context: Context): List<String> = withContext(Dispatchers.IO) {
        val prefix = filePrefix(context)
        try {
            drive.files().list()
                .setQ("name contains '$prefix' and trashed = false")
                .setOrderBy("modifiedTime desc")
                .setFields("files(id)")
                .execute()
                .files
                ?.mapNotNull { it.id }
                ?: emptyList()
        } catch (e: Exception) {
            throw DriveSearchException(e)
        }
    }

    // ── Backup ────────────────────────────────────────────────────────────────

    /** Llamado desde la UI: valida permisos antes de hacer backup. */
    suspend fun backup(context: Context, prefs: SharedPreferences): Result<Unit> {
        val account = getSignedInAccount(context)
            ?: return Result.failure(Exception(context.getString(R.string.err_drive_no_account)))
        return backupWithAccount(context, account, prefs)
    }

    /**
     * Llamado desde el Worker en background: recibe la cuenta directamente para
     * evitar el check de hasPermissions() que puede fallar en contexto de Worker.
     */
    suspend fun backupWithAccount(
        context: Context,
        account: GoogleSignInAccount,
        prefs: SharedPreferences
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                com.lecturameter.utils.AppLogger.log("Iniciando backup en Google Drive")
                val drive = buildDriveService(context, account)
                val json = buildBackupJson(prefs)
                val content = ByteArrayContent(
                    "application/json",
                    json.toByteArray(Charsets.UTF_8)
                )
                val fileName = backupFileName(context)
                val existingIds = try {
                    findBackupFileIds(drive, context)
                } catch (e: DriveSearchException) {
                    // v1.7: si la BÚSQUEDA falla, abortar. Crear un archivo sin saber si ya
                    // existe uno genera duplicados. El worker reintentará más tarde.
                    com.lecturameter.utils.AppLogger.logError("Búsqueda de backup en Drive falló, abortando (retry)", e.cause)
                    throw e
                }
                if (existingIds.isNotEmpty()) {
                    // Actualiza contenido Y nombre (refleja la fecha de hoy) en el primero.
                    val meta = DriveFile().setName(fileName)
                    drive.files().update(existingIds.first(), meta, content).execute()
                    // v1.7: limpieza de duplicados históricos — borrar el resto
                    existingIds.drop(1).forEach { staleId ->
                        try {
                            drive.files().delete(staleId).execute()
                            com.lecturameter.utils.AppLogger.log("Backup duplicado en Drive eliminado: $staleId")
                        } catch (e: Exception) {
                            com.lecturameter.utils.AppLogger.logError("No se pudo borrar duplicado Drive: $staleId", e)
                        }
                    }
                } else {
                    // Primera vez: crea el archivo en la raíz del Drive (visible).
                    // Sin setParents() el archivo va a "Mi unidad" directamente.
                    val meta = DriveFile()
                        .setName(fileName)
                        .setMimeType("application/json")
                    drive.files().create(meta, content).setFields("id").execute()
                }
                prefs.edit().putLong(PREF_LAST_BACKUP_MS, System.currentTimeMillis()).apply()
                com.lecturameter.utils.AppLogger.log("Backup en Google Drive completado con éxito")
            }.onFailure { e -> com.lecturameter.utils.AppLogger.logError("Fallo al hacer backup en Drive", e) }
        }

    // ── Restaurar desde Drive ─────────────────────────────────────────────────

    suspend fun restore(
        context: Context,
        vm: BooksViewModel,
        prefs: SharedPreferences
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val account = getSignedInAccount(context)
                ?: error(context.getString(R.string.err_drive_no_account))
            val drive = buildDriveService(context, account)
            val fileId = try {
                findBackupFileIds(drive, context).firstOrNull()
            } catch (e: DriveSearchException) {
                com.lecturameter.utils.AppLogger.logError("Búsqueda de backup en Drive falló durante restore", e.cause)
                error(context.getString(R.string.err_drive_no_backup_found))
            } ?: error(context.getString(R.string.err_drive_no_backup_found))
            val out = ByteArrayOutputStream()
            drive.files().get(fileId).executeMediaAndDownloadTo(out)
            val json = out.toString(Charsets.UTF_8.name())
            val (ok, msg) = importFullBackupFromJson(json, vm, prefs, context)
            if (!ok) error(msg) else msg
        }
    }

    // ── Helpers internos ──────────────────────────────────────────────────────

    private fun buildBackupJson(prefs: SharedPreferences): String {
        val gson = Gson()
        val books = gson.fromJson<List<Book>>(
            prefs.getString("books", "[]"),
            object : TypeToken<List<Book>>() {}.type
        ) ?: emptyList()
        val sessions = gson.fromJson<List<ReadingSession>>(
            prefs.getString("sessions", "[]"),
            object : TypeToken<List<ReadingSession>>() {}.type
        ) ?: emptyList()
        val wrapped = gson.fromJson<List<YearWrapped>>(
            prefs.getString("wrapped_history", "[]"),
            object : TypeToken<List<YearWrapped>>() {}.type
        ) ?: emptyList()
        val booksWithCovers = books.map { book ->
            val embCover = embedLocalCoverUrl(book.coverUrl)
            val embEditions = book.editions.map { ed -> ed.copy(coverUrl = embedLocalCoverUrl(ed.coverUrl)) }
            book.copy(coverUrl = embCover, editions = embEditions)
        }
        return gson.toJson(FullBackup(books = booksWithCovers, sessions = sessions, wrappedHistory = wrapped))
    }

    // ── Timestamp de la última copia ──────────────────────────────────────────

    fun formatLastBackup(context: Context, prefs: SharedPreferences): String {
        val ts = prefs.getLong(PREF_LAST_BACKUP_MS, 0L)
        if (ts == 0L) return context.getString(R.string.backup_time_never)
        val diff = abs(System.currentTimeMillis() - ts)
        val mins = TimeUnit.MILLISECONDS.toMinutes(diff)
        return when {
            mins < 2    -> context.getString(R.string.backup_time_moment)
            mins < 60   -> context.getString(R.string.backup_time_mins, mins)
            mins < 1440 -> context.getString(R.string.backup_time_hours, TimeUnit.MILLISECONDS.toHours(diff))
            else        -> context.getString(R.string.backup_time_days, TimeUnit.MILLISECONDS.toDays(diff))
        }
    }
}
