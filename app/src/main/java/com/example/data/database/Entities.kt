package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val githubToken: String = "",
    val githubOAuthToken: String = "",
    val useOAuth: Boolean = false,
    val portfolioUrl: String = "",
    val portfolioRepo: String = "",
    val portfolioBranch: String = "main",
    val portfolioFilePath: String = "src/data/projects.json",
    val defaultSectionName: String = "Projects",
    val desiredTags: String = "nextjs, tailwind, react",
    val requireDescription: Boolean = false,
    val requireUrl: Boolean = false,
    val requireLanguage: Boolean = false,
    val useCustomAiSettings: Boolean = false,
    val aiProvider: String = "Gemini",
    val customApiKey: String = "",
    val customModelName: String = "gemini-1.5-flash",
    val autoDetectConfig: Boolean = true,
    val autoDetectSectionAndStack: Boolean = true,
    val oauthClientId: String = "822cdca5-e59f-43e4-8fd9",
    val oauthClientSecret: String = ""
)

@Entity(tableName = "github_repos")
data class GithubRepo(
    @PrimaryKey val id: Long,
    val name: String,
    val fullName: String,
    val description: String?,
    val htmlUrl: String,
    val language: String?,
    val isSynced: Boolean = false,
    val category: String = "",
    val tags: String = ""
)

@Entity(tableName = "sync_logs")
data class SyncLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val actionType: String,
    val status: String,
    val title: String,
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val role: String, // "user" or "model"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
