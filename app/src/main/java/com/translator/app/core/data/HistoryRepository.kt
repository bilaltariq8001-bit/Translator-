package com.translator.app.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class HistoryItem(
    val id: Long,
    val sourceLang: String,
    val targetLang: String,
    val sourceText: String,
    val translation: String,
    val timeMillis: Long,
    val favorite: Boolean = false,
)

interface HistoryRepository {
    val items: Flow<List<HistoryItem>>
    suspend fun add(item: HistoryItem): Long
    suspend fun setFavorite(id: Long, favorite: Boolean)
    suspend fun delete(id: Long)
    suspend fun clear()
}

/** Held in memory for now; Room replaces this (history stays on the phone) in the history step of the plan. */
class InMemoryHistoryRepository : HistoryRepository {
    private val state = MutableStateFlow<List<HistoryItem>>(emptyList())
    private var nextId = 1L
    override val items: Flow<List<HistoryItem>> = state.asStateFlow()

    override suspend fun add(item: HistoryItem): Long {
        val id = nextId++
        state.update { listOf(item.copy(id = id)) + it }
        return id
    }

    override suspend fun setFavorite(id: Long, favorite: Boolean) {
        state.update { list -> list.map { if (it.id == id) it.copy(favorite = favorite) else it } }
    }

    override suspend fun delete(id: Long) { state.update { list -> list.filterNot { it.id == id } } }
    override suspend fun clear() { state.value = emptyList() }
}
