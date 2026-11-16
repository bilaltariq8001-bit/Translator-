package com.translator.app.core.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** One saved AI conversation, as listed in the chat history. */
data class ChatSession(val id: Long, val title: String, val updatedAt: Long, val messageCount: Int, val favorite: Boolean, val preview: String)

/** One message of a saved conversation, in the order it was written. */
data class StoredMessage(val fromUser: Boolean, val text: String)

/** Saved AI conversations. They live only on this phone, like the translation history. */
interface ChatRepository {
    val sessions: Flow<List<ChatSession>>
    suspend fun messages(sessionId: Long): List<StoredMessage>
    suspend fun createSession(title: String): Long
    suspend fun addMessage(sessionId: Long, fromUser: Boolean, text: String)
    suspend fun setFavorite(sessionId: Long, favorite: Boolean)
    suspend fun delete(sessionId: Long)
    suspend fun clear()
}

@Entity(tableName = "chat_session")
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "0") val favorite: Boolean = false,
)

@Entity(
    tableName = "chat_message",
    foreignKeys = [ForeignKey(ChatSessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")],
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val fromUser: Boolean,
    val text: String,
    val timeMillis: Long,
)

data class ChatSessionRow(
    val id: Long,
    val title: String,
    val updatedAt: Long,
    @ColumnInfo(name = "messageCount") val messageCount: Int,
    val favorite: Boolean,
    val preview: String?,
)

@Dao
interface ChatDao {
    @Query(
        "SELECT s.id AS id, s.title AS title, s.updatedAt AS updatedAt, " +
            "(SELECT COUNT(*) FROM chat_message m WHERE m.sessionId = s.id) AS messageCount, s.favorite AS favorite, " +
            "(SELECT m.text FROM chat_message m WHERE m.sessionId = s.id ORDER BY m.id DESC LIMIT 1) AS preview " +
            "FROM chat_session s ORDER BY s.updatedAt DESC, s.id DESC",
    )
    fun observe(): Flow<List<ChatSessionRow>>

    @Query("SELECT * FROM chat_message WHERE sessionId = :sessionId ORDER BY id")
    suspend fun messages(sessionId: Long): List<ChatMessageEntity>

    @Insert
    suspend fun insertSession(entity: ChatSessionEntity): Long

    @Insert
    suspend fun insertMessage(entity: ChatMessageEntity): Long

    @Query("UPDATE chat_session SET updatedAt = :time WHERE id = :id")
    suspend fun touch(id: Long, time: Long)

    @Query("UPDATE chat_session SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Transaction
    suspend fun addMessage(entity: ChatMessageEntity) {
        insertMessage(entity)
        touch(entity.sessionId, entity.timeMillis)
    }

    @Query("DELETE FROM chat_session WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM chat_session")
    suspend fun clear()
}

class RoomChatRepository(private val dao: ChatDao, private val clock: () -> Long = System::currentTimeMillis) : ChatRepository {
    override val sessions: Flow<List<ChatSession>> = dao.observe().map { rows -> rows.map { ChatSession(it.id, it.title, it.updatedAt, it.messageCount, it.favorite, it.preview.orEmpty()) } }

    override suspend fun messages(sessionId: Long) = dao.messages(sessionId).map { StoredMessage(it.fromUser, it.text) }
    override suspend fun createSession(title: String) = dao.insertSession(ChatSessionEntity(title = title, updatedAt = clock()))
    override suspend fun addMessage(sessionId: Long, fromUser: Boolean, text: String) =
        dao.addMessage(ChatMessageEntity(sessionId = sessionId, fromUser = fromUser, text = text, timeMillis = clock()))

    override suspend fun setFavorite(sessionId: Long, favorite: Boolean) = dao.setFavorite(sessionId, favorite)
    override suspend fun delete(sessionId: Long) = dao.delete(sessionId)
    override suspend fun clear() = dao.clear()
}

/** Version 1 had only the translation history; version 2 adds saved chats without touching it. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `chat_session` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `chat_message` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, " +
                "`fromUser` INTEGER NOT NULL, `text` TEXT NOT NULL, `timeMillis` INTEGER NOT NULL, " +
                "FOREIGN KEY(`sessionId`) REFERENCES `chat_session`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_message_sessionId` ON `chat_message` (`sessionId`)")
    }
}

/** Version 3 lets a saved chat be marked as a favorite. Existing chats start as not favorite. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `chat_session` ADD COLUMN `favorite` INTEGER NOT NULL DEFAULT 0")
    }
}
