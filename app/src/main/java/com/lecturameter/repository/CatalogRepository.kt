// Fase 1 del catalogo local (ver Documentacion/DATABASE_ARCHITECTURE.md).
// Acceso raw a SQLite (sin Room) sobre catalog_core.db y los packs de idioma opcionales.
// El catalogo es read-only e inmutable en runtime: nunca se escribe, nunca entra en el backup.
//
// Devuelve OpenLibraryResult directamente en vez del CatalogBookResult del documento de
// diseno: la UI de busqueda ya consume ese tipo, y anadir un tipo intermedio obligaria a
// tocar AddBookScreen/BookSearchScreen sin ganar nada. El campo `source` del diseno se
// conserva como CatalogSource, devuelto aparte por searchWithSource().
package com.lecturameter

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Normalizer
import java.util.Locale

/** Fuente de un resultado de busqueda, para el indicador visual de la Fase 2. */
enum class CatalogSource { CATALOG_LOCAL, OPEN_LIBRARY_API, GOOGLE_BOOKS_API, MANUAL }

object CatalogRepository {

    private const val TAG = "CatalogRepo"

    /** Subir cuando se regenere el catalogo con un ETL nuevo: fuerza recopiar desde assets. */
    private const val CATALOG_VERSION = 1
    private const val PREFS = "catalog_prefs"
    private const val KEY_INSTALLED_VERSION = "installed_version"

    private const val CORE_ASSET = "catalog_core.db"

    // core + packs descargados. Se consultan todos y se fusionan resultados.
    private val open = LinkedHashMap<String, SQLiteDatabase>()

    // -- ciclo de vida ------------------------------------------------------

    /**
     * Copia catalog_core.db desde assets si hace falta y abre todos los catalogos
     * disponibles. Idempotente. Llamar desde un hilo de IO en el arranque.
     */
    fun init(context: Context) {
        if (open.isNotEmpty()) return
        try {
            val dir = catalogDir(context)
            val core = File(dir, CORE_ASSET)

            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val installed = prefs.getInt(KEY_INSTALLED_VERSION, -1)
            if (!core.exists() || installed != CATALOG_VERSION) {
                copyCoreFromAssets(context, core)
                prefs.edit().putInt(KEY_INSTALLED_VERSION, CATALOG_VERSION).apply()
            }

            // core primero: sus resultados tienen prioridad al deduplicar
            openIfPresent(core)
            dir.listFiles { f -> f.name.startsWith("catalog_") && f.name != CORE_ASSET }
                ?.sortedBy { it.name }
                ?.forEach { openIfPresent(it) }

            Log.i(TAG, "catalogos abiertos: ${open.keys}")
        } catch (e: Exception) {
            // Un catalogo roto nunca debe tumbar la app: la busqueda cae a las APIs.
            Log.e(TAG, "init fallo, se seguira usando solo la busqueda online", e)
        }
    }

    fun close() {
        open.values.forEach { runCatching { it.close() } }
        open.clear()
    }

    /** true si hay al menos un catalogo utilizable. */
    val isAvailable: Boolean get() = open.isNotEmpty()

    private fun catalogDir(context: Context) = File(context.filesDir, "catalog").apply { mkdirs() }

    private fun copyCoreFromAssets(context: Context, dest: File) {
        Log.i(TAG, "copiando $CORE_ASSET desde assets")
        val tmp = File(dest.parentFile, "$CORE_ASSET.tmp")
        context.assets.open(CORE_ASSET).use { input ->
            tmp.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
        }
        if (dest.exists()) dest.delete()
        // rename atomico: si el proceso muere a medio copiar no queda un .db truncado
        if (!tmp.renameTo(dest)) throw IllegalStateException("no se pudo renombrar $tmp")
    }

    private fun openIfPresent(file: File) {
        if (!file.exists() || file.length() == 0L) return
        try {
            val db = SQLiteDatabase.openDatabase(
                file.absolutePath, null, SQLiteDatabase.OPEN_READONLY
            )
            open[file.name] = db
        } catch (e: Exception) {
            Log.e(TAG, "no se pudo abrir ${file.name}, se ignora", e)
            // Un pack corrupto se borra para que no falle en cada arranque.
            if (file.name != CORE_ASSET) file.delete()
        }
    }

    // -- busqueda -----------------------------------------------------------

    /**
     * Busqueda FTS4 por titulo y autor. Devuelve lista vacia si no hay catalogo
     * o la consulta no da resultados: quien llama decide si va a las APIs.
     */
    suspend fun search(
        query: String,
        preferredLang: String = "es",
        limit: Int = 25
    ): List<OpenLibraryResult> = withContext(Dispatchers.IO) {
        if (open.isEmpty()) return@withContext emptyList()
        val match = ftsQuery(query) ?: return@withContext emptyList()

        val out = LinkedHashMap<String, OpenLibraryResult>()
        for ((name, db) in open) {
            try {
                queryOne(db, match, preferredLang, limit).forEach { r ->
                    // el primer catalogo que aporta una obra gana (core tiene prioridad)
                    out.putIfAbsent(r.olKey.ifBlank { "${r.title}|${r.author}" }, r)
                }
            } catch (e: Exception) {
                Log.e(TAG, "busqueda fallo en $name", e)
            }
            if (out.size >= limit) break
        }
        out.values.take(limit).toList()
    }

    private fun queryOne(
        db: SQLiteDatabase,
        match: String,
        preferredLang: String,
        limit: Int
    ): List<OpenLibraryResult> {
        val prefOl = appLangToMarc(preferredLang)
        // Por cada obra que casa el FTS se elige UNA edicion representativa:
        // primero en el idioma del usuario, luego la que tenga ISBN y paginas, luego la mas reciente.
        val sql = """
            SELECT w.ol_key, w.title, w.author_names, w.first_publish_year, w.subjects,
                   e.isbn13, e.language, e.pages, w.title_es, w.cover_id
            FROM works_fts f
            JOIN works w ON w.rowid = f.docid
            LEFT JOIN editions e ON e.ol_key = (
                SELECT e2.ol_key FROM editions e2
                WHERE e2.work_key = w.ol_key
                ORDER BY (e2.language = ?) DESC,
                         (e2.isbn13 IS NOT NULL) DESC,
                         (e2.pages IS NOT NULL) DESC,
                         e2.year DESC
                LIMIT 1
            )
            WHERE works_fts MATCH ?
            LIMIT ?
        """.trimIndent()

        val results = ArrayList<OpenLibraryResult>()
        db.rawQuery(sql, arrayOf(prefOl, match, limit.toString())).use { c ->
            while (c.moveToNext()) results += readResult(c, preferredLang) ?: continue
        }
        return results
    }

    /**
     * Mapea la fila actual del cursor a OpenLibraryResult. Las dos consultas
     * (FTS y lookup por ISBN) proyectan las mismas nueve columnas en el mismo orden.
     * Devuelve null si falta titulo, que es el unico campo sin el que el resultado no sirve.
     *
     * Si el usuario lee en espanol y la obra tiene edicion espanola, se muestra ese titulo:
     * quien busca "El Imperio Final" espera ver "El Imperio Final", no "The Final Empire".
     */
    private fun readResult(c: android.database.Cursor, preferredLang: String): OpenLibraryResult? {
        val original = c.getString(1)?.takeIf { it.isNotBlank() } ?: return null
        val title = if (preferredLang.equals("es", ignoreCase = true)) {
            c.getString(8)?.takeIf { it.isNotBlank() } ?: original
        } else {
            original
        }
        val authors = c.getString(2).orEmpty().split('|').filter { it.isNotBlank() }
        val isbn13 = c.getString(5)
        return OpenLibraryResult(
            title = title,
            author = authors.firstOrNull().orEmpty(),
            pages = if (c.isNull(7)) 0 else c.getInt(7),
            coverUrl = coverUrlFor(if (c.isNull(9)) null else c.getLong(9), isbn13),
            isbn = isbn13,
            genre = c.getString(4).orEmpty().split('|').firstOrNull { it.isNotBlank() }.orEmpty(),
            publishYear = if (c.isNull(3)) "" else c.getInt(3).toString(),
            olKey = c.getString(0).orEmpty(),
            language = marcToAppLang(c.getString(6)),
            matchAuthors = authors.joinToString(" ")
        )
    }

    /** Lookup directo por ISBN13: la ruta del escaner de codigos de barras. */
    suspend fun lookupIsbn(
        isbn13: String,
        preferredLang: String = "es"
    ): OpenLibraryResult? = withContext(Dispatchers.IO) {
        val clean = isbn13.filter { it.isDigit() }
        if (clean.length != 13 || open.isEmpty()) return@withContext null

        val sql = """
            SELECT w.ol_key, w.title, w.author_names, w.first_publish_year, w.subjects,
                   e.isbn13, e.language, e.pages, w.title_es, w.cover_id
            FROM editions e
            JOIN works w ON w.ol_key = e.work_key
            WHERE e.isbn13 = ?
            LIMIT 1
        """.trimIndent()

        for ((name, db) in open) {
            try {
                val hit = db.rawQuery(sql, arrayOf(clean)).use { c ->
                    if (c.moveToFirst()) readResult(c, preferredLang) else null
                }
                if (hit != null) return@withContext hit
            } catch (e: Exception) {
                Log.e(TAG, "lookupIsbn fallo en $name", e)
            }
        }
        null
    }

    // -- helpers ------------------------------------------------------------

    /**
     * Construye la expresion MATCH de FTS4. Normaliza igual que el ETL (minusculas sin
     * acentos), descarta puntuacion, y anade prefijo * al ultimo token para que la
     * busqueda funcione mientras el usuario aun escribe.
     *
     * Devuelve null si no queda ningun token util, para no lanzar un MATCH vacio.
     */
    internal fun ftsQuery(raw: String): String? {
        val tokens = normalize(raw).split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null
        return tokens.mapIndexed { i, t ->
            if (i == tokens.lastIndex && t.length >= 2) "$t*" else t
        }.joinToString(" ")
    }

    /** Misma normalizacion que common.normalize() del ETL: sin esto el matching falla. */
    internal fun normalize(text: String): String {
        val nfkd = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFKD)
        val sinAcentos = nfkd.replace(Regex("\\p{Mn}+"), "")
        // \p{Nd} + guion bajo replica exactamente el \w de Python usado por el ETL
        // (common.normalize). Sin el guion bajo, un titulo con "_" se normalizaria
        // distinto aqui que en la base y nunca casaria con el FTS.
        return sinAcentos.replace(Regex("[^\\p{L}\\p{Nd}_\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Portada via OL Covers API. El catalogo no lleva imagenes empaquetadas (son obra con
     * copyright de la editorial; solo se enlazan en tiempo de ejecucion).
     *
     * Se prefiere SIEMPRE la ruta por cover ID: la Covers API limita a 100 peticiones cada
     * 5 minutos por IP cuando se pide por ISBN, y devuelve 403 al pasarse, pero por cover ID
     * no aplica limite. Con una rejilla de resultados, la ruta por ISBN se agota en nada.
     * El ISBN queda como respaldo para las obras sin portada registrada.
     */
    private fun coverUrlFor(coverId: Long?, isbn13: String?): String? = when {
        coverId != null && coverId > 0 -> "https://covers.openlibrary.org/b/id/$coverId-M.jpg"
        !isbn13.isNullOrBlank() -> "https://covers.openlibrary.org/b/isbn/$isbn13-M.jpg"
        else -> null
    }

    private fun appLangToMarc(code: String) = when (code.lowercase(Locale.ROOT)) {
        "es" -> "spa"; "en" -> "eng"; "ca" -> "cat"; "fr" -> "fre"
        "de" -> "ger"; "it" -> "ita"; "pt" -> "por"; else -> code.lowercase(Locale.ROOT)
    }

    private fun marcToAppLang(marc: String?) = when (marc?.lowercase(Locale.ROOT)) {
        "spa" -> "es"; "eng" -> "en"; "cat" -> "ca"
        "fre", "fra" -> "fr"; "ger", "deu" -> "de"; "ita" -> "it"; "por" -> "pt"
        null, "" -> ""
        else -> marc.lowercase(Locale.ROOT)
    }
}
