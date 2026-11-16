package com.translator.app

import com.translator.app.core.data.HistoryItem
import com.translator.app.feature.history.filterHistory
import com.translator.app.feature.history.relativeTime
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryFilterTest {
    private val items = listOf(
        HistoryItem(1, "en", "es", "Hi! How are you?", "¡Hola! ¿Cómo estás?", 3, favorite = true),
        HistoryItem(2, "en", "ja", "How much does this cost?", "これはいくらですか？", 2),
        HistoryItem(3, "es", "en", "La cuenta, por favor.", "The bill, please.", 1),
    )

    @Test fun emptyQuery_returnsAll() = assertEquals(3, filterHistory(items, "", false).size)
    @Test fun query_matchesSourceTranslationAndLanguageName() {
        assertEquals(listOf(2L), filterHistory(items, "cost", false).map { it.id })
        assertEquals(listOf(3L), filterHistory(items, "bill", false).map { it.id })
        assertEquals(listOf(2L), filterHistory(items, "japanese", false).map { it.id })
    }
    @Test fun favoritesOnly_filters() = assertEquals(listOf(1L), filterHistory(items, "", true).map { it.id })
    @Test fun justNow_underAMinute() = assertEquals("Just now", relativeTime(1_000_000, now = 1_030_000) { id -> if (id == com.translator.app.R.string.just_now) "Just now" else "?" })
}
