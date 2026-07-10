package com.lecturameter.repository

// ── Repositorios de datos (Fase 1.3) ──────────────────────────────────────────
// CRUD sobre SharedPreferences + Gson, extraído de BooksViewModel sin cambios
// funcionales. Los repos devuelven null cuando la clave no existe para que el
// ViewModel conserve sus early-returns de primera ejecución.
// SearchRepository y BackupRepository se extraen aparte (bloques grandes del monolito).

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lecturameter.model.*
import com.lecturameter.utils.sanitizeBook

object BookRepository {
    private val gson = Gson()

    /** null = clave "books" ausente (primera ejecución). */
    fun loadOrNull(prefs: SharedPreferences): List<Book>? {
        val json = prefs.getString("books", null) ?: return null
        val type = object : TypeToken<List<Book>>() {}.type
        // distinctBy { id }: si un proceso previo (restauración con IDs colisionantes) dejó
        // libros fantasma con el mismo ID, se conserva solo el primero. Evita duplicados ocultos.
        return (gson.fromJson<List<Book>>(json, type) ?: emptyList())
            .map { sanitizeBook(it) }
            .distinctBy { it.id }
    }

    fun save(prefs: SharedPreferences, books: List<Book>) {
        // v21.41: apply() en lugar de commit() — asíncrono, no bloquea hilo principal
        prefs.edit().putString("books", gson.toJson(books)).apply()
    }
}

object SessionRepository {
    private val gson = Gson()

    /** null = clave "sessions" ausente. */
    fun loadOrNull(prefs: SharedPreferences): List<ReadingSession>? {
        val json = prefs.getString("sessions", null) ?: return null
        val type = object : TypeToken<List<ReadingSession>>() {}.type
        return (gson.fromJson<List<ReadingSession>>(json, type) ?: emptyList())
            .distinctBy { it.id }
    }

    fun save(prefs: SharedPreferences, sessions: List<ReadingSession>) {
        prefs.edit().putString("sessions", gson.toJson(sessions)).apply()
    }
}

object WrappedRepository {
    private val gson = Gson()

    fun loadOrNull(prefs: SharedPreferences): List<YearWrapped>? {
        val json = prefs.getString("wrapped_history", null) ?: return null
        val type = object : TypeToken<List<YearWrapped>>() {}.type
        return try { gson.fromJson(json, type) ?: emptyList() } catch (_: Exception) { emptyList() }
    }

    fun save(prefs: SharedPreferences, history: List<YearWrapped>) {
        prefs.edit().putString("wrapped_history", gson.toJson(history)).apply()
    }
}

object ChallengeRepository {
    private val gson = Gson()

    fun loadOrNull(prefs: SharedPreferences): List<Challenge>? {
        val json = prefs.getString("challenges", null) ?: return null
        val type = object : TypeToken<List<Challenge>>() {}.type
        return (gson.fromJson<List<Challenge>>(json, type) ?: emptyList())
            .filter { it.name != null && it.type != null }  // Gson puede colar nulls desde JSON corrupto
    }

    fun save(prefs: SharedPreferences, challenges: List<Challenge>) {
        prefs.edit().putString("challenges", gson.toJson(challenges)).apply()
    }
}
