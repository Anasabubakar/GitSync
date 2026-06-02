package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.AppSettings
import com.example.data.database.ChatMessage
import com.example.data.database.GithubRepo
import com.example.data.database.SyncLog
import com.example.data.repository.PortfolioSyncRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class GitPortfolioViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = PortfolioSyncRepository(db)

    val logs: StateFlow<List<SyncLog>> = repository.allLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val repos: StateFlow<List<GithubRepo>> = repository.allRepos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<AppSettings> = repository.appSettings
        .filterNotNull()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val chatMessages: StateFlow<List<ChatMessage>> = repository.chatMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _isBotResponding = MutableStateFlow(false)
    val isBotResponding: StateFlow<Boolean> = _isBotResponding.asStateFlow()

    private val _syncProgress = MutableStateFlow("")
    val syncProgress: StateFlow<String> = _syncProgress.asStateFlow()

    // Structuring standard alerts/notifications system
    data class LogNotification(
        val isSuccess: Boolean,
        val title: String,
        val details: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val _activeNotification = MutableStateFlow<LogNotification?>(null)
    val activeNotification: StateFlow<LogNotification?> = _activeNotification.asStateFlow()

    init {
        // Initialize default settings if missing
        viewModelScope.launch {
            repository.getSettings()
        }
    }

    fun dismissNotification() {
        _activeNotification.value = null
    }

    fun saveSettings(
        token: String,
        url: String,
        repoPath: String,
        branch: String,
        filePath: String,
        defaultSection: String,
        tags: String,
        reqDesc: Boolean,
        reqUrl: Boolean,
        reqLang: Boolean
    ) {
        viewModelScope.launch {
            val current = repository.getSettings()
            repository.saveSettings(
                current.copy(
                    githubToken = token.trim(),
                    portfolioUrl = url.trim(),
                    portfolioRepo = repoPath.trim(),
                    portfolioBranch = branch.trim(),
                    portfolioFilePath = filePath.trim(),
                    defaultSectionName = defaultSection.trim(),
                    desiredTags = tags.trim(),
                    requireDescription = reqDesc,
                    requireUrl = reqUrl,
                    requireLanguage = reqLang
                )
            )
            // Trigger confirmation notice
            _activeNotification.value = LogNotification(
                isSuccess = true,
                title = "Configurations Saved!",
                details = "Your project matching rules, credentials, and Next.js variables were successfully stored locally."
            )
        }
    }

    /**
     * Executes the Core sync chain with advanced validations, error triage, and notification alerts
     */
    fun triggerSync() {
        if (_isSyncing.value) return
        _isSyncing.value = true
        _syncProgress.value = "Scanning for GitHub repository updates..."
        
        viewModelScope.launch {
            try {
                val gitSuccess = repository.syncGithubActivity()
                if (gitSuccess) {
                    val currentRepos = repos.value.filter { !it.isFork }.take(5) // focus on top 5 creative projects
                    if (currentRepos.isEmpty()) {
                        _syncProgress.value = "GitHub scanned, no primary original repositories found."
                        _activeNotification.value = LogNotification(
                            isSuccess = true,
                            title = "GitHub Scanned",
                            details = "GitHub was scanned successfully. However, no primary repositories matched your custom configuration and metadata criteria."
                        )
                    } else {
                        var index = 1
                        var newlyShowcasedCount = 0
                        for (r in currentRepos) {
                            _syncProgress.value = "Auditing '${r.name}' (${index}/${currentRepos.size}) against live portfolio..."
                            val updated = repository.auditPortfolioAndSyncRepo(r)
                            if (updated) newlyShowcasedCount++
                            index++
                        }
                        
                        // Check if we hit any errors during auditing
                        val latestErr = db.syncLogDao().getLatestErrorLog()
                        if (latestErr != null && latestErr.timestamp > (System.currentTimeMillis() - 40000)) {
                            _activeNotification.value = LogNotification(
                                isSuccess = false,
                                title = "Sync Completed with Warnings",
                                details = "Audit was executed, but warnings were logged: ${latestErr.title}. Details: ${latestErr.details}"
                            )
                        } else {
                            _activeNotification.value = LogNotification(
                                isSuccess = true,
                                title = "Sync Completed Successfully",
                                details = "Audit of original repositories complete. Your Next.js portfolio at ${settings.value.portfolioUrl} is up to date and beautifully aligned."
                            )
                        }
                    }
                } else {
                    // Fetch latest recorded error log to present inside the live dialog
                    val latestErr = db.syncLogDao().getLatestErrorLog()
                    _activeNotification.value = LogNotification(
                        isSuccess = false,
                        title = latestErr?.title ?: "Audit / Connection Interrupted",
                        details = latestErr?.details ?: "Synchronization halted. Please ensure your Personal Access Token, Gemini Key, and URL settings are verified and active."
                    )
                }
            } catch (e: Exception) {
                _syncProgress.value = "Error during automated sync: ${e.localizedMessage}"
                _activeNotification.value = LogNotification(
                    isSuccess = false,
                    title = "System Failure during Sync",
                    details = "Unhandled Sync Error exception: ${e.localizedMessage}"
                )
            } finally {
                _isSyncing.value = false
                _syncProgress.value = ""
                // Refresh settings timestamp
                val s = repository.getSettings()
                repository.saveSettings(s.copy(lastSyncTime = System.currentTimeMillis()))
            }
        }
    }

    fun deployGithubActionWorkflow() {
        viewModelScope.launch {
            val ok = repository.installGitHubActionWorkflow()
            if (ok) {
                _activeNotification.value = LogNotification(
                    isSuccess = true,
                    title = "CI/CD Action Committed",
                    details = "Beautifully configured daily recurring cron synchronization workflow committed natively to portfolio repository .github/workflows!"
                )
            } else {
                val latestErr = db.syncLogDao().getLatestErrorLog()
                _activeNotification.value = LogNotification(
                    isSuccess = false,
                    title = "Workflow Setup Aborted",
                    details = latestErr?.details ?: "Unable to commit background Action schedule. Ensure your GitHub Personal Access Token possesses 'repo' permission."
                )
            }
        }
    }

    fun sendChatMessage(messageContent: String) {
        if (messageContent.trim().isEmpty() || _isBotResponding.value) return
        
        viewModelScope.launch {
            _isBotResponding.value = true
            
            // 1. Insert User Message
            val userMsg = ChatMessage(role = "user", content = messageContent)
            repository.insertChatMessage(userMsg)
            
            // 2. Fetch Chat History & Run LLM + RAG with snapshot data context
            val currentHistory = chatMessages.value
            val response = repository.askAssistant(messageContent, currentHistory)
            
            // 3. Insert Bot Response
            val botMsg = ChatMessage(role = "model", content = response)
            repository.insertChatMessage(botMsg)
            
            _isBotResponding.value = false
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            repository.clearChat()
        }
    }

    fun resetAppStats() {
        viewModelScope.launch {
            repository.clearLogs()
            repository.clearRepos()
        }
    }
}
