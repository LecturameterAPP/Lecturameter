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
