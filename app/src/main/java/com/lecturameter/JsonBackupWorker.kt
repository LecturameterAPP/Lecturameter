package com.lecturameter

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lecturameter.model.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Worker que se ejecuta automáticamente cada 2 horas para guardar
 * una copia de seguridad JSON local.
 *
 * Carpeta: Download/Backups Refrac/
 * Fichero: Backup_Refrac_DDMMYY.json  (fecha de HOY)
 *
 * Estrategia idéntica a DriveBackupManager:
 *   - Se busca cualquier archivo cuyo nombre empiece por "Backup_Refrac_"
 *     en la carpeta de backups (búsqueda por prefijo, no por nombre exacto).
 *   - Si existe: se actualiza el contenido Y se renombra a la fecha de hoy.
 *     Resultado: siempre exactamente un archivo, con la fecha del último backup.
 *   - Si no existe: se crea (solo ocurre la primera vez o si el usuario lo borró).
 *
 * Esto evita el bug de MediaStore que creaba Backup_(1).json, (2).json… cuando
 * la búsqueda por nombre exacto + RELATIVE_PATH fallaba por variaciones en la ruta.
 */
class JsonBackupWorker(
    private val ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    companion object {
        // v2.4 rework fix: nombre fijo SIN sufijo de flavor, como pre-release.
        // Los backups antiguos "Backup_Lecturameter_Public_*" siguen matcheando el
        // prefijo nuevo (startsWith / LIKE), así que se limpian/renombran solos.
        fun suffix(ctx: Context): String = ""

        // Fase 0 QA: carpeta y prefijo propios del refrac. CRÍTICO: este worker borra
        // por prefijo los backups antiguos de su carpeta; con el prefijo/carpeta de 2.7
        // podía machacar los backups reales de la app original instalada en el mismo móvil.
        fun folderName(ctx: Context)   = "Backups Lecturameter"
        fun filePrefix(ctx: Context)   = "Backup_Lecturameter_"
        fun relativePath(ctx: Context) = "${Environment.DIRECTORY_DOWNLOADS}/${folderName(ctx)}/"

        fun todayFileName(ctx: Context): String {
            val sdf = SimpleDateFormat("ddMMyy", Locale.getDefault())
            return "${filePrefix(ctx)}${sdf.format(Date())}.json"
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val prefs = ctx.getSharedPreferences("lecturameter", Context.MODE_PRIVATE)

            // Respetar el toggle de backup local de Ajustes
            if (!prefs.getBoolean("local_backup_enabled", true)) return Result.success()

            com.lecturameter.utils.AppLogger.log("Iniciando backup local")
            // v3: constructor compartido (retos + bingo incluidos). null = biblioteca
            // vacía → NO escribir: este worker mantiene UN solo fichero por carpeta y
            // una pasada en vacío (arranque tras reinstalar, antes de restaurar) pisaría
            // el único backup bueno con uno de 85 bytes. Visto en dispositivo el 16-07.
            val backup = buildFullBackupFromPrefs(prefs)
            if (backup == null) {
                com.lecturameter.utils.AppLogger.log("Backup local OMITIDO: biblioteca vacía (no se pisa el fichero existente)")
                return Result.success()
            }
            val jsonContent = Gson().toJson(backup)

            // Carpeta personalizada elegida en Ajustes (SAF) — si falla, fallback a Descargas
            val customFolderUri = prefs.getString("local_backup_folder_uri", null)
            val wroteCustom = customFolderUri != null && writeToCustomFolder(ctx, customFolderUri, jsonContent)

            if (!wroteCustom) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    writeViaMediaStore(jsonContent)
                } else {
                    writeDirectly(jsonContent)
                }
            }

            prefs.edit().putLong("last_local_backup_ms", System.currentTimeMillis()).apply()
            com.lecturameter.utils.AppLogger.log("Backup local completado con éxito")
            Result.success()
        } catch (e: Exception) {
            com.lecturameter.utils.AppLogger.logError("Fallo en backup local", e)
            Result.retry()
        }
    }

    /**
     * Escribe el backup en la carpeta SAF elegida por el usuario.
     * Misma estrategia que MediaStore: buscar por prefijo, sobrescribir y renombrar
     * a la fecha de hoy; crear solo si no existe. Devuelve false si algo falla
     * (permiso revocado, carpeta borrada…) para que el caller use el fallback.
     */
    private fun writeToCustomFolder(ctx: Context, treeUriStr: String, jsonContent: String): Boolean {
        return try {
            val treeUri = Uri.parse(treeUriStr)
            val parentDocId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
            val todayName  = todayFileName(ctx)
            val prefix     = filePrefix(ctx)

            // Buscar archivo del día (mismo nombre) y otros backups antiguos del prefijo
            var sameNameUri: Uri? = null
            val staleUris = mutableListOf<Uri>()
            ctx.contentResolver.query(
                childrenUri,
                arrayOf(
                    android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME
                ),
                null, null, null
            )?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = c.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (c.moveToNext()) {
                    val name = c.getString(nameIdx)
                    val uri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, c.getString(idIdx))
                    when {
                        name == todayName -> sameNameUri = uri
                        name.startsWith(prefix) -> staleUris.add(uri)
                    }
                }
            }

            // Borrar backups antiguos del prefijo (mantener solo el del día, como en Descargas)
            staleUris.forEach { try { android.provider.DocumentsContract.deleteDocument(ctx.contentResolver, it) } catch (_: Exception) {} }

            // Sobrescribir el del día si existe, o crear uno nuevo
            val targetUri = sameNameUri ?: run {
                val parentUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
                android.provider.DocumentsContract.createDocument(ctx.contentResolver, parentUri, "application/json", todayName)
            } ?: return false

            ctx.contentResolver.openOutputStream(targetUri, "wt")?.use { it.write(jsonContent.toByteArray()) }
                ?: return false
            true
        } catch (_: Exception) { false }
    }

    @Suppress("DEPRECATION")  // MediaStore.setIncludePending — solo rama API 29
    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.Q)
    private fun writeViaMediaStore(jsonContent: String) {
        val resolver   = ctx.contentResolver
        val todayName  = todayFileName(ctx)
        val prefix     = filePrefix(ctx)
        val folder     = folderName(ctx)
        val relPath    = relativePath(ctx)

        // Buscar por PREFIJO (igual que Drive), independiente del nombre exacto almacenado.
        // v2.4 rework fix: incluir archivos IS_PENDING — si un backup previo quedó a medias
        // (proceso muerto entre IS_PENDING=1 y 0), la query normal NO lo ve y se creaba un
        // duplicado nuevo en cada pasada.
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} LIKE ? AND ${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("${prefix}%", "%$folder%")
        val existing: Pair<Uri, String>? = (
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val queryArgs = android.os.Bundle().apply {
                    putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                    putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                    putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
                }
                resolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME),
                    queryArgs, null
                )
            } else {
                resolver.query(
                    MediaStore.setIncludePending(MediaStore.Downloads.EXTERNAL_CONTENT_URI),
                    arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME),
                    selection, selectionArgs, null
                )
            }
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id   = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME))
                val uri  = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
                uri to name
            } else null
        }

        if (existing != null) {
            val (existingUri, existingName) = existing
            // Sobreescribir contenido y renombrar a fecha de hoy si cambió.
            // v2.4 rework fix: migrar también RELATIVE_PATH — archivos heredados de la
            // carpeta "Backups Lecturameter_Public" se mueven a la carpeta nueva.
            val cv = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 1)
                if (existingName != todayName) put(MediaStore.Downloads.DISPLAY_NAME, todayName)
                put(MediaStore.Downloads.RELATIVE_PATH, relPath)
            }
            try {
                resolver.update(existingUri, cv, null, null)
            } catch (_: Exception) {
                // Si el rename/move falla (conflicto de nombre), al menos sobrescribir contenido
                val cvPend = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 1) }
                resolver.update(existingUri, cvPend, null, null)
            }
            resolver.openOutputStream(existingUri, "wt")?.use { it.write(jsonContent.toByteArray()) }
            cv.clear()
            cv.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(existingUri, cv, null, null)
        } else {
            // Primera vez: crear archivo
            val cv = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME,  todayName)
                put(MediaStore.Downloads.MIME_TYPE,     "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, relPath)
                put(MediaStore.Downloads.IS_PENDING,    1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: return
            resolver.openOutputStream(uri)?.use { it.write(jsonContent.toByteArray()) }
            cv.clear()
            cv.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, cv, null, null)
        }
    }

    @Suppress("DEPRECATION")
    private fun writeDirectly(jsonContent: String) {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            folderName(ctx)
        )
        dir.mkdirs()
        // Borrar cualquier backup anterior del prefijo y escribir el de hoy
        val prefix = filePrefix(ctx)
        dir.listFiles { f -> f.name.startsWith(prefix) }?.forEach { it.delete() }
        File(dir, todayFileName(ctx)).writeText(jsonContent)
    }
}
