package com.example.data.repository

import android.util.Base64
import com.example.BuildConfig
import com.example.data.api.*
import com.example.data.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.nio.charset.StandardCharsets

class PortfolioSyncRepository(private val db: AppDatabase) {

    suspend fun getSettings(): AppSettings {
        return db.appSettingsDao().getSettings() ?: AppSettings().also {
            db.appSettingsDao().saveSettings(it)
        }
    }

    suspend fun saveSettings(settings: AppSettings) {
        db.appSettingsDao().saveSettings(settings)
    }

    fun getSettingsFlow(): Flow<AppSettings?> {
        return db.appSettingsDao().getSettingsFlow()
    }

    fun getRepos(): Flow<List<GithubRepo>> {
        return db.githubRepoDao().getAllRepos()
    }

    fun getLogs(): Flow<List<SyncLog>> {
        return db.syncLogDao().getAllLogs()
    }

    fun getChatMessages(): Flow<List<ChatMessage>> {
        return db.chatMessageDao().getAllMessages()
    }

    suspend fun addChatMessage(message: ChatMessage) {
        db.chatMessageDao().insertMessage(message)
    }

    suspend fun clearChatMessages() {
        db.chatMessageDao().clearAll()
    }

    suspend fun clearLogs() {
        db.syncLogDao().clearLogging()
    }

    suspend fun addLog(actionType: String, status: String, title: String, details: String) {
        db.syncLogDao().insertLog(
            SyncLog(
                actionType = actionType,
                status = status,
                title = title,
                details = details
            )
        )
    }

    fun cleanAndParseRepoPath(urlOrPath: String): Pair<String, String> {
        val clean = urlOrPath.replace("https://github.com/", "")
            .replace("http://github.com/", "")
            .trim()
            .trim('/')
        val parts = clean.split("/")
        if (parts.size >= 2) {
            return Pair(parts[0], parts[1])
        }
        throw IllegalArgumentException("Invalid repository path or URL. Must be in 'owner/repository' format.")
    }

    suspend fun fetchGithubRepos(): List<GithubRepo> = withContext(Dispatchers.IO) {
        val settings = getSettings()
        val token = if (settings.useOAuth) settings.githubOAuthToken else settings.githubToken
        if (token.isBlank()) {
            addLog(
                "FETCH_REPOS",
                "ERROR",
                "Authentication Token Missing",
                if (settings.useOAuth) "Please authorize with GitHub first using the 'GitHub OAuth' login flow in Settings."
                else "Please configure a GitHub Personal Access Token in Settings."
            )
            throw IllegalStateException("Authentication credentials missing")
        }

        val authHeader = if (token.startsWith("ghp_") || token.startsWith("github_pat_")) "token $token" else "Bearer $token"
        addLog(
            "FETCH_REPOS",
            "SUCCESS",
            "Indexing Github Repositories",
            "Querying Github API endpoint is starting to import user repositories..."
        )

        try {
            val responseList = GithubApiClient.service.getUserRepos(token = authHeader)
            val converted = responseList.map {
                GithubRepo(
                    id = it.id,
                    name = it.name,
                    fullName = it.fullName,
                    description = it.description,
                    htmlUrl = it.htmlUrl,
                    language = it.language,
                    isSynced = false
                )
            }
            db.githubRepoDao().clearRepos()
            db.githubRepoDao().insertRepos(converted)
            addLog(
                "FETCH_REPOS",
                "SUCCESS",
                "Successfully Loaded Repositories",
                "Imported ${converted.size} projects successfully from your GitHub profile."
            )
            converted
        } catch (e: Exception) {
            addLog(
                "FETCH_REPOS",
                "ERROR",
                "Sync Failed",
                "Exception occurred while loading repositories: ${e.message}"
            )
            throw e
        }
    }

    suspend fun autoDetectPortfolioFilePath(
        owner: String,
        repo: String,
        branch: String,
        authHeader: String?
    ): String = withContext(Dispatchers.IO) {
        val commonPaths = listOf(
            "src/data/projects.json",
            "data/projects.json",
            "projects.json",
            "src/projects.json",
            "src/data/projects.ts",
            "data/projects.ts",
            "projects.ts",
            "src/projects.ts",
            "src/config/projects.json",
            "src/config/portfolio.json",
            "src/data/portfolio.json",
            "portfolio.json"
        )
        for (path in commonPaths) {
            try {
                val res = GithubApiClient.service.getFileContent(owner, repo, path, branch, authHeader)
                if (res.sha.isNotEmpty()) {
                    return@withContext path
                }
            } catch (e: Exception) {
                // Not found, try next
            }
        }
        "src/data/projects.json" // default fallback
    }

    suspend fun callAiAssistant(prompt: String, temp: Float): StringByCustomKey = withContext(Dispatchers.IO) {
        val settings = getSettings()
        if (settings.useCustomAiSettings) {
            val apiKey = settings.customApiKey
            if (apiKey.isBlank()) {
                throw IllegalStateException("Custom AI key is selected but empty in Settings.")
            }
            if (settings.aiProvider == "Gemini") {
                val modelUrl = "v1beta/models/${settings.customModelName}:generateContent"
                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                    generationConfig = GenerationConfig(temperature = temp)
                )
                val response = GeminiApiClient.service.generateContentCustom(url = modelUrl, apiKey = apiKey, request = request)
                return@withContext response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: throw Exception("Empty generation response from custom Gemini model.")
            } else {
                // OpenAI compatible
                val baseUrl = if (settings.customModelName.contains("deepseek")) {
                    "https://api.deepseek.com/"
                } else {
                    "https://api.openai.com/"
                }
                
                val authHeader = "Bearer $apiKey"
                val request = OpenAiRequest(
                    model = settings.customModelName,
                    messages = listOf(OpenAiMessage(role = "user", content = prompt)),
                    temperature = temp
                )
                
                val openAiClient = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(okhttp3.OkHttpClient())
                    .addConverterFactory(MoshiConverterFactory.create())
                    .build()
                
                val openAiService = openAiClient.create(OpenAiService::class.java)
                val response = openAiService.getCompletion(authHeader, request)
                return@withContext response.choices?.firstOrNull()?.message?.content
                    ?: throw Exception("Empty completion response from OpenAI Compatible model.")
            }
        } else {
            // Use environment or default AI models
            val defaultKey = BuildConfig.GEMINI_API_KEY
            if (defaultKey.isBlank()) {
                throw IllegalStateException("No API key configured. Secure your own AI key in 'Settings > Bring Your Own AI Key'.")
            }
            val request = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                generationConfig = GenerationConfig(temperature = temp)
            )
            val response = GeminiApiClient.service.generateContent(apiKey = defaultKey, request = request)
            return@withContext response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: throw Exception("Empty response from default Gemini system.")
        }
    }

    suspend fun validateAndFetchModels(provider: String, apiKey: String): List<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw IllegalArgumentException("API Key cannot be blank")
        }
        if (provider == "Gemini") {
            try {
                val response = GeminiApiClient.service.listModels(apiKey)
                val modelList = response.models?.mapNotNull { model ->
                    val rawName = model.name
                    if (rawName.startsWith("models/")) {
                        rawName.substringAfter("models/")
                    } else {
                        rawName
                    }
                }?.filter { model ->
                    model.contains("gemini")
                } ?: emptyList()
                
                if (modelList.isEmpty()) {
                    throw Exception("No valid Gemini models returned from API.")
                }
                modelList.sorted()
            } catch (e: Exception) {
                val errMsg = e.message ?: "Unknown Gemini API error"
                throw Exception("Gemini API key validation failed: $errMsg")
            }
        } else {
            val bases = listOf("https://api.openai.com/", "https://api.deepseek.com/")
            var lastError: Exception? = null
            for (base in bases) {
                try {
                    val client = Retrofit.Builder()
                        .baseUrl(base)
                        .client(okhttp3.OkHttpClient.Builder()
                            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                            .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                            .build())
                        .addConverterFactory(MoshiConverterFactory.create(
                            com.squareup.moshi.Moshi.Builder()
                                .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                                .build()
                        ))
                        .build()
                    val service = client.create(OpenAiService::class.java)
                    val response = service.listModels("Bearer $apiKey")
                    val models = response.data?.map { it.id } ?: emptyList()
                    if (models.isNotEmpty()) {
                        return@withContext models.filter { 
                            it.contains("gpt") || it.contains("deepseek") || it.contains("claude")
                        }.ifEmpty { models }.sorted()
                    }
                } catch (e: Exception) {
                    lastError = e
                }
            }
            throw Exception("OpenAI Compatible API key validation failed: ${lastError?.message ?: "Could not connect to OpenAI/Deepseek servers"}")
        }
    }

    private fun cleanResponseMarkdown(text: String): String {
        var clean = text.trim()
        if (clean.startsWith("```")) {
            val lines = clean.lines()
            if (lines.size >= 2) {
                // Remove first line (e.g. ```typescript or ```json)
                val firstClean = lines.subList(1, lines.size - 1)
                clean = firstClean.joinToString("\n").trim()
            }
        }
        // Also remove outer backticks if they are present
        if (clean.startsWith("```") && clean.endsWith("```")) {
            clean = clean.removePrefix("```").removeSuffix("```").trim()
        }
        return clean
    }

    suspend fun syncProjectToPortfolio(repo: GithubRepo): Boolean = withContext(Dispatchers.IO) {
        val settings = getSettings()
        val token = if (settings.useOAuth) settings.githubOAuthToken else settings.githubToken
        if (token.isBlank()) {
            addLog(
                "PORTFOLIO_UPDATE",
                "ERROR",
                "Credentials Missing",
                "Failed to update portfolio because github token or OAuth session was empty."
            )
            return@withContext false
        }
        val authHeader = if (token.startsWith("ghp_") || token.startsWith("github_pat_")) "token $token" else "Bearer $token"

        try {
            val repoInfo = cleanAndParseRepoPath(settings.portfolioRepo)
            val owner = repoInfo.first
            val repoName = repoInfo.second

            var targetFilePath = settings.portfolioFilePath
            if (settings.autoDetectConfig) {
                addLog(
                    "PORTFOLIO_UPDATE",
                    "SUCCESS",
                    "Scanning Portfolio Repo for Configuration",
                    "Scanning repo dynamically to locate projects database/array config..."
                )
                val detected = autoDetectPortfolioFilePath(owner, repoName, settings.portfolioBranch, authHeader)
                if (detected != targetFilePath) {
                    targetFilePath = detected
                    saveSettings(settings.copy(portfolioFilePath = detected))
                    addLog(
                        "PORTFOLIO_UPDATE",
                        "SUCCESS",
                        "Target Database Standard Path Detected",
                        "Auto-detected map: found database config target at: '$detected'"
                    )
                }
            }

            addLog(
                "PORTFOLIO_UPDATE",
                "SUCCESS",
                "Reading Web Database Settings",
                "Reading remote file asset matching: '$targetFilePath' from main codebase branch '${settings.portfolioBranch}'..."
            )

            val fileResponse = GithubApiClient.service.getFileContent(
                owner = owner,
                repo = repoName,
                path = targetFilePath,
                ref = settings.portfolioBranch,
                token = authHeader
            )

            val encodedContent = fileResponse.content?.replace("\n", "")?.replace("\r", "") ?: ""
            val currentFileContent = String(Base64.decode(encodedContent, Base64.DEFAULT), StandardCharsets.UTF_8)

            addLog(
                "PORTFOLIO_UPDATE",
                "SUCCESS",
                "AI Parsing & Showcase Engineering",
                "AI assistant is injecting repository showcase metadata dynamically..."
            )

            val automationPromptPart = if (settings.autoDetectSectionAndStack) {
                """
                - AUTOMATION ACTIVE: Automatically discover existing sections/categories inside the target file content (e.g. look for sections like "Projects", "OSS", "Fullstack", "Work", etc.) and append or insert this new project into the most appropriate section.
                - AUTOMATION ACTIVE: Automatically determine modern tags/skills/technologies for this project showcase (e.g. Next.js, React, Android, Compose, Kotlin, etc.) based on the repo name and primary language.
                """.trimIndent()
            } else {
                """
                - New project section category name is: "${settings.defaultSectionName}"
                - Desired tags/skills to assign to this new project showcase are: "${settings.desiredTags}"
                """.trimIndent()
            }

            val isProjectAlreadyInFile = currentFileContent.contains(repo.name, ignoreCase = true)
            if (isProjectAlreadyInFile) {
                addLog(
                    "PORTFOLIO_UPDATE",
                    "WARNING",
                    "Project Already Showcase Connected",
                    "Repo '${repo.name}' seems already referenced in visual array list. AI will verify sync adjustments."
                )
            }

            val refactorPrompt = """
                You are an expert Next.js, Tailwind CSS, and TypeScript full-stack engineer and design master.
                You need to modify the user's web project list file/component code to insert a new project beautifully.
                
                The repository details to sync:
                - Name: ${repo.name}
                - GitHub URL: ${repo.htmlUrl}
                - Description: ${repo.description ?: "A comprehensive software project developed on GitHub."}
                - Selected Core Language: ${repo.language ?: "TypeScript"}
                
                IMPORTANT TECH REQUIREMENT:
                - The portfolio is NOT HTML. It is a modern Next.js/Tailwind/TypeScript application.
                - Projects can be written in any programming language.
                - We are modifying the projects database file: $targetFilePath
                - You must parse and modify the file format appropriately (often a typescript array of items, or a JSON file configuration).
                
                The user has specified these custom placement configurations rules:
                $automationPromptPart
                
                Here is the current content of $targetFilePath:
                ---------
                $currentFileContent
                ---------
                
                Please insert a matching structured TypeScript object or JSON item into the projects array list file. 
                Maintain the exact original coding style, brackets, tab indentation, variable names, syntax, and semicolons.
                Include fields like tags, section, language, description, and link, deriving them beautifully and naturally.
                
                Respond with the COMPLETE modified file contents. Do NOT output any markdown codeblocks (do NOT wrap in ```), do not output explanations—ONLY output the raw updated file content.
            """.trimIndent()

            val updatedContent = callAiAssistant(refactorPrompt, 0.2f)
            val cleanedUpdatedContent = cleanResponseMarkdown(updatedContent)

            if (cleanedUpdatedContent.isBlank() || cleanedUpdatedContent == currentFileContent) {
                addLog(
                    "PORTFOLIO_UPDATE",
                    "WARNING",
                    "File Content Unchanged",
                    "The AI did not suggest a different state. Sync completes with no-op."
                )
                return@withContext true
            }

            addLog(
                "PORTFOLIO_UPDATE",
                "SUCCESS",
                "Updating Codebase Portfolio",
                "Base64 encoding changes and committing into remote repository structure..."
            )

            val encodedModified = Base64.encodeToString(cleanedUpdatedContent.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
            val updateRequest = GithubUpdateContentRequest(
                message = "Automated Showcase: Add project showcase '${repo.name}' [automated update]",
                content = encodedModified,
                sha = fileResponse.sha,
                branch = settings.portfolioBranch
            )

            GithubApiClient.service.updateFileContent(
                owner = owner,
                repo = repoName,
                path = targetFilePath,
                token = authHeader,
                request = updateRequest
            )

            val detectedSect = if (settings.autoDetectSectionAndStack) "Auto-Detected" else settings.defaultSectionName
            val detectedTags = if (settings.autoDetectSectionAndStack) "Auto-Analyzed" else settings.desiredTags

            db.githubRepoDao().updateRepoSyncStatus(
                repoId = repo.id,
                isSynced = true,
                category = detectedSect,
                tags = detectedTags
            )

            addLog(
                "PORTFOLIO_UPDATE",
                "SUCCESS",
                "Showcase Automation Succeeded!",
                "Showcased '${repo.name}' directly. Automatically pushed and synced to portfolio repository successfully."
            )
            true
        } catch (e: Exception) {
            val is404 = e.message?.contains("404") == true || (e is retrofit2.HttpException && e.code() == 404)
            var githubErrorDetails = ""
            if (e is retrofit2.HttpException) {
                try {
                    val rawBody = e.response()?.errorBody()?.string()
                    if (!rawBody.isNullOrBlank()) {
                        githubErrorDetails = " | GitHub Response: $rawBody"
                    }
                } catch (err: Exception) {
                    // ignore reading error body exceptions
                }
            }
            val detailMsg = if (is404) {
                "HTTP 404 Error$githubErrorDetails. This usually means either: 1. The specified target path is incorrect, 2. The branch name is incorrect, or 3. Your GitHub token (PAT/OAuth) lacks 'repo' write scopes/permissions. Please make sure you are using a standard 'OAuth App' (not a 'GitHub App'), that users authorize with 'repo' scope enabled, or that your PAT has full 'repo' write access."
            } else {
                "Error details: ${e.message ?: "Unknown Exception during update"}$githubErrorDetails"
            }
            addLog(
                "PORTFOLIO_UPDATE",
                "ERROR",
                "Showcase Syncing Aborted",
                detailMsg
            )
            false
        }
    }
}

typealias StringByCustomKey = String
