package com.lecturameter.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lecturameter.model.Book
import com.lecturameter.model.BookStatus
import com.lecturameter.model.ReadingSession
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class GlobalStats(
    val totalBooks: Int,
    val totalPages: Int,
    val totalMinutes: Int,
    val activeDays: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    val peakHourSlot: Int?,
    val favoriteGenre: String?,
    // Monthly comparison
    val thisMonthPages: Int,
    val lastMonthPages: Int,
    val thisMonthMinutes: Int,
    val lastMonthMinutes: Int,
    val thisMonthBooks: Int,
    val lastMonthBooks: Int,
    val thisMonthStreak: Int,
    val lastMonthStreak: Int,
    val thisMonthSessionCount: Int,
    val lastMonthSessionCount: Int
)

object StatsWidgetDataProvider {

    fun loadAllBooks(context: Context): List<Book> {
        return try {
            val prefs = context.getSharedPreferences("lecturameter", Context.MODE_PRIVATE)
            val json = prefs.getString("books", null) ?: return emptyList()
            val books: List<Book> = Gson().fromJson(
                json,
                object : TypeToken<List<Book>>() {}.type
            ) ?: emptyList()
            books.map { b ->
                b.copy(
                    title      = b.title      ?: "",
                    author     = b.author     ?: "",
                    genres     = b.genres     ?: emptyList(),
                    editions   = b.editions   ?: emptyList(),
                    dateEvents = b.dateEvents ?: emptyList()
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun compute(books: List<Book>, sessions: List<ReadingSession>): GlobalStats {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        val today = sdf.format(cal.time)
        val thisMonthKey = today.substring(0, 7) // yyyy-MM
        cal.add(Calendar.MONTH, -1)
        val lastMonthKey = sdf.format(cal.time).substring(0, 7)
        cal.timeInMillis = System.currentTimeMillis() // reset

        val finishedBooks = books.filter { it.status == BookStatus.FINISHED }
        val totalBooks = finishedBooks.size
        val totalPages = sessions.sumOf { it.pages }
        val totalMinutes = sessions.sumOf { it.minutes ?: 0 }
        val sessionDates = sessions.filter { it.date.isNotBlank() }.map { it.date }.toHashSet()
        val activeDays = sessionDates.size

        // Streak calculation (same logic as BooksViewModel.currentReadingStreak)
        val currentStreak = computeCurrentStreak(sessionDates)
        val bestStreak = computeBestStreak(sessionDates)

        val peakHourSlot = computePeakHourSlot(sessions)

        // Favorite genre from finished books
        val favoriteGenre = finishedBooks
            .flatMap { it.genres }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

        // Monthly stats
        val thisMonthSessions = sessions.filter { it.date.startsWith(thisMonthKey) }
        val lastMonthSessions = sessions.filter { it.date.startsWith(lastMonthKey) }
        val thisMonthPages = thisMonthSessions.sumOf { it.pages }
        val lastMonthPages = lastMonthSessions.sumOf { it.pages }
        val thisMonthMinutes = thisMonthSessions.sumOf { it.minutes ?: 0 }
        val lastMonthMinutes = lastMonthSessions.sumOf { it.minutes ?: 0 }

        val thisMonthBooks = finishedBooks.count { it.endDate?.startsWith(thisMonthKey) == true }
        val lastMonthBooks = finishedBooks.count { it.endDate?.startsWith(lastMonthKey) == true }

        val thisMonthDates = thisMonthSessions.map { it.date }.toHashSet()
        val lastMonthDates = lastMonthSessions.map { it.date }.toHashSet()
        val thisMonthStreak = computeBestStreakInSet(thisMonthDates)
        val lastMonthStreak = computeBestStreakInSet(lastMonthDates)

        return GlobalStats(
            totalBooks = totalBooks,
            totalPages = totalPages,
            totalMinutes = totalMinutes,
            activeDays = activeDays,
            currentStreak = currentStreak,
            bestStreak = bestStreak,
            peakHourSlot = peakHourSlot,
            favoriteGenre = favoriteGenre,
            thisMonthPages = thisMonthPages,
            lastMonthPages = lastMonthPages,
            thisMonthMinutes = thisMonthMinutes,
            lastMonthMinutes = lastMonthMinutes,
            thisMonthBooks = thisMonthBooks,
            lastMonthBooks = lastMonthBooks,
            thisMonthStreak = thisMonthStreak,
            lastMonthStreak = lastMonthStreak,
            thisMonthSessionCount = thisMonthSessions.size,
            lastMonthSessionCount = lastMonthSessions.size
        )
    }

    private fun computeCurrentStreak(daysWithSession: Set<String>): Int {
        if (daysWithSession.isEmpty()) return 0
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        var anchor = sdf.format(cal.time)
        if (anchor !in daysWithSession) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
            anchor = sdf.format(cal.time)
            if (anchor !in daysWithSession) return 0
        }
        var streak = 0
        while (sdf.format(cal.time) in daysWithSession) {
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return streak
    }

    private fun computeBestStreak(daysWithSession: Set<String>): Int {
        val valid = daysWithSession.filter { it.isNotBlank() }
        if (valid.isEmpty()) return 0
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sorted = valid.sortedBy { it }
        var best = 1
        var current = 1
        val cal = Calendar.getInstance()
        for (i in 1 until sorted.size) {
            val prev = try { sdf.parse(sorted[i - 1]) } catch (_: Exception) { null } ?: continue
            try { sdf.parse(sorted[i]) } catch (_: Exception) { null } ?: continue
            cal.time = prev
            cal.add(Calendar.DAY_OF_YEAR, 1)
            if (sdf.format(cal.time) == sorted[i]) {
                current++
                if (current > best) best = current
            } else {
                current = 1
            }
        }
        return best
    }

    /** Best streak within a set of dates (for monthly comparison). */
    private fun computeBestStreakInSet(dates: Set<String>): Int {
        val valid = dates.filter { it.isNotBlank() }
        if (valid.isEmpty()) return 0
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sorted = valid.sortedBy { it }
        var best = 1
        var current = 1
        val cal = Calendar.getInstance()
        for (i in 1 until sorted.size) {
            val prev = try { sdf.parse(sorted[i - 1]) } catch (_: Exception) { null } ?: continue
            cal.time = prev
            cal.add(Calendar.DAY_OF_YEAR, 1)
            if (sdf.format(cal.time) == sorted[i]) {
                current++
                if (current > best) best = current
            } else {
                current = 1
            }
        }
        return best
    }

    private fun computePeakHourSlot(sessions: List<ReadingSession>): Int? {
        val slots = sessions.mapNotNull { s ->
            s.startTimestamp?.let { ts ->
                if (ts <= 0) return@mapNotNull null
                val cal = Calendar.getInstance()
                cal.timeInMillis = ts
                cal.get(Calendar.HOUR_OF_DAY) / 3
            }
        }
        if (slots.isEmpty()) return null
        return slots.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    }
}
