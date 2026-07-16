package com.lecturameter

// D-016 (16-07-2026): historial de retos. Estos tests fijan (1) que Gson deserializa el
// campo v4 challengeHistory y que un backup v3 sin él queda a null, (2) el criterio de
// dedupe del historial por (nombre|tipo|objetivo|año) que usa la RESTAURACIÓN al fusionar
// backups (el archivado en vivo usa ademas las fechas, ver A1 en archiveSnapshot), y (3)
// la regla de qué retos se archivan (completado o vencido).

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lecturameter.model.*
import org.junit.Assert.*
import org.junit.Test

class ChallengeHistoryTest {

    private val gson = Gson()

    @Test
    fun `fullbackup v4 deserializa challengeHistory`() {
        val json = """{
            "version":4,"exportedAt":1,"books":[],"sessions":[],
            "challengeHistory":[{"id":1,"name":"12 libros este año","type":"BOOKS","target":12,
                "finalProgress":14,"completed":true,"year":2026,"isDefault":true,"archivedAt":"2026-07-16"}]
        }"""
        val type = object : TypeToken<FullBackup>() {}.type
        val backup: FullBackup = gson.fromJson(json, type)
        assertNotNull(backup.challengeHistory)
        assertEquals(1, backup.challengeHistory!!.size)
        val s = backup.challengeHistory!![0]
        assertEquals(ChallengeType.BOOKS, s.type)
        assertTrue(s.completed)
        assertEquals(2026, s.year)
        assertEquals(14, s.finalProgress)
    }

    @Test
    fun `fullbackup v3 sin challengeHistory queda a null`() {
        val json = """{"version":3,"exportedAt":1,"books":[],"sessions":[]}"""
        val type = object : TypeToken<FullBackup>() {}.type
        val backup: FullBackup = gson.fromJson(json, type)
        assertNull(backup.challengeHistory)
    }

    // Réplica del criterio de merge del restore (clave de 4 partes, sin fechas).
    private fun key(s: ChallengeSnapshot) = "${s.name.trim().lowercase()}|${s.type}|${s.target}|${s.year}"
    private fun mergeIncoming(local: List<ChallengeSnapshot>, fromBackup: List<ChallengeSnapshot>): List<ChallengeSnapshot> {
        val localKeys = local.map(::key).toSet()
        return fromBackup.filter { key(it) !in localKeys }
    }

    private fun snap(id: Long, name: String, type: ChallengeType, target: Int, year: Int, completed: Boolean = true) =
        ChallengeSnapshot(id = id, name = name, type = type, target = target,
            finalProgress = if (completed) target else target / 2, completed = completed, year = year)

    @Test
    fun `el mismo reto del mismo año no se duplica aunque cambie el id`() {
        val local = listOf(snap(100, "12 libros este año", ChallengeType.BOOKS, 12, 2026))
        val backup = listOf(snap(999, "12 libros este año", ChallengeType.BOOKS, 12, 2026))
        assertTrue(mergeIncoming(local, backup).isEmpty())
    }

    @Test
    fun `el mismo reto de OTRO año si entra (historial por años)`() {
        val local = listOf(snap(100, "12 libros este año", ChallengeType.BOOKS, 12, 2026))
        val backup = listOf(snap(999, "12 libros este año", ChallengeType.BOOKS, 12, 2025))
        val incoming = mergeIncoming(local, backup)
        assertEquals(1, incoming.size)
        assertEquals(2025, incoming[0].year)
    }

    // Réplica de la regla de archivado de reconcileChallenges: completado (progreso >=
    // objetivo) o vencido (endDate explícita anterior a hoy). Sin endDate no vence nunca
    // dentro del año (el cierre de año va aparte).
    private fun shouldArchive(current: Int, target: Int, endDate: String?, today: String): Boolean =
        current >= target || (endDate != null && today > endDate)

    @Test
    fun `un reto completado se archiva`() {
        assertTrue(shouldArchive(current = 62, target = 12, endDate = null, today = "2026-07-16"))
    }

    @Test
    fun `un reto con fecha limite pasada se archiva como vencido`() {
        assertTrue(shouldArchive(current = 3, target = 12, endDate = "2026-06-30", today = "2026-07-16"))
    }

    @Test
    fun `un reto anual en curso sin completar NO se archiva`() {
        assertFalse(shouldArchive(current = 3, target = 12, endDate = null, today = "2026-07-16"))
        assertFalse(shouldArchive(current = 3, target = 12, endDate = "2026-12-31", today = "2026-07-16"))
    }
}
