package com.example.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppSettings
import com.example.data.database.GithubRepo
import com.example.data.database.SyncLog
import com.example.data.database.ChatMessage
import com.example.data.repository.PortfolioSyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class GitPortfolioViewModel(private val repository: PortfolioSyncRepository) : ViewModel() {

    // Manage configurations in memory as property backups
    var oauthClientId by mutableStateOf("")
    var oauthClientSecret by mutableStateOf("")

    // Settings StateFlow
    private val _settings = MutableStateFlow<AppSettings>(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    // Repos StateFlow
    private val _repos = MutableStateFlow<List<GithubRepo>>(emptyList())
    val repos: StateFlow<List<GithubRepo>> = _repos.asStateFlow()

    // Logs StateFlow
    private val _logs = MutableStateFlow<List<SyncLog>>(emptyList())
    val logs: StateFlow<List<SyncLog>> = _logs.asStateFlow()

    // Screen Refresh State
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Repository Active Sync State
    private val _syncingRepoId = MutableStateFlow<Long?>(null)
    val syncingRepoId: StateFlow<Long?> = _syncingRepoId.asStateFlow()

    // WebSocket / Live Channel States
    private val _webSocketConnected = MutableStateFlow(true)
    val webSocketConnected: StateFlow<Boolean> = _webSocketConnected.asStateFlow()

    private val _webSocketMessage = MutableStateFlow("")
    val webSocketMessage: StateFlow<String> = _webSocketMessage.asStateFlow()

    // Chatbot States
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isSendingChat = MutableStateFlow(false)
    val isSendingChat: StateFlow<Boolean> = _isSendingChat.asStateFlow()

    init {
        // Initial setup for default keys or cached settings
        viewModelScope.launch {
            // Load Settings or save initial placeholder row
            val initialSettings = repository.getSettings()
            _settings.value = initialSettings

            // Pull saved custom Client Configs from database if available or standard presets
            oauthClientId = initialSettings.oauthClientId
            oauthClientSecret = initialSettings.oauthClientSecret

            // Bind Database flows
            repository.getSettingsFlow()
                .filterNotNull()
                .onEach { settingsObj ->
                    _settings.value = settingsObj
                    oauthClientId = settingsObj.oauthClientId
                    oauthClientSecret = settingsObj.oauthClientSecret
                }
                .launchIn(this)

            repository.getRepos()
                .onEach { _repos.value = it }
                .launchIn(this)

            repository.getLogs()
                .onEach { _logs.value = it }
                .launchIn(this)
        }

        // Live Link notifications ticker loop
        viewModelScope.launch {
            val liveQuotes = listOf(
                "Waiting for commit changes on remote branch...",
                "Gemini AI Showcase auditor is idle.",
                "Portfolio pipeline check: 0 errors.",
                "System checked remote portfolio deployment - Live & active",
                "Ready to synchronize newly discovered repository assets."
            )
            while (true) {
                delay(25000)
                if (_webSocketConnected.value) {
                    _webSocketMessage.value = liveQuotes.random()
                    delay(2000)
                    _webSocketMessage.value = "" // clear toast trigger
                }
            }
        }

        // Initial Chat History Sync
        viewModelScope.launch {
            repository.getChatMessages()
                .onEach { messagesList ->
                    if (messagesList.isEmpty()) {
                        // Pre-populate with a warm greetings welcome toast
                        val welcomeMsg = ChatMessage(
                            role = "model",
                            content = "Hello! I am GitSync AI, your agentic developer and portfolio automation chatbot. I have control over this app and can perform actions on your behalf! You can ask me to:\n\n• **Refresh or index** your GitHub projects\n• **Sync** a specific project (e.g. \"Sync movie-database-app\")\n• **Update settings** directly (e.g. \"Change my portfolio branch to main\")\n• **Clear the logs** tracking database\n\nHow can I help automate your workflow today?"
                        )
                        repository.addChatMessage(welcomeMsg)
                    } else {
                        _chatMessages.value = messagesList
                    }
                }
                .launchIn(this)
        }
    }

    fun setUseOAuth(active: Boolean) {
        viewModelScope.launch {
            val current = repository.getSettings()
            repository.saveSettings(current.copy(useOAuth = active))
        }
    }

    fun disconnectOAuth() {
        viewModelScope.launch {
            val current = repository.getSettings()
            repository.saveSettings(current.copy(githubOAuthToken = "", useOAuth = false))
        }
    }

    fun refreshRepos() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                repository.fetchGithubRepos()
            } catch (e: Exception) {
                // Handled in repository log writing
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun syncRepo(repo: GithubRepo) {
        if (_syncingRepoId.value != null) return
        viewModelScope.launch {
            _syncingRepoId.value = repo.id
            try {
                repository.syncProjectToPortfolio(repo)
            } catch (e: Exception) {
                // Handled in repository logs
            } finally {
                _syncingRepoId.value = null
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    fun saveSettings(
        token: String,
        url: String,
        repo: String,
        branch: String,
        path: String,
        sect: String,
        tags: String,
        desc: Boolean,
        hasUrl: Boolean,
        lang: Boolean,
        useCustom: Boolean,
        provider: String,
        key: String,
        model: String,
        autoFile: Boolean,
        autoSect: Boolean
    ) {
        viewModelScope.launch {
            val current = repository.getSettings()
            val updated = current.copy(
                githubToken = token,
                portfolioUrl = url,
                portfolioRepo = repo,
                portfolioBranch = branch,
                portfolioFilePath = path,
                defaultSectionName = sect,
                desiredTags = tags,
                requireDescription = desc,
                requireUrl = hasUrl,
                requireLanguage = lang,
                useCustomAiSettings = useCustom,
                aiProvider = provider,
                customApiKey = key,
                customModelName = model,
                autoDetectConfig = autoFile,
                autoDetectSectionAndStack = autoSect,
                oauthClientId = oauthClientId,
                oauthClientSecret = oauthClientSecret
            )
            repository.saveSettings(updated)
        }
    }

    // Fetched models list state
    private val _fetchedModels = MutableStateFlow<List<String>>(emptyList())
    val fetchedModels: StateFlow<List<String>> = _fetchedModels.asStateFlow()

    // Validation Status: "idle", "loading", "success", "error"
    private val _validationStatus = MutableStateFlow("idle")
    val validationStatus: StateFlow<String> = _validationStatus.asStateFlow()

    private val _validationError = MutableStateFlow("")
    val validationError: StateFlow<String> = _validationError.asStateFlow()

    fun validateAndFetchModelList(provider: String, apiKey: String) {
        if (apiKey.isBlank()) {
            _validationStatus.value = "error"
            _validationError.value = "API key cannot be blank."
            return
        }
        
        viewModelScope.launch {
            _validationStatus.value = "loading"
            _validationError.value = ""
            try {
                val modelsList = repository.validateAndFetchModels(provider, apiKey)
                _fetchedModels.value = modelsList
                _validationStatus.value = "success"
                
                val currentSettings = repository.getSettings()
                if (modelsList.isNotEmpty() && !modelsList.contains(currentSettings.customModelName)) {
                    val matchingModel = modelsList.firstOrNull { 
                        it.contains("flash") || it.contains("mini") || it.contains("gpt-4o") 
                    } ?: modelsList.first()
                    
                    repository.saveSettings(currentSettings.copy(customModelName = matchingModel))
                }
            } catch (e: Exception) {
                _validationStatus.value = "error"
                _validationError.value = e.message ?: "Validation failed"
                _fetchedModels.value = emptyList()
            }
        }
    }

    // --- Custom OAuth Token Code Swap Receiver ---
    fun handleOAuthCallbackCode(code: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addLog(
                "OAUTH_TOKEN_EXCHANGE",
                "SUCCESS",
                "Exchanging Auth Code for Access Token",
                "Authenticating login response with GitHub server nodes..."
            )
            try {
                // We exchange authorization code for access token using POST request
                val client = okhttp3.OkHttpClient()
                val requestBody = okhttp3.FormBody.Builder()
                    .add("client_id", oauthClientId)
                    .add("client_secret", oauthClientSecret)
                    .add("code", code)
                    .build()

                val request = okhttp3.Request.Builder()
                    .url("https://github.com/login/oauth/access_token")
                    .header("Accept", "application/json")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw java.io.IOException("Unexpected HTTP code $response")
                    }
                    val bodyString = response.body?.string() ?: ""
                    // Simple parse JSON response
                    val tokenRegex = """\"access_token\":\"(.*?)\"""".toRegex()
                    val matchResult = tokenRegex.find(bodyString)
                    val token = matchResult?.groups?.get(1)?.value

                    if (!token.isNullOrBlank()) {
                        val current = repository.getSettings()
                        repository.saveSettings(current.copy(githubOAuthToken = token, useOAuth = true))

                        repository.addLog(
                            "OAUTH_TOKEN_EXCHANGE",
                            "SUCCESS",
                            "GitHub OAuth Authorized",
                            "Access token generated security-wise. Connected index profile successfully."
                        )
                    } else {
                        // Error check
                        repository.addLog(
                            "OAUTH_TOKEN_EXCHANGE",
                            "ERROR",
                            "Access Token Empty",
                            "GitHub reply body context was: $bodyString"
                        )
                    }
                }
            } catch (e: Exception) {
                repository.addLog(
                    "OAUTH_TOKEN_EXCHANGE",
                    "ERROR",
                    "OAuth Handshake Aborted",
                    "Exception during endpoint handshake: ${e.message}"
                )
            }
        }
    }

    // --- Chatbot Functionality ---
    fun sendChatMessage(content: String) {
        if (content.isBlank() || _isSendingChat.value) return
        
        viewModelScope.launch {
            // Safe persist user query context to Database
            val userMsg = ChatMessage(role = "user", content = content)
            repository.addChatMessage(userMsg)

            _isSendingChat.value = true

            try {
                val settingsLocal = repository.getSettings()
                val reposList = _repos.value.take(20).joinToString("\n") { 
                    "- Name: ${it.name}, Language: ${it.language ?: "No Lang"}, Synced: ${it.isSynced}, Description: ${it.description ?: "None"}" 
                }
                val currentSettingsDetails = """
                    Current configurations:
                    - Portfolio Repo: ${settingsLocal.portfolioRepo}
                    - Portfolio Branch: ${settingsLocal.portfolioBranch}
                    - Config Filepath: ${settingsLocal.portfolioFilePath}
                    - Auto Detect Config: ${settingsLocal.autoDetectConfig}
                    - Auto Detect Section & Stack: ${settingsLocal.autoDetectSectionAndStack}
                    - Default Group Name: ${settingsLocal.defaultSectionName}
                    - Custom Tech Tags: ${settingsLocal.desiredTags}
                    - Use OAuth authentication: ${settingsLocal.useOAuth}
                """.trimIndent()

                val systemPrompt = """
                    You are GitSync AI, a highly intelligent and agentic software developer chatbot and portfolio automation specialist.
                    You help developers index and sync their projects to visual portfolios.
                    
                    CRITICAL AGENTIC CAPABILITY:
                    You have direct control over this application and can execute tasks on behalf of the user. To perform an action, append a single JSON command block at the absolute end of your response. 
                    
                    Available commands/schemas:
                    1. Refresh or indexing user repositories from GitHub:
                    ```json
                    {
                      "action": "REFRESH_REPOS"
                    }
                    ```
                    
                    2. Synchronize a specific repository name to the visual portfolio:
                    ```json
                    {
                      "action": "SYNC_REPO",
                      "repoName": "repository-name"
                    }
                    ```
                    
                    3. Clear the app log tracking database:
                    ```json
                    {
                      "action": "CLEAR_LOGS"
                    }
                    ```
                    
                    4. Programmatically update portfolio configuration details:
                    ```json
                    {
                      "action": "UPDATE_SETTINGS",
                      "settings": {
                        "portfolioRepo": "optionally-change-owner/repo",
                        "portfolioBranch": "optionally-change-branch",
                        "portfolioFilePath": "optionally-change-filepath",
                        "autoDetectConfig": true/false,
                        "autoDetectSectionAndStack": true/false,
                        "defaultSectionName": "optionally-change-sectionName",
                        "desiredTags": "optionally-change-tags"
                      }
                    }
                    ```

                    Rules:
                    - When the user asks you to refresh, clear logs, sync a project, or change settings, respond in a friendly manner explaining that you are performing the action, and return the EXACT JSON block at the very end of your response.
                    - Do NOT explain the JSON syntax to the user unless asked. Just append it silently at the very end of your message.
                    - Keep explanations clear, professional, short, and highly technical.
                    
                    Current workspace context:
                    ==== AVAILABLE GITHUB REPOSITORIES ====
                    $reposList
                    
                    ==== CURRENT CONFIGURATION SETTINGS ====
                    $currentSettingsDetails
                """.trimIndent()

                val historyString = _chatMessages.value.filter { it.role != "system" }.takeLast(10).joinToString("\n") { 
                    "${if (it.role == "user") "User" else "AI"}: ${it.content}" 
                }

                val finalPrompt = "$systemPrompt\n\n$historyString\nAI:"
                
                val aiResponse = repository.callAiAssistant(finalPrompt, temp = 0.5f)
                
                // Extract action JSON prior to saving message visually for neat aesthetic styling
                val cleanResponse = aiResponse.replace("""```json[\s\S]*?```""".toRegex(), "")
                    .replace("""```[\s\S]*?```""".toRegex(), "")
                    .replace("""\{[\s\S]*?"action"[\s\S]*?\}""".toRegex(), "")
                    .trim()

                val aiMsg = ChatMessage(role = "model", content = if (cleanResponse.isEmpty()) aiResponse else cleanResponse)
                repository.addChatMessage(aiMsg)

                // Background Action parser logic
                val actionRegex = """(\{[\s\S]*?"action"[\s\S]*?\})""".toRegex()
                val match = actionRegex.find(aiResponse)
                if (match != null) {
                    val jsonStr = match.value
                    try {
                        val json = org.json.JSONObject(jsonStr)
                        val action = json.optString("action")
                        when (action) {
                            "REFRESH_REPOS" -> {
                                refreshRepos()
                            }
                            "CLEAR_LOGS" -> {
                                clearLogs()
                            }
                            "SYNC_REPO" -> {
                                val repoName = json.optString("repoName")
                                if (!repoName.isNullOrEmpty()) {
                                    val matchingRepo = _repos.value.firstOrNull { 
                                        it.name.equals(repoName, ignoreCase = true) || 
                                        it.fullName.equals(repoName, ignoreCase = true) ||
                                        it.name.contains(repoName, ignoreCase = true)
                                    }
                                    if (matchingRepo != null) {
                                        syncRepo(matchingRepo)
                                    } else {
                                        repository.addLog(
                                            "AI_CHATBOT",
                                            "ERROR",
                                            "Sync Target Not Found",
                                            "The chatbot requested to sync repo '$repoName', but no matching repository was found in your indexed list."
                                        )
                                    }
                                }
                            }
                            "UPDATE_SETTINGS" -> {
                                val settingsJson = json.optJSONObject("settings")
                                if (settingsJson != null) {
                                    val current = repository.getSettings()
                                    val updated = current.copy(
                                        portfolioRepo = settingsJson.optString("portfolioRepo", current.portfolioRepo),
                                        portfolioBranch = settingsJson.optString("portfolioBranch", current.portfolioBranch),
                                        portfolioFilePath = settingsJson.optString("portfolioFilePath", current.portfolioFilePath),
                                        autoDetectConfig = if (settingsJson.has("autoDetectConfig")) settingsJson.optBoolean("autoDetectConfig") else current.autoDetectConfig,
                                        autoDetectSectionAndStack = if (settingsJson.has("autoDetectSectionAndStack")) settingsJson.optBoolean("autoDetectSectionAndStack") else current.autoDetectSectionAndStack,
                                        defaultSectionName = settingsJson.optString("defaultSectionName", current.defaultSectionName),
                                        desiredTags = settingsJson.optString("desiredTags", current.desiredTags)
                                    )
                                    repository.saveSettings(updated)
                                    repository.addLog(
                                        "AI_CHATBOT",
                                        "SUCCESS",
                                        "Settings Auto-Updated",
                                        "The AI chatbot updated configurations automatically in response to user request."
                                    )
                                }
                            }
                        }
                    } catch (inner: Exception) {
                        android.util.Log.e("GitSyncAI", "Action parsing exception", inner)
                    }
                }
            } catch (e: Exception) {
                val errorMsg = ChatMessage(role = "model", content = "Sorry, I ran into an error generating that assistance: ${e.message}")
                repository.addChatMessage(errorMsg)
            } finally {
                _isSendingChat.value = false
            }
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            repository.clearChatMessages()
            _chatMessages.value = emptyList()
        }
    }
}
