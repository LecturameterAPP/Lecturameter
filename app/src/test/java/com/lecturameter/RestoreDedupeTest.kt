package com.lecturameter

// F9 / B-032: al restaurar un backup, deduplicar sesiones SOLO por id no vale.
// ReadingSession.id es System.currentTimeMillis(), asi que la misma lectura
// registrada en dos instalaciones distintas nunca comparte id -> se duplicaba.
// Caso real: restaurar el backup del refac sobre los datos de la 2.7 dejaba dos
// sesiones exactas repetidas contando doble en las estadisticas.

import com.lecturameter.model.ReadingSession
import org.junit.Assert.assertEquals
import org.junit.Test

class RestoreDedupeTest {

    /** Misma regla que usa BackupRepository al restaurar. */
    private fun key(s: ReadingSession) = "${s.bookId}|${s.date}|${s.pages}|${s.startPage}|${s.endPage}"

    private fun ses(id: Long, bookId: Long, date: String, pages: Int, sp: Int?, ep: Int?) =
        ReadingSession(id = id, bookId = bookId, date = date, pages = pages, startPage = sp, endPage = ep)

    @Test fun la_misma_sesion_con_ids_distintos_es_la_misma() {
        // El dragon renacido, 14-07, 12 pags, 143-154 -- registrada en las dos apps
        val enLa27    = ses(1_700_000_000_001, 10L, "2026-07-14", 12, 143, 154)
        val enElRefac = ses(1_700_000_999_999, 10L, "2026-07-14", 12, 143, 154)
        assertEquals(key(enLa27), key(enElRefac))
    }

    @Test fun sesiones_distintas_del_mismo_libro_y_dia_no_se_confunden() {
        // Dos tramos distintos el mismo dia: son lecturas diferentes, deben sobrevivir ambas
        val a = ses(1L, 10L, "2026-07-15", 26, 155, 180)
        val b = ses(2L, 10L, "2026-07-15", 2, 181, 182)
        assert(key(a) != key(b))
    }

    @Test fun el_merge_no_duplica_y_conserva_lo_unico_de_cada_lado() {
        // Estado real del caso: la 2.7 y el refac comparten una sesion y cada uno
        // tiene una propia. El resultado debe ser 3, no 4.
        val existentes = listOf(
            ses(1L, 10L, "2026-07-14", 12, 143, 154),   // comun
            ses(2L, 20L, "2026-07-10", 11, 13, 23)      // solo en la 2.7
        )
        val delBackup = listOf(
            ses(99L, 10L, "2026-07-14", 12, 143, 154),  // comun, id distinto
            ses(98L, 10L, "2026-07-15", 26, 155, 180)   // solo en el refac (lectura de anoche)
        )
        val existentesKeys = existentes.map { key(it) }.toSet()
        val nuevas = delBackup.filter { key(it) !in existentesKeys }.distinctBy { key(it) }
        assertEquals(1, nuevas.size)
        assertEquals("2026-07-15", nuevas[0].date)
        assertEquals(3, (existentes + nuevas).size)
    }

    @Test fun un_backup_con_repes_dentro_no_las_propaga() {
        val delBackup = listOf(
            ses(1L, 10L, "2026-07-15", 26, 155, 180),
            ses(2L, 10L, "2026-07-15", 26, 155, 180)   // repe dentro del propio backup
        )
        assertEquals(1, delBackup.distinctBy { key(it) }.size)
    }
}
