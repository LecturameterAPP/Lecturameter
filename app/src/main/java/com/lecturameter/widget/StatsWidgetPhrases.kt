package com.lecturameter.widget

import android.content.Context
import com.lecturameter.R
import com.lecturameter.utils.GlobalStats
import java.util.Calendar
import kotlin.random.Random

/**
 * Rotating phrases for the Stats Widget Pro.
 * Selection uses day-of-year as seed for deterministic daily rotation.
 * Rarity: Common 70%, Rare 20%, Epic 9%, Legendary 1%.
 */
internal object StatsWidgetPhrases {

    internal enum class Rarity { COMMON, RARE, EPIC, LEGENDARY }

    internal data class Phrase(
        val rarity: Rarity,
        val resolve: (Context, GlobalStats) -> String
    )

    private val phrases: List<Phrase> = listOf(
        // ── Common (70%) ────────────────────────────────────────────────────
        Phrase(Rarity.COMMON) { ctx, s ->
            val n = if (s.totalPages > 0) s.totalPages / 88 else 0
            ctx.getString(R.string.sw_phrase_01, n)
        },
        Phrase(Rarity.COMMON) { ctx, s ->
            val n = if (s.totalPages > 0) s.totalPages / 300 else 0
            ctx.getString(R.string.sw_phrase_02, n)
        },
        Phrase(Rarity.COMMON) { ctx, s ->
            ctx.getString(R.string.sw_phrase_03, s.totalBooks)
        },
        Phrase(Rarity.COMMON) { ctx, s ->
            ctx.getString(R.string.sw_phrase_04, s.activeDays)
        },
        Phrase(Rarity.COMMON) { ctx, s ->
            val h = s.totalMinutes / 60
            ctx.getString(R.string.sw_phrase_05, h)
        },
        Phrase(Rarity.COMMON) { ctx, s ->
            ctx.getString(R.string.sw_phrase_06, s.currentStreak)
        },
        Phrase(Rarity.COMMON) { ctx, s ->
            ctx.getString(R.string.sw_phrase_07, s.bestStreak)
        },
        Phrase(Rarity.COMMON) { ctx, s ->
            val n = if (s.totalPages > 0) s.totalPages / 250 else 0
            ctx.getString(R.string.sw_phrase_08, n)
        },
        Phrase(Rarity.COMMON) { ctx, s ->
            val km = s.totalPages * 20 / 100 // ~20cm per page stacked
            ctx.getString(R.string.sw_phrase_09, km)
        },
        Phrase(Rarity.COMMON) { ctx, s ->
            val n = s.totalMinutes / 120
            ctx.getString(R.string.sw_phrase_10, n)
        },
        Phrase(Rarity.COMMON) { ctx, s ->
            ctx.getString(R.string.sw_phrase_11, s.totalPages)
        },
        Phrase(Rarity.COMMON) { ctx, s ->
            val n = if (s.totalPages > 0) s.totalPages / 200 else 0
            ctx.getString(R.string.sw_phrase_12, n)
        },
        Phrase(Rarity.COMMON) { ctx, s ->
            val d = s.totalMinutes / (60 * 24)
            ctx.getString(R.string.sw_phrase_13, d)
        },
        Phrase(Rarity.COMMON) { ctx, s ->
            val n = s.totalMinutes / 150
            ctx.getString(R.string.sw_phrase_14, n)
        },
        Phrase(Rarity.COMMON) { ctx, s ->
            val n = if (s.totalPages > 0) s.totalPages / 500 else 0
            ctx.getString(R.string.sw_phrase_15, n)
        },
        Phrase(Rarity.COMMON) { ctx, s ->
            ctx.getString(R.string.sw_phrase_16, s.activeDays)
        },
        Phrase(Rarity.COMMON) { ctx, s ->
            val n = if (s.totalPages > 0) s.totalPages / 350 else 0
            ctx.getString(R.string.sw_phrase_17, n)
        },
        Phrase(Rarity.COMMON) { ctx, s ->
            val n = s.totalMinutes / 45
            ctx.getString(R.string.sw_phrase_18, n)
        },
        Phrase(Rarity.COMMON) { ctx, s ->
            ctx.getString(R.string.sw_phrase_19, s.totalBooks)
        },
        Phrase(Rarity.COMMON) { ctx, s ->
            val n = if (s.totalPages > 0) s.totalPages / 100 else 0
            ctx.getString(R.string.sw_phrase_20, n)
        },
        Phrase(Rarity.COMMON) { ctx, s ->
            val n = s.totalMinutes / 30
            ctx.getString(R.string.sw_phrase_21, n)
        },
        // ── Rare (20%) ──────────────────────────────────────────────────────
        Phrase(Rarity.RARE) { ctx, s ->
            val n = s.totalMinutes / 90
            ctx.getString(R.string.sw_phrase_22, n)
        },
        Phrase(Rarity.RARE) { ctx, s ->
            val n = s.totalMinutes / 130
            ctx.getString(R.string.sw_phrase_23, n)
        },
        Phrase(Rarity.RARE) { ctx, s ->
            val n = if (s.totalPages > 0) s.totalPages * 250 / 1000 else 0
            ctx.getString(R.string.sw_phrase_24, n)
        },
        Phrase(Rarity.RARE) { ctx, s ->
            val n = s.totalMinutes / 42
            ctx.getString(R.string.sw_phrase_25, n)
        },
        Phrase(Rarity.RARE) { ctx, s ->
            val n = s.totalMinutes / 180
            ctx.getString(R.string.sw_phrase_26, n)
        },
        Phrase(Rarity.RARE) { ctx, s ->
            val mm = s.totalPages * 130 / 1000 // ~130mm avg thickness per 300p
            ctx.getString(R.string.sw_phrase_27, mm)
        },
        Phrase(Rarity.RARE) { ctx, s ->
            val n = if (s.totalPages > 0) s.totalPages * 5 / 1000 else 0
            ctx.getString(R.string.sw_phrase_28, n)
        },
        Phrase(Rarity.RARE) { ctx, s ->
            val n = s.totalMinutes / 22
            ctx.getString(R.string.sw_phrase_29, n)
        },
        Phrase(Rarity.RARE) { ctx, s ->
            val kg = s.totalPages * 5 / 1000 // ~5g per page
            ctx.getString(R.string.sw_phrase_30, kg)
        },
        Phrase(Rarity.RARE) { ctx, s ->
            val n = if (s.totalPages > 0) s.totalPages / 1084 else 0
            ctx.getString(R.string.sw_phrase_31, n)
        },
        Phrase(Rarity.RARE) { ctx, s ->
            val n = s.totalMinutes / 60 / 8
            ctx.getString(R.string.sw_phrase_32, n)
        },
        Phrase(Rarity.RARE) { ctx, s ->
            val n = if (s.totalPages > 0) s.totalPages / 180 else 0
            ctx.getString(R.string.sw_phrase_33, n)
        },
        // ── Epic (9%) ───────────────────────────────────────────────────────
        Phrase(Rarity.EPIC) { ctx, _ ->
            ctx.getString(R.string.sw_phrase_34)
        },
        Phrase(Rarity.EPIC) { ctx, _ ->
            ctx.getString(R.string.sw_phrase_35)
        },
        Phrase(Rarity.EPIC) { ctx, _ ->
            ctx.getString(R.string.sw_phrase_36)
        },
        Phrase(Rarity.EPIC) { ctx, _ ->
            ctx.getString(R.string.sw_phrase_37)
        },
        Phrase(Rarity.EPIC) { ctx, _ ->
            ctx.getString(R.string.sw_phrase_38)
        },
        Phrase(Rarity.EPIC) { ctx, _ ->
            ctx.getString(R.string.sw_phrase_39)
        },
        Phrase(Rarity.EPIC) { ctx, _ ->
            ctx.getString(R.string.sw_phrase_40)
        },
        // ── Legendary (1%) ──────────────────────────────────────────────────
        Phrase(Rarity.LEGENDARY) { ctx, _ ->
            ctx.getString(R.string.sw_phrase_41)
        },
        Phrase(Rarity.LEGENDARY) { ctx, _ ->
            ctx.getString(R.string.sw_phrase_42)
        },
        Phrase(Rarity.LEGENDARY) { ctx, _ ->
            ctx.getString(R.string.sw_phrase_43)
        }
    )

    fun selectPhrase(context: Context, stats: GlobalStats): String {
        val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val seed = (year * 1000L + dayOfYear)
        val rng = Random(seed)

        // Determine rarity by weighted random
        val roll = rng.nextInt(100)
        val targetRarity = when {
            roll < 1  -> Rarity.LEGENDARY
            roll < 10 -> Rarity.EPIC
            roll < 30 -> Rarity.RARE
            else      -> Rarity.COMMON
        }

        val candidates = phrases.filter { it.rarity == targetRarity }
        if (candidates.isEmpty()) return ""

        val idx = rng.nextInt(candidates.size)
        return try {
            candidates[idx].resolve(context, stats)
        } catch (_: Exception) {
            ""
        }
    }
}
