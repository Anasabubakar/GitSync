package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): AppSettings?

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<AppSettings?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: AppSettings)
}

@Dao
interface GithubRepoDao {
    @Query("SELECT * FROM github_repos ORDER BY name ASC")
    fun getAllRepos(): Flow<List<GithubRepo>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepos(repos: List<GithubRepo>)

    @Query("UPDATE github_repos SET isSynced = :isSynced, category = :category, tags = :tags WHERE id = :repoId")
    suspend fun updateRepoSyncStatus(repoId: Long, isSynced: Boolean, category: String, tags: String)

    @Query("DELETE FROM github_repos")
    suspend fun clearRepos()
}

@Dao
interface SyncLogDao {
    @Query("SELECT * FROM sync_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<SyncLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: SyncLog)

    @Query("DELETE FROM sync_logs")
    suspend fun clearLogging()
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()
}

@Database(entities = [AppSettings::class, GithubRepo::class, SyncLog::class, ChatMessage::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun githubRepoDao(): GithubRepoDao
    abstract fun syncLogDao(): SyncLogDao
    abstract fun chatMessageDao(): ChatMessageDao
}
