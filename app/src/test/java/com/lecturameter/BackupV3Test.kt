package com.lecturameter

// Backup v3 (16-07-2026): FullBackup gana challenges/bingoCard/bingoMonthHistory.
// Estos tests fijan (1) que Gson deserializa los campos nuevos, (2) que un backup
// v2 (sin los campos) los deja a null sin romper, y (3) el criterio de dedupe de
// retos por id Y por (nombre|tipo|objetivo) que usa la restauración.

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lecturameter.model.*
import org.junit.Assert.*
import org.junit.Test

class BackupV3Test {

    private val gson = Gson()

    private fun challengeJson(id: Long, name: String, type: String, target: Int) =
        """{"id":$id,"name":"$name","type":"$type","target":$target,"isDefault":true}"""

    @Test
    fun `fullbackup v3 deserializa challenges y bingoCard`() {
        val json = """{
            "version":3,"exportedAt":1,
            "books":[],"sessions":[],
            "challenges":[${challengeJson(1, "12 books this year", "BOOKS", 12)}],
            "bingoCard":{"templateId":"clasico","templateNameEs":"El clásico","templateNameEn":"The classic",
                "monthKey":"2026-07","completedLines":[],
                "cells":[{"conditionType":"genre","conditionValue":"Fantasía","labelEs":"x","labelEn":"x","isCompleted":true}]}
        }"""
        val type = object : TypeToken<FullBackup>() {}.type
        val backup: FullBackup = gson.fromJson(json, type)
        assertNotNull(backup.challenges)
        assertEquals(1, backup.challenges!!.size)
        assertEquals(ChallengeType.BOOKS, backup.challenges!![0].type)
        assertNotNull(backup.bingoCard)
        assertEquals("2026-07", backup.bingoCard!!.monthKey)
        assertEquals(1, backup.bingoCard!!.cells.count { it.isCompleted })
    }

    @Test
    fun `fullbackup v2 sin campos nuevos deja null y no rompe`() {
        val json = """{"version":2,"exportedAt":1,"books":[],"sessions":[],"wrappedHistory":[]}"""
        val type = object : TypeToken<FullBackup>() {}.type
        val backup: FullBackup = gson.fromJson(json, type)
        assertNull(backup.challenges)
        assertNull(backup.bingoCard)
        assertNull(backup.bingoMonthHistory)
    }

    // ── v5 (B4, 2): el cartón 3×3 también viaja ───────────────────────────────

    @Test
    fun `fullbackup v5 deserializa el carton 3x3 aparte del 4x4`() {
        val json = """{
            "version":5,"exportedAt":1,"books":[],"sessions":[],
            "bingoCard":{"templateId":"clasico","templateNameEs":"El clásico","templateNameEn":"The classic",
                "monthKey":"2026-07","completedLines":[],
                "cells":[{"conditionType":"genre","conditionValue":"Fantasía","labelEs":"x","labelEn":"x","isCompleted":true}]},
            "bingoCard3":{"templateId":"reto_tocho","templateNameEs":"El tocho","templateNameEn":"The doorstopper",
                "monthKey":"2026-07","completedLines":[],
                "cells":[{"conditionType":"pages_gt","conditionValue":"600","labelEs":"y","labelEn":"y","isCompleted":true}]}
        }"""
        val backup: FullBackup = gson.fromJson(json, object : TypeToken<FullBackup>() {}.type)
        // Los dos cartones sobreviven al backup y NO se pisan entre si
        assertEquals("clasico", backup.bingoCard!!.templateId)
        assertEquals("reto_tocho", backup.bingoCard3!!.templateId)
    }

    @Test
    fun `un backup v3 sigue restaurando su 4x4 y deja el 3x3 a null`() {
        // La regla de compatibilidad: bingoCard NO cambia de significado (sigue siendo el
        // 4×4), asi que un backup viejo restaura exactamente igual que antes.
        val json = """{
            "version":3,"exportedAt":1,"books":[],"sessions":[],
            "bingoCard":{"templateId":"clasico","templateNameEs":"El clásico","templateNameEn":"The classic",
                "monthKey":"2026-07","completedLines":[],
                "cells":[{"conditionType":"genre","conditionValue":"Fantasía","labelEs":"x","labelEn":"x","isCompleted":true}]}
        }"""
        val backup: FullBackup = gson.fromJson(json, object : TypeToken<FullBackup>() {}.type)
        assertNotNull(backup.bingoCard)
        assertNull(backup.bingoCard3)
    }

    // Réplica del merge del historial de bingos de importFullBackupFromJson (por identidad,
    // no por mes). Fija el fallo que se arreglo: un mes con 4×4 Y 3×3 perdia uno.
    private fun mergeBingoHistory(
        local: List<com.lecturameter.utils.BingoMonthSummary>,
        fromBackup: List<com.lecturameter.utils.BingoMonthSummary>
    ): List<com.lecturameter.utils.BingoMonthSummary> {
        val localKeys = local.map { com.lecturameter.utils.BingoManager.summaryKey(it) }.toSet()
        val incoming = fromBackup.filter {
            it.monthKey.isNotBlank() && com.lecturameter.utils.BingoManager.summaryKey(it) !in localKeys
        }
        return (local + incoming).sortedBy { it.monthKey }
    }

    private fun sum(monthKey: String, cellsTotal: Int, name: String) =
        com.lecturameter.utils.BingoMonthSummary(
            monthKey = monthKey, templateNameEs = name, templateNameEn = name,
            cellsDone = 1, cellsTotal = cellsTotal, lines = 0, complete = false, pattern = ""
        )

    @Test
    fun `restaurar el historial no se come el 3x3 del mismo mes que el 4x4`() {
        val local = listOf(sum("2026-07", 16, "El clásico"))
        val backup = listOf(sum("2026-07", 16, "El clásico"), sum("2026-07", 9, "El tocho"))
        val merged = mergeBingoHistory(local, backup)
        // El 4×4 no se duplica y el 3×3 entra: 2 entradas, no 1 (el bug) ni 3 (duplicado)
        assertEquals(2, merged.size)
        assertEquals(1, merged.count { it.cellsTotal == 9 })
        assertEquals(1, merged.count { it.cellsTotal == 16 })
    }

    @Test
    fun `restaurar dos veces el mismo historial no duplica nada`() {
        val local = listOf(sum("2026-07", 16, "El clásico"), sum("2026-07", 9, "El tocho"))
        assertEquals(2, mergeBingoHistory(local, local).size)
    }

    // Réplica del criterio de merge de importFullBackupFromJson.
    private fun mergeIncoming(local: List<Challenge>, fromBackup: List<Challenge>): List<Challenge> {
        val localIds = local.map { it.id }.toSet()
        fun key(c: Challenge) = "${c.name.trim().lowercase()}|${c.type}|${c.target}"
        val localKeys = local.map(::key).toSet()
        return fromBackup.filter { it.id !in localIds && key(it) !in localKeys }
    }

    private fun ch(id: Long, name: String, type: ChallengeType, target: Int, default: Boolean = false) =
        Challenge(id = id, name = name, type = type, target = target, startDate = null, endDate = null, isDefault = default)

    @Test
    fun `los defaults resembrados con otro id no se duplican`() {
        val local = listOf(ch(100, "12 libros este año", ChallengeType.BOOKS, 12, default = true))
        val backup = listOf(ch(999, "12 libros este año", ChallengeType.BOOKS, 12, default = true))
        assertTrue(mergeIncoming(local, backup).isEmpty())
    }

    @Test
    fun `un reto custom perdido en la reinstalacion se restaura`() {
        val local = listOf(ch(100, "12 libros este año", ChallengeType.BOOKS, 12, default = true))
        val backup = listOf(
            ch(999, "12 libros este año", ChallengeType.BOOKS, 12, default = true),
            ch(555, "Verano de clásicos", ChallengeType.PAGES, 1000)
        )
        val incoming = mergeIncoming(local, backup)
        assertEquals(1, incoming.size)
        assertEquals("Verano de clásicos", incoming[0].name)
    }

    @Test
    fun `mismo id no se duplica aunque cambie el nombre`() {
        val local = listOf(ch(100, "Reto renombrado", ChallengeType.PAGES, 500))
        val backup = listOf(ch(100, "Reto original", ChallengeType.PAGES, 500))
        assertTrue(mergeIncoming(local, backup).isEmpty())
    }
}
