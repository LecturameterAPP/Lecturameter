package com.lecturameter.utils

import android.content.Context

// ── Caché de portadas para resultados de búsqueda del catálogo local ──────────
//
// Por qué existe. El catálogo resuelve el libro (título, autor, páginas, año) pero muchas
// obras entran SIN portada: las 250.000 que vienen de la Biblioteca Nacional llegan con
// `cover_id` nulo, porque las cubiertas no se pueden empaquetar (copyright de las
// editoriales, ninguna fuente las sublicencia). `CatalogRepository.coverUrlFor()` cae
// entonces en la vía por ISBN de Open Library, que tiene límite de 100 peticiones cada
// 5 minutos y luego responde 403, y que para muchos ISBN españoles no tiene imagen.
//
// La idea, en palabras de Víctor: la base de datos decide QUÉ libro es, la API solo lo
// viste. Así que se pregunta a la red SOLO por la imagen, y se guarda una semana para no
// repetir la llamada cada vez que alguien busca lo mismo.
//
// Se cachea la URL, no los bytes: de descargar y reutilizar la imagen ya se encarga el
// cargador de imágenes de la app. Aquí lo caro es AVERIGUAR la URL, no traerla.
//
// NO confundir con [CoverStore], que es otra cosa: aquel saca de SharedPreferences las
// portadas base64 de los libros que ya están en la biblioteca (B-029).
object SearchCoverCache {

    private const val PREFS = "search_cover_cache"
    private const val TTL_MS = 7L * 24 * 60 * 60 * 1000     // una semana

    // Tope de entradas. Sin él, un usuario que busca mucho acaba con un XML de prefs de
    // varios MB que se lee entero en cada arranque. Al pasarse se tira la mitad más vieja.
    private const val MAX_ENTRIES = 400

    // Marca para recordar que una obra NO tiene portada en ninguna fuente. Sin esto, un
    // libro sin cubierta se vuelve a preguntar a la red en CADA búsqueda, para siempre.
    private const val SIN_PORTADA = ""

    @Volatile private var appContext: Context? = null

    // Espejo en memoria. Es también el modo degradado: si nadie llama a init(), la caché
    // sigue funcionando dentro de la sesión en vez de romper la búsqueda.
    private val memoria = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Long>>()

    @Volatile private var cargada = false

    fun init(context: Context) {
        appContext = context.applicationContext
        cargarSiHaceFalta()
    }

    @Synchronized
    private fun cargarSiHaceFalta() {
        if (cargada) return
        val ctx = appContext ?: return
        try {
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val ahora = System.currentTimeMillis()
            for ((k, v) in prefs.all) {
                val s = v as? String ?: continue
                val corte = s.lastIndexOf('|')
                if (corte < 0) continue
                val ts = s.substring(corte + 1).toLongOrNull() ?: continue
                if (ahora - ts > TTL_MS) continue          // caducada, no se carga
                memoria[k] = s.substring(0, corte) to ts
            }
            cargada = true
        } catch (_: Exception) {
            cargada = true   // una caché rota nunca debe impedir buscar
        }
    }

    /**
     * Devuelve la URL cacheada, cadena vacía si ya se comprobó que no hay portada, o null
     * si no se sabe nada de esta clave (entonces toca preguntar a la red).
     */
    fun get(key: String): String? {
        cargarSiHaceFalta()
        val (url, ts) = memoria[key] ?: return null
        if (System.currentTimeMillis() - ts > TTL_MS) {
            memoria.remove(key)
            return null
        }
        return url
    }

    /** `url` nula significa "comprobado y no hay portada": también se cachea. */
    fun put(key: String, url: String?) {
        cargarSiHaceFalta()
        val ahora = System.currentTimeMillis()
        memoria[key] = (url ?: SIN_PORTADA) to ahora
        val ctx = appContext ?: return
        try {
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (memoria.size > MAX_ENTRIES) podar(prefs)
            prefs.edit().putString(key, "${url ?: SIN_PORTADA}|$ahora").apply()
        } catch (_: Exception) {
        }
    }

    /** Tira la mitad más antigua. Llamado solo al superar el tope. */
    private fun podar(prefs: android.content.SharedPreferences) {
        val sobran = memoria.entries.sortedBy { it.value.second }.take(memoria.size / 2)
        val ed = prefs.edit()
        for (e in sobran) {
            memoria.remove(e.key)
            ed.remove(e.key)
        }
        ed.apply()
    }

    /** Clave estable: el ISBN si lo hay, y si no el título y el autor normalizados. */
    fun keyFor(isbn13: String?, title: String, author: String): String =
        if (!isbn13.isNullOrBlank()) "i:$isbn13"
        else "t:${normalizaClave(title)}|${normalizaClave(author)}"

    private fun normalizaClave(s: String): String =
        s.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), "").replace(Regex("\\s+"), " ").trim()
}
