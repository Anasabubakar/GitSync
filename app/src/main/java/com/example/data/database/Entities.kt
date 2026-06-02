package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_logs")
data class SyncLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val actionType: String, // "GITHUB_CHECK", "VISION_AUDIT", "PORTFOLIO_UPDATE", "PUSH"
    val status: String,     // "SUCCESS", "WARNING", "ERROR"
    val title: String,
    val details: String,
    val targetRepoName: String = ""
)

@Entity(tableName = "github_repos")
data class GithubRepo(
    @PrimaryKey val id: Long,
    val name: String,
    val description: String?,
    val htmlUrl: String,
    val isFork: Boolean,
    val createdAt: String,
    val latestCommitSha: String?,
    val latestCommitMessage: String?,
    val latestCommitDate: String?,
    val isInPortfolio: Boolean = false,
    val language: String?
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String, // "user", "model"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val githubToken: String = "",
    val portfolioUrl: String = "https://username.github.io",
    val portfolioRepo: String = "username/username.github.io",
    val portfolioBranch: String = "main",
    val portfolioFilePath: String = "src/data/projects.json",
    val lastSyncTime: Long = 0,
    val defaultSectionName: String = "Projects",
    val desiredTags: String = "Next.js, Tailwind, TypeScript",
    val requireDescription: Boolean = true,
    val requireUrl: Boolean = true,
    val requireLanguage: Boolean = false
)
