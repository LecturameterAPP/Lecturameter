package com.lecturameter.utils

import android.content.SharedPreferences

/**
 * D-013 (monetización): punto ÚNICO de verdad del entitlement Pro.
 *
 * De momento es un placeholder: lee el flag `pro_unlocked` de prefs (false por defecto).
 * Cuando entre la infraestructura de pago (Play Billing + canje de códigos, modelo Augur),
 * el canje/compra escribirá este flag y todos los gates existentes funcionarán sin tocarse.
 *
 * Gates que ya lo usan: historial de retos (3 páginas por año gratis, D-016) y tope de
 * retos activos (3 páginas). Pendientes de cablear cuando D-013 se cierre: temas
 * Cuero/Aurora/AMOLED y tope de ediciones 2/5 (P-031).
 */
object Pro {
    const val PREF_KEY = "pro_unlocked"

    /** Páginas de retos activos incluidas en el plan gratis (5 retos por página). */
    const val FREE_CHALLENGE_PAGES = 3

    /** Páginas de historial de retos POR AÑO en el plan gratis (acumulativo: cada año suma 3). */
    const val FREE_HISTORY_PAGES_PER_YEAR = 3

    const val PER_PAGE = 5

    fun isPro(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_KEY, false)
}
