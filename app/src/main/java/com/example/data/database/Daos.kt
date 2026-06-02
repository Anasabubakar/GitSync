package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncLogDao {
    @Query("SELECT * FROM sync_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<SyncLog>>

    @Query("SELECT * FROM sync_logs ORDER BY timestamp DESC LIMIT 15")
    suspend fun getRecentLogsSnapshot(): List<SyncLog>

    @Query("SELECT * FROM sync_logs WHERE status = 'ERROR' ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestErrorLog(): SyncLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: SyncLog)

    @Query("DELETE FROM sync_logs")
    suspend fun clearLogs()
}

@Dao
interface GithubRepoDao {
    @Query("SELECT * FROM github_repos ORDER BY id DESC")
    fun getAllRepos(): Flow<List<GithubRepo>>

    @Query("SELECT * FROM github_repos ORDER BY id DESC")
    suspend fun getAllReposSnapshot(): List<GithubRepo>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepos(repos: List<GithubRepo>)

    @Update
    suspend fun updateRepo(repo: GithubRepo)

    @Query("DELETE FROM github_repos")
    suspend fun clearRepos()
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)

    @Query("DELETE FROM chat_messages")
    suspend fun clearChat()
}

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<AppSettings?>

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): AppSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: AppSettings)
}
