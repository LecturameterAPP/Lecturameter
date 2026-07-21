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
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import com.lecturameter.utils.isbn10To13
import com.lecturameter.utils.isbn13To10
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Normalizer
import java.util.Locale

/** Fuente de un resultado de busqueda, para el indicador visual de la Fase 2. */
enum class CatalogSource { CATALOG_LOCAL, OPEN_LIBRARY_API, GOOGLE_BOOKS_API, MANUAL }

object CatalogRepository {

    private const val TAG = "CatalogRepo"

    private const val CORE_ASSET = "catalog_core.db"

    /**
     * Play Asset Delivery. El catalogo ya NO viaja en el modulo base ni se copia a
     * filesDir: vive descomprimido en el directorio del pack y se abre ahi mismo.
     *
     * Esto mata de raiz el bug del 21-07 (CATALOG_VERSION sin subir y la app abriendo en
     * silencio la copia vieja de filesDir): ya no hay copia, y Play sustituye el pack entero
     * cuando cambia. Al reemitir el catalogo NO hay que tocar ninguna constante.
     *
     * El pack es fast-follow: Play lo baja solo tras instalar. Mientras no este, la busqueda
     * cae a las APIs EN SILENCIO, que es el criterio elegido; el unico aviso al usuario es
     * `err_no_internet_search`, que solo salta sin red Y sin catalogo.
     */
    private const val PACK_NAME = "catalog_pack"

    // Copia heredada de las versiones anteriores al pack (<= 3.1): 160 MB muertos en
    // filesDir que hay que devolver al usuario en el primer arranque.
    private const val LEGACY_PREFS = "catalog_prefs"
    private const val LEGACY_KEY_VERSION = "installed_version"

    // core + packs descargados. Se consultan todos y se fusionan resultados.
    private val open = LinkedHashMap<String, SQLiteDatabase>()

    // -- ciclo de vida ------------------------------------------------------

    /**
     * Abre el catalogo del asset pack y los packs de idioma ya descargados. Idempotente.
     * Llamar desde un hilo de IO en el arranque.
     *
     * Si el pack todavia no esta (recien instalada, o instalada sin conexion) pide su
     * descarga y sale sin catalogo: `isAvailable` queda en false y la busqueda usa las APIs.
     * El siguiente arranque lo encontrara.
     */
    fun init(context: Context) {
        if (open.isNotEmpty()) return
        try {
            deleteLegacyCopy(context)

            val core = corePackFile(context)
            if (core == null) {
                Log.i(TAG, "$PACK_NAME aun no esta instalado, se pide y se sigue sin catalogo")
                requestPack(context)
            } else {
                // core primero: sus resultados tienen prioridad al deduplicar
                openIfPresent(core)
            }

            // Packs de idioma (Fase 3): se descargan aparte y viven en filesDir.
            catalogDir(context)
                .listFiles { f -> f.name.startsWith("catalog_") && f.name != CORE_ASSET }
                ?.sortedBy { it.name }
                ?.forEach { openIfPresent(it) }

            Log.i(TAG, "catalogos abiertos: ${open.keys}")
        } catch (e: Exception) {
            // Un catalogo roto nunca debe tumbar la app: la busqueda cae a las APIs.
            Log.e(TAG, "init fallo, se seguira usando solo la busqueda online", e)
        }
    }

    /** Ruta de catalog_core.db dentro del asset pack, o null si el pack no esta instalado. */
    private fun corePackFile(context: Context): File? {
        val location = AssetPackManagerFactory.getInstance(context).getPackLocation(PACK_NAME)
        val assetsPath = location?.assetsPath() ?: return null
        return File(assetsPath, CORE_ASSET).takeIf { it.exists() && it.length() > 0L }
    }

    /**
     * Fuerza la descarga del pack. Sobre un fast-follow a medias `fetch()` es valido y lo
     * acelera. No se observa el progreso a proposito: el usuario no tiene que enterarse.
     */
    private fun requestPack(context: Context) {
        runCatching { AssetPackManagerFactory.getInstance(context).fetch(listOf(PACK_NAME)) }
            .onFailure { Log.w(TAG, "no se pudo pedir $PACK_NAME", it) }
    }

    /**
     * Borra la copia que hacian las versiones <= 3.1 en filesDir/catalog/. Son 160 MB que
     * ya no se abren nunca: el catalogo se lee del pack.
     */
    private fun deleteLegacyCopy(context: Context) {
        val legacy = File(catalogDir(context), CORE_ASSET)
        if (!legacy.exists()) return
        val bytes = legacy.length()
        if (legacy.delete()) {
            Log.i(TAG, "borrada la copia heredada de $CORE_ASSET ($bytes bytes)")
            context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
                .edit().remove(LEGACY_KEY_VERSION).apply()
        }
    }

    fun close() {
        open.values.forEach { runCatching { it.close() } }
        open.clear()
    }

    /** true si hay al menos un catalogo utilizable. */
    val isAvailable: Boolean get() = open.isNotEmpty()

    private fun catalogDir(context: Context) = File(context.filesDir, "catalog").apply { mkdirs() }

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
     *
     * Si la consulta ES un ISBN se resuelve por `lookupIsbn` en vez de por el FTS. No es
     * un extra: el indice FTS solo contiene titulos y autores, asi que tecleiar un ISBN en
     * la caja de busqueda no casaba con nada y el catalogo local no aportaba nada, pese a
     * que el campo dice "Titulo, autor, ISBN" y el estado vacio promete lo mismo.
     * Reproducido sin conexion el 21-07 con 9788416858361 (La torre), que SI esta en el
     * catalogo: la pantalla decia "sin resultados" tras esperar a que la red fallase.
     */
    suspend fun search(
        query: String,
        preferredLang: String = "es",
        limit: Int = 25
    ): List<OpenLibraryResult> = withContext(Dispatchers.IO) {
        if (open.isEmpty()) return@withContext emptyList()

        val posibleIsbn = query.filter { it.isDigit() || it == 'X' || it == 'x' }
        if ((posibleIsbn.length == 13 || posibleIsbn.length == 10) &&
            posibleIsbn.length == query.count { !it.isWhitespace() && it != '-' }
        ) {
            lookupIsbn(posibleIsbn, preferredLang)?.let { return@withContext listOf(it) }
        }

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
        // Por cada obra que casa el FTS se elige UN ISBN representativo: primero en el
        // idioma del usuario, luego el que traiga paginacion.
        // isbn13 es INTEGER PRIMARY KEY, o sea alias de rowid: el join va por el B-tree de
        // la propia tabla, sin indice secundario.
        val sql = """
            SELECT w.ol_key, w.title, w.author_names, w.first_publish_year, w.subjects,
                   i.isbn13, i.lang, i.pages, w.title_es, w.cover_id
            FROM works_fts f
            JOIN works w ON w.id = f.docid
            LEFT JOIN isbn i ON i.rowid = (
                SELECT i2.isbn13 FROM isbn i2
                WHERE i2.work_id = w.id
                ORDER BY (i2.lang = ?) DESC, (i2.pages IS NOT NULL) DESC
                LIMIT 1
            )
            WHERE works_fts MATCH ?
            LIMIT ?
        """.trimIndent()

        val results = ArrayList<OpenLibraryResult>()
        db.rawQuery(
            sql,
            arrayOf(langCode(preferredLang).toString(), match, limit.toString())
        ).use { c ->
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
        // isbn13 se guarda como INTEGER para ocupar 7 bytes en vez de 14; se re-formatea
        // a 13 digitos con ceros a la izquierda (los ISBN 979... y algunos 978 los pierden).
        val isbn13 = if (c.isNull(5)) null else c.getLong(5).toString().padStart(13, '0')
        return OpenLibraryResult(
            title = title,
            author = authors.firstOrNull().orEmpty(),
            pages = if (c.isNull(7)) 0 else c.getInt(7),
            coverUrl = coverUrlFor(if (c.isNull(9)) null else c.getLong(9), isbn13),
            isbn = isbn13,
            genre = c.getString(4).orEmpty().split('|').firstOrNull { it.isNotBlank() }.orEmpty(),
            publishYear = if (c.isNull(3)) "" else c.getInt(3).toString(),
            olKey = c.getString(0).orEmpty(),
            language = if (c.isNull(6)) "" else langFromCode(c.getInt(6)),
            matchAuthors = authors.joinToString(" ")
        )
    }

    /**
     * Lookup directo por ISBN: la ruta del escaner de codigos de barras.
     *
     * Acepta ISBN-10 y ISBN-13, y busca por AMBAS columnas convirtiendo entre formatos.
     * No es un detalle: en una biblioteca real medida, 62 de 108 libros guardaban ISBN-10
     * (los de fondo antiguo y las ediciones importadas). Mirar solo `isbn13` dejaba fuera
     * al 57% de la biblioteca sin ningun error visible.
     */
    suspend fun lookupIsbn(
        isbn: String,
        preferredLang: String = "es"
    ): OpenLibraryResult? = withContext(Dispatchers.IO) {
        if (open.isEmpty()) return@withContext null
        val limpio = isbn.filter { it.isDigit() || it == 'X' || it == 'x' }.uppercase()
        val par: Pair<String?, String?> = when (limpio.length) {
            13 -> limpio to isbn13To10(limpio)
            10 -> isbn10To13(limpio) to limpio
            else -> return@withContext null
        }
        val (i13, i10) = par
        if (i13 == null && i10 == null) return@withContext null

        // El catalogo guarda una sola forma canonica del ISBN, el 13 como INTEGER, asi que
        // aqui basta con una consulta por clave primaria. El ISBN-10 se convirtio arriba.
        val sql = """
            SELECT w.ol_key, w.title, w.author_names, w.first_publish_year, w.subjects,
                   i.isbn13, i.lang, i.pages, w.title_es, w.cover_id
            FROM isbn i
            JOIN works w ON w.id = i.work_id
            WHERE i.isbn13 = ?
            LIMIT 1
        """.trimIndent()
        val args = arrayOf(i13)

        for ((name, db) in open) {
            try {
                val hit = db.rawQuery(sql, args).use { c ->
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

    // El idioma se guarda como codigo corto en vez de cadena de 3 letras. Debe coincidir
    // con LANG_CODE en catalog-etl/etl.py. El catalogo solo contiene es, en y ca: el resto
    // de idiomas se resuelve online (MangaDex/Kitsu/AniList para japones, APIs para el resto).
    private fun langCode(code: String) = when (code.lowercase(Locale.ROOT)) {
        "es" -> 1; "en" -> 2; "ca" -> 3; else -> 0
    }

    private fun langFromCode(code: Int) = when (code) {
        1 -> "es"; 2 -> "en"; 3 -> "ca"; else -> ""
    }
}
