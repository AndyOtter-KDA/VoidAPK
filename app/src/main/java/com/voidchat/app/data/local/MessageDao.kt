package com.voidchat.app.data.local

import androidx.room.*
import com.voidchat.app.data.models.Message
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM local_messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesByChatId(chatId: String): Flow<List<Message>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message)

    @Query("DELETE FROM local_messages WHERE messageId = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("DELETE FROM local_messages WHERE chatId = :chatId")
    suspend fun deleteMessagesByChatId(chatId: String)

    @Query("DELETE FROM local_messages WHERE destroyed = 1")
    suspend fun deleteExpiredMessages()

    @Query("SELECT * FROM local_messages WHERE chatId = :chatId AND destroyed = 0")
    suspend fun getUndestroyedMessages(chatId: String): List<Message>

    @Query("SELECT * FROM local_messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessageForChat(chatId: String): Message?

    @Query("UPDATE local_messages SET destroyed = 1 WHERE messageId = :messageId")
    suspend fun markMessageDestroyed(messageId: String)
}
