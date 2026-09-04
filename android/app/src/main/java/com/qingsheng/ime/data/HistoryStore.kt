package com.qingsheng.ime.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Entity(tableName = "targets")
data class TargetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val platform: String,
    val stage: Int,
    val style: String,
    val factsJson: String = "[]",
    val eventsJson: String = "[]",
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetId: Long,
    val content: String,
    val sender: String,
    val emotion: String,
    val stageId: Int,
    val mode: String,
    val timestamp: Long
)

@Dao
interface TargetDao {
    @Query("SELECT * FROM targets ORDER BY updatedAt DESC LIMIT 1")
    suspend fun current(): TargetEntity?

    @Query("SELECT * FROM targets ORDER BY updatedAt DESC")
    suspend fun all(): List<TargetEntity>

    @Insert
    suspend fun insert(target: TargetEntity): Long

    @Update
    suspend fun update(target: TargetEntity)

    @Query("UPDATE targets SET stage = :stage, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateStage(id: Long, stage: Int, timestamp: Long)
}

@Dao
interface MessageDao {
    @Insert
    suspend fun insert(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE targetId = :targetId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recent(targetId: Long, limit: Int): List<MessageEntity>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recentLatest(limit: Int): List<MessageEntity>

    @Query("DELETE FROM messages WHERE targetId = :targetId")
    suspend fun clearFor(targetId: Long)
}

@Database(
    entities = [TargetEntity::class, MessageEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun targetDao(): TargetDao
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "qingsheng.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}

class HistoryStore(context: Context) {

    private val db = AppDatabase.get(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun ensureDefaultTarget(): TargetEntity {
        db.targetDao().current()?.let { return it }
        val now = System.currentTimeMillis()
        val target = TargetEntity(
            name = "默认目标",
            platform = "微信",
            stage = 2,
            style = "NATURAL",
            createdAt = now,
            updatedAt = now
        )
        val id = db.targetDao().insert(target)
        return target.copy(id = id)
    }

    fun recordIncoming(targetId: Long, content: String, emotion: String, stageId: Int, mode: String) {
        scope.launch {
            db.messageDao().insert(
                MessageEntity(
                    targetId = targetId,
                    content = content,
                    sender = SENDER_OTHER,
                    emotion = emotion,
                    stageId = stageId,
                    mode = mode,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    fun recordReply(targetId: Long, content: String, mode: String) {
        scope.launch {
            db.messageDao().insert(
                MessageEntity(
                    targetId = targetId,
                    content = content,
                    sender = SENDER_ME,
                    emotion = "",
                    stageId = 0,
                    mode = mode,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun recentMessages(limit: Int = 50): List<MessageEntity> {
        val target = ensureDefaultTarget()
        return db.messageDao().recent(target.id, limit)
    }

    companion object {
        const val SENDER_OTHER = "other"
        const val SENDER_ME = "me"
    }
}
