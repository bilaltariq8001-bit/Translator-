package com.translator.app.core.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceLang: String,
    val targetLang: String,
    val sourceText: String,
    val translation: String,
    val timeMillis: Long,
    val favorite: Boolean,
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY timeMillis DESC, id DESC")
    fun observe(): Flow<List<HistoryEntity>>

    @Insert
    suspend fun insert(entity: HistoryEntity): Long

    @Query("UPDATE history SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Database(entities = [HistoryEntity::class, ChatSessionEntity::class, ChatMessageEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun chatDao(): ChatDao
}

/** History and favorites live only on this phone. */
class RoomHistoryRepository(private val dao: HistoryDao) : HistoryRepository {
    override val items: Flow<List<HistoryItem>> = dao.observe().map { list ->
        list.map { HistoryItem(it.id, it.sourceLang, it.targetLang, it.sourceText, it.translation, it.timeMillis, it.favorite) }
    }

    override suspend fun add(item: HistoryItem): Long = dao.insert(
        HistoryEntity(0, item.sourceLang, item.targetLang, item.sourceText, item.translation, item.timeMillis, item.favorite),
    )

    override suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)
    override suspend fun delete(id: Long) = dao.delete(id)
    override suspend fun clear() = dao.clear()
}
