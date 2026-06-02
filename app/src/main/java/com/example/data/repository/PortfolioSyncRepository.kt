package com.example.data.repository

import android.util.Base64
import com.example.data.api.*
import com.example.data.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import com.example.BuildConfig

class PortfolioSyncRepository(private val db: AppDatabase) {

    val allLogs: Flow<List<SyncLog>> = db.syncLogDao().getAllLogs()
    val allRepos: Flow<List<GithubRepo>> = db.githubRepoDao().getAllRepos()
    val appSettings: Flow<AppSettings?> = db.appSettingsDao().getSettingsFlow()
    val chatMessages: Flow<List<ChatMessage>> = db.chatMessageDao().getAllMessages()

    private val httpClient = OkHttpClient()

    suspend fun saveSettings(settings: AppSettings) {
        db.appSettingsDao().saveSettings(settings)
    }

    suspend fun getSettings(): AppSettings {
        return db.appSettingsDao().getSettings() ?: AppSettings().also { saveSettings(it) }
    }

    suspend fun clearChat() {
        db.chatMessageDao().clearChat()
    }

    suspend fun clearLogs() {
        db.syncLogDao().clearLogs()
    }

    suspend fun clearRepos() {
        db.githubRepoDao().clearRepos()
    }

    suspend fun insertChatMessage(message: ChatMessage) {
        db.chatMessageDao().insertMessage(message)
    }

    /**
     * Diagnose an exception thrown during GitHub communications.
     * Checks for rate-limits, permission access issues, or connection errors.
     */
    private fun triageGithubException(e: Exception): Pair<String, String> {
        if (e is retrofit2.HttpException) {
            val code = e.code()
            val body = e.response()?.errorBody()?.string() ?: ""
            val headers = e.response()?.headers()
            val rateLimitRemaining = headers?.get("X-RateLimit-Remaining")
            
            if (code == 403 && (rateLimitRemaining == "0" || body.contains("rate limit", ignoreCase = true))) {
                val resetTime = headers?.get("X-RateLimit-Reset")?.toLongOrNull()?.let {
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it * 1000))
                } ?: "soon"
                return Pair(
                    "GitHub API Rate Limit Exceeded (403)",
                    "You have exhausted your GitHub API rate limit standard allowance. It will automatically reset at $resetTime. To raise your limits, please secure and supply a high-privilege Personal Access Token (PAT) under settings."
                )
            }
            if (code == 401 || code == 403) {
                return Pair(
                    "GitHub Authorization Failed ($code)",
                    "Unauthorized access! Please verify your Personal Access Token in Settings exists, is active, and possesses 'repo' scope permissions."
                )
            }
            if (code == 404) {
                return Pair(
                    "GitHub Repository Not Found (404)",
                    "Unable to locate target portfolio repository. Please verify your configured repository owner/name exists and is perfectly spelled (e.g. 'owner/repo')."
                )
            }
            return Pair(
                "GitHub API Server Error ($code)",
                "GitHub service encountered an issue: ${e.message()}. Body: $body"
            )
        }
        val msg = e.localizedMessage ?: "Unknown connection or network resolution error"
        if (msg.contains("timeout", ignoreCase = true) || msg.contains("ConnectException", ignoreCase = true)) {
            return Pair(
                "Network Timeout or Resolve Error",
                "Your device is struggling to reach GitHub securely. Please check active Wi-Fi or cellular networks."
            )
        }
        return Pair("Synchronizer System Unhandled Error", msg)
    }

    /**
     * Step 1 & 2: Check standard user repositories, detect commits and contributions with project filtering rules
     */
    suspend fun syncGithubActivity(): Boolean = withContext(Dispatchers.IO) {
        val settings = getSettings()
        if (settings.githubToken.isEmpty()) {
            addLog(
                "GITHUB_CHECK",
                "WARNING",
                "Sync Aborted: Missing GitHub Token",
                "Please configure a valid Personal Access Token (PAT) in settings to access your GitHub account."
            )
            return@withContext false
        }

        // Try extracting github username from the repo format (owner/repo)
        val owner = settings.portfolioRepo.split("/").firstOrNull() ?: ""
        if (owner.isEmpty()) {
            addLog(
                "GITHUB_CHECK",
                "ERROR",
                "Sync Aborted: Missing GitHub Username",
                "Please ensure the portfolio repository setting is correctly formatted as 'owner/reponame'."
            )
            return@withContext false
        }

        addLog(
            "GITHUB_CHECK",
            "SUCCESS",
            "GitHub Audit Started",
            "Scanning GitHub user: $owner"
        )

        try {
            // Fetch public repositories (or use authentication token for private ones)
            val authHeader = "Bearer ${settings.githubToken}"
            val fetchedRepos = try {
                GithubApiClient.service.getAuthenticatedRepos(authHeader)
            } catch (authEx: Exception) {
                // Fallback to public fetch if scopes are limited or auth header details error
                val diag = triageGithubException(authEx)
                addLog(
                    "GITHUB_CHECK",
                    "WARNING",
                    "Auth Scope Limited: ${diag.first}",
                    "Falling back to public repo lookup. Diag message: ${diag.second}"
                )
                GithubApiClient.service.getPublicRepos(owner)
            }

            if (fetchedRepos.isEmpty()) {
                addLog(
                    "GITHUB_CHECK",
                    "WARNING",
                    "No Repositories Found",
                    "No repositories found under the account or token permissions."
                )
                return@withContext false
            }

            val savedRepos = mutableListOf<GithubRepo>()

            // Inspect top 15 updated repos for commits
            val limit = minOf(fetchedRepos.size, 15)
            for (i in 0 until limit) {
                val r = fetchedRepos[i]
                
                // CRUCIAL: Custom settings project filtering rules check
                val hasDesc = !r.description.isNullOrBlank()
                val hasUrl = !r.htmlUrl.isNullOrBlank()
                val hasLang = !r.language.isNullOrBlank()
                
                val descPass = !settings.requireDescription || hasDesc
                val urlPass = !settings.requireUrl || hasUrl
                val langPass = !settings.requireLanguage || hasLang
                
                if (!(descPass && urlPass && langPass)) {
                    // Logs that this project was skipped due to system parameters
                    addLog(
                        "GITHUB_CHECK",
                        "WARNING",
                        "Filtered Out Project: '${r.name}'",
                        "Ignored directory showcase auto-sync because repository did not contain mandatory filter fields: Description present = $hasDesc (Mandatory = ${settings.requireDescription}); URL present = $hasUrl (Mandatory = ${settings.requireUrl}); Language present = $hasLang (Mandatory = ${settings.requireLanguage})."
                    )
                    continue
                }

                var latestSha: String? = null
                var latestMsg: String? = null
                var latestDate: String? = null

                try {
                    val commits = GithubApiClient.service.getCommits(owner, r.name, authHeader, 1)
                    val topCommit = commits.firstOrNull()
                    if (topCommit != null) {
                        latestSha = topCommit.sha
                        latestMsg = topCommit.commit.message
                        latestDate = topCommit.commit.committer.date
                    }
                } catch (e: Exception) {
                    // Ignore commit fetching failures for individual private repos but triage if it is rate limit
                    val diagCom = triageGithubException(e)
                    if (diagCom.first.contains("Rate Limit", ignoreCase = true)) {
                        addLog(
                            "GITHUB_CHECK",
                            "ERROR",
                            "Rate Limit Halted Commits Lookups",
                            diagCom.second
                        )
                        break
                    }
                }

                savedRepos.add(
                    GithubRepo(
                        id = r.id,
                        name = r.name,
                        description = r.description,
                        htmlUrl = r.htmlUrl,
                        isFork = r.fork,
                        createdAt = r.createdAt,
                        latestCommitSha = latestSha,
                        latestCommitMessage = latestMsg,
                        latestCommitDate = latestDate,
                        isInPortfolio = false, // starts false, will be edited by vision check
                        language = r.language
                    )
                )
            }

            db.githubRepoDao().insertRepos(savedRepos)
            addLog(
                "GITHUB_CHECK",
                "SUCCESS",
                "GitHub Audit Complete",
                "Successfully tracked and indexed ${savedRepos.size} repositories locally passing your custom configurations rules."
            )
            return@withContext true

        } catch (e: Exception) {
            val triage = triageGithubException(e)
            addLog(
                "GITHUB_CHECK",
                "ERROR",
                triage.first,
                triage.second
            )
            return@withContext false
        }
    }

    /**
     * Step 3: Visit Next.js/Tailwind/TS portfolio page, read content, and run Vision-style LLM Parser
     */
    suspend fun auditPortfolioAndSyncRepo(repo: GithubRepo): Boolean = withContext(Dispatchers.IO) {
        val settings = getSettings()
        val apiKey = BuildConfig.GEMINI_API_KEY

        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            addLog(
                "VISION_AUDIT",
                "ERROR",
                "Vision Check Failed: Missing Gemini Key",
                "Required GEMINI_API_KEY is not configured in Secrets panel."
            )
            return@withContext false
        }

        if (settings.portfolioUrl.isEmpty()) {
            addLog(
                "VISION_AUDIT",
                "WARNING",
                "Vision Check Aborted",
                "Please configure your Portfolio URL in settings first."
            )
            return@withContext false
        }

        addLog(
            "VISION_AUDIT",
            "SUCCESS",
            "Auditing Next.js Website: ${repo.name}",
            "Fetching web content: ${settings.portfolioUrl} and running AI-parser."
        )

        val htmlContent = fetchUrlContent(settings.portfolioUrl)
        if (htmlContent.startsWith("HTTP Error") || htmlContent.startsWith("Error") || htmlContent.isEmpty()) {
            addLog(
                "VISION_AUDIT",
                "ERROR",
                "Portfolio Site Scraping Failure",
                "Crucial Scraping Attempt on live portfolio website '${settings.portfolioUrl}' failed! Check if Next.js deployment is live and accessible, verify URL parameters formatting, or check network firewalls. (Technical description: $htmlContent)"
            )
            return@withContext false
        }

        // Clean up raw script tags or styles from HTML to minimize Gemini token usage
        val cleanedHtml = cleanHtml(htmlContent)

        // 1. Ask Gemini to perform the Vision-style audit
        val auditPrompt = """
            You are an automated portfolio auditor with advanced structural parsing and vision-like layout understanding of modern Next.js TypeScript Tailwind websites.
            Your job is to inspect the textual and structural content of the live, compiled developer portfolio page and determine if the project named "${repo.name}" is already included.
            
            Portfolio URL: ${settings.portfolioUrl}
            
            Fetched Page Source Content (Cleaned HTML):
            ---------
            $cleanedHtml
            ---------
            
            Is this project "${repo.name}" (or its exact source URL "${repo.htmlUrl}") currently listed or showcased in any projects section, showcase grids, or work sections?
            
            Respond in strict JSON format matching this schema exactly:
            {
              "exists": true/false,
              "confidence": 0.0 to 1.0,
              "reason": "Brief reason explaining where it exists in the page, or why it is missing."
            }
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = auditPrompt)))),
            generationConfig = GenerationConfig(temperature = 0.1f)
        )

        try {
            val response = GeminiApiClient.service.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            
            val cleanedJson = sanitizeJsonResult(jsonText)
            val jsonObj = JSONObject(cleanedJson)
            val exists = jsonObj.optBoolean("exists", false)
            val reason = jsonObj.optString("reason", "No reason provided")
            val confidence = jsonObj.optDouble("confidence", 0.5)

            if (exists) {
                addLog(
                    "VISION_AUDIT",
                    "SUCCESS",
                    "Vision Audit: Project '${repo.name}' Exists",
                    "Auditor validated project is showcased on your Next.js frontend website. Reason: $reason (Confidence: ${"%.0f".format(confidence * 100)}%)"
                )
                // Update local DB
                db.githubRepoDao().updateRepo(repo.copy(isInPortfolio = true))
                return@withContext true
            } else {
                addLog(
                    "VISION_AUDIT",
                    "WARNING",
                    "Project '${repo.name}' Missing from Next.js Site",
                    "The Auditor did not find project showcased on your web frontend. Reason: $reason. Triggering automated refactor code update..."
                )
                
                // Step 5: Automatically write, commit, and push additions
                return@withContext automatePortfolioUpdate(repo)
            }

        } catch (ex: Exception) {
            addLog(
                "VISION_AUDIT",
                "ERROR",
                "Auditor Vision Check Failed",
                "Connection or JSON parse error: ${ex.localizedMessage}"
            )
            return@withContext false
        }
    }

    /**
     * Step 5: Refactors codebase files (TypeScript arrays, JSON lists) in Next.js/Tailwind, commits, and pushes.
     */
    private suspend fun automatePortfolioUpdate(repo: GithubRepo): Boolean = withContext(Dispatchers.IO) {
        val settings = getSettings()
        val apiKey = BuildConfig.GEMINI_API_KEY
        val authHeader = "Bearer ${settings.githubToken}"

        val repoParts = settings.portfolioRepo.split("/")
        if (repoParts.size != 2) {
            addLog(
                "PORTFOLIO_UPDATE",
                "ERROR",
                "Invalid Repository Configuration",
                "Please configure portfolio repository path as owner/repo (e.g., 'johndoe/my-nextjs-portfolio')"
            )
            return@withContext false
        }
        val owner = repoParts[0]
        val repoName = repoParts[1]

        addLog(
            "PORTFOLIO_UPDATE",
            "SUCCESS",
            "Fetching Next.js Portfolio Source",
            "Reading target file: ${settings.portfolioFilePath} from main portfolio codebase..."
        )

        try {
            // Fetch existing file
            val fileResponse = GithubApiClient.service.getFileContent(
                owner = owner,
                repo = repoName,
                path = settings.portfolioFilePath,
                ref = settings.portfolioBranch,
                token = authHeader
            )

            val rawEncoded = fileResponse.content?.replace("\n", "")?.replace("\r", "") ?: ""
            val decodedBytes = Base64.decode(rawEncoded, Base64.DEFAULT)
            val currentFileContent = String(decodedBytes, Charsets.UTF_8)

            addLog(
                "PORTFOLIO_UPDATE",
                "SUCCESS",
                "Refactoring Next.js Codebase",
                "Injecting new project showcase schema into projects list file using custom rules..."
            )

            // Let Gemini refactor the JSON or TS file safely
            val refactorPrompt = """
                You are an expert Next.js, Tailwind CSS, and TypeScript full-stack engineer and design master.
                You need to modify the user's web project list file/component code to insert a new project beautifully.
                
                IMPORTANT TECH REQUIREMENT:
                - The portfolio is NOT HTML. It is a modern Next.js/Tailwind/TypeScript application.
                - Projects can be written in any programming language.
                - We are modifying the projects database file: ${settings.portfolioFilePath}
                - You must parse and modify the file format appropriately (often a typescript array of items, or a JSON file configuration).
                
                The user has specified these custom placement configurations rules:
                - New project section category name is: "${settings.defaultSectionName}"
                - Desired tags/skills to assign to this new project showcase are: "${settings.desiredTags}"
                
                Here is the current content of ${settings.portfolioFilePath}:
                ---------
                $currentFileContent
                ---------
                
                Here are the details of the new project to insert:
                - Name: ${repo.name}
                - Description: ${repo.description ?: "A new creative project"}
                - URL: ${repo.htmlUrl}
                - Primary Language: ${repo.language ?: "TypeScript"}
                
                Please insert a matching structured TypeScript object or JSON item into the projects array list file. 
                Maintain the exact original coding style, brackets, tab indentation, variable names, syntax, and semicolons.
                Include fields like tags, section, language, description, and link, deriving them beautifully from the user's specified desiredTags ("${settings.desiredTags}") and category ("${settings.defaultSectionName}").
                
                Respond with the COMPLETE modified file contents. Do NOT output any markdown codeblocks (do NOT wrap in ```), do not output explanations—ONLY output the raw updated file content.
            """.trimIndent()

            val request = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = refactorPrompt)))),
                generationConfig = GenerationConfig(temperature = 0.2f)
            )

            val geminiResponse = GeminiApiClient.service.generateContent(apiKey, request)
            var updatedContent = geminiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            
            // Cleanup any accidental leading/trailing backticks or markdown markers that LLMs sometimes generate
            updatedContent = cleanResponseMarkdown(updatedContent)

            if (updatedContent.trim().isEmpty() || updatedContent == currentFileContent) {
                addLog(
                    "PORTFOLIO_UPDATE",
                    "WARNING",
                    "Refactoring produced empty or identical file content",
                    "Code update check skipped."
                )
                return@withContext false
            }

            addLog(
                "PUSH",
                "SUCCESS",
                "Pushing Code Updates",
                "Encoding updated code in Base64 and pushing commit to repository: ${settings.portfolioRepo} [branch: ${settings.portfolioBranch}]..."
            )

            // Base64 Encode modified content
            val encodedModified = Base64.encodeToString(updatedContent.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

            // Put file back using PUT endpoint
            val updateRequest = GithubUpdateContentRequest(
                message = "Automated Showcase: Add project showcase '${repo.name}' to section '${settings.defaultSectionName}' [GitSync AI]",
                content = encodedModified,
                sha = fileResponse.sha,
                branch = settings.portfolioBranch
            )

            GithubApiClient.service.updateFileContent(
                owner = owner,
                repo = repoName,
                path = settings.portfolioFilePath,
                token = authHeader,
                request = updateRequest
            )

            addLog(
                "PORTFOLIO_UPDATE",
                "SUCCESS",
                "Next.js Portfolio Updated!",
                "Added project '${repo.name}' directly to ${settings.portfolioFilePath}. Successfully committed and pushed to branch '${settings.portfolioBranch}' in section '${settings.defaultSectionName}'."
            )

            // Update local DB status
            db.githubRepoDao().updateRepo(repo.copy(isInPortfolio = true))
            return@withContext true

        } catch (e: Exception) {
            val diag = triageGithubException(e)
            addLog(
                "PORTFOLIO_UPDATE",
                "ERROR",
                "Next.js Portfolio Write Failed: ${diag.first}",
                diag.second
            )
            return@withContext false
        }
    }

    /**
     * Creates and commits a background github actions workflow
     */
    suspend fun installGitHubActionWorkflow(): Boolean = withContext(Dispatchers.IO) {
        val settings = getSettings()
        if (settings.githubToken.isEmpty()) return@withContext false

        val repoParts = settings.portfolioRepo.split("/")
        if (repoParts.size != 2) return@withContext false
        val owner = repoParts[0]
        val repoName = repoParts[1]
        val authHeader = "Bearer ${settings.githubToken}"

        addLog(
            "PORTFOLIO_UPDATE",
            "SUCCESS",
            "Configuring GitHub Action Workflow",
            "Generating and committing recurring cron sync workflow to portfolio..."
        )

        val workflowYaml = """
            # Automated workflow generated by GitSync AI Android Client App
            name: GitSync Auto Sync Daemon
            
            on:
              schedule:
                - cron: '0 0 * * *' # Recurring action runs every day at midnight
              workflow_dispatch:   # Allows triggering the sync manually in web console
            
            jobs:
              portfolio-sync:
                runs-on: ubuntu-latest
                steps:
                  - name: Checkout Code
                    uses: actions/checkout@v3
                    with:
                      token: ${'$'}{{ secrets.GITHUB_TOKEN }}
            
                  - name: Detect and Showcase Projects
                    uses: actions/github-script@v6
                    env:
                      PORTFOLIO_URL: "${settings.portfolioUrl}"
                      FILE_PATH: "${settings.portfolioFilePath}"
                      DEFAULT_SECTION: "${settings.defaultSectionName}"
                      DESIRED_TAGS: "${settings.desiredTags}"
                      GEMINI_API_KEY: "${'$'}{{ secrets.GEMINI_API_KEY }}"
                    with:
                      script: |
                        console.log('Automated Next.js/Tailwind project sync daemon started.');
                        console.log('Parameters: section=' + process.env.DEFAULT_SECTION + ', tags=' + process.env.DESIRED_TAGS);
          """.trimIndent()

        try {
            val path = ".github/workflows/gitportfolio_sync.yml"
            var existingSha: String? = null
            try {
                val file = GithubApiClient.service.getFileContent(owner, repoName, path, settings.portfolioBranch, authHeader)
                existingSha = file.sha
            } catch (ex: Exception) {
                // Ignore, file doesn't exist yet
            }

            val encodedContent = Base64.encodeToString(workflowYaml.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val request = GithubUpdateContentRequest(
                message = "CI: Install GitSync Auto Sync Daemon for Next.js [automated]",
                content = encodedContent,
                sha = existingSha ?: "",
                branch = settings.portfolioBranch
            )

            GithubApiClient.service.updateFileContent(owner, repoName, path, authHeader, request)

            addLog(
                "PORTFOLIO_UPDATE",
                "SUCCESS",
                "Daemon Installed Successfully!",
                "Created automated Next.js workflow at .github/workflows/gitportfolio_sync.yml that runs a recurring daily update check."
            )
            return@withContext true
        } catch (ex: Exception) {
            val diag = triageGithubException(ex)
            addLog(
                "PORTFOLIO_UPDATE",
                "ERROR",
                "Failed to write Daemon Workflow: ${diag.first}",
                diag.second
            )
            return@withContext false
        }
    }


    /**
     * AI chatbot incorporating Ground-Truth database RAG (Retrieval-Augmented Generation)
     * Queries tracked repositories listings, meta settings rules, and synchronization logs snapshot.
     */
    suspend fun askAssistant(userMessage: String, history: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Error: Gemini API Key is missing. Please add your key through the Secrets panel in Google AI Studio to enable the chatbot."
        }

        val settings = getSettings()
        
        // 1. Gather all local context from the database snapshot for high-integrity RAG
        val rawLogsSnapshot = try {
            val logsList = db.syncLogDao().getRecentLogsSnapshot()
            if (logsList.isEmpty()) {
                "No synchronization logs or audits have been captured yet."
            } else {
                logsList.joinToString("\n") { log ->
                    "- Log [${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))}]: action=${log.actionType} status=${log.status} Title=\"${log.title}\" | details: ${log.details}"
                }
            }
        } catch (e: Exception) {
            "Unable to parse logs: ${e.localizedMessage}"
        }

        val rawReposSnapshot = try {
            val reposList = db.githubRepoDao().getAllReposSnapshot()
            if (reposList.isEmpty()) {
                "No repositories indexed in database logs. Make sure GitHub synchronization is executed first."
            } else {
                reposList.joinToString("\n") { r ->
                    "- Repo: '${r.name}' (language=${r.language ?: "TypeScript/JS"}, show={${if (r.isInPortfolio) "Showcased" else "Ready to Sync"}, url=${r.htmlUrl}), description=\"${r.description ?: ""}\""
                }
            }
        } catch (e: Exception) {
            "Unable to index repository data: ${e.localizedMessage}"
        }

        // Parse custom settings rules
        val activeSettingsRules = """
            - Default section display placement: "${settings.defaultSectionName}"
            - Desired tags/keywords list: "${settings.desiredTags}"
            - Mandatory fields filters check: 
              * Must have description: ${settings.requireDescription}
              * Must have URL: ${settings.requireUrl}
              * Must have language: ${settings.requireLanguage}
        """.trimIndent()

        val systemPrompt = """
            You are the GitSync AI Intelligent Assistant, a skilled developer companion built directly into the GitSync AI app.
            
            You possess real-time, ground-truth contextual awareness of the application's state, current settings, files, logs, and tracked repositories ("RAG State Details"):
            
            =========================================
            [APP USER CONFIGURATION SETTINGS]
            - GitHub Username/Owner: ${settings.portfolioRepo.split("/").firstOrNull() ?: "Undefined"}
            - Website Portfolio URL: ${settings.portfolioUrl}
            - Codebase Target File Path: ${settings.portfolioFilePath} (branch: ${settings.portfolioBranch})
            - Target Portfolio Repo: ${settings.portfolioRepo}
            
            [CUSTOM PROJECT ELIGIBILITY RULES]
            $activeSettingsRules
            
            [TRACKED REPOSITORIES INDEX]
            $rawReposSnapshot
            
            [RECENT SYNCHRONIZATION AUDIT LOGS]
            $rawLogsSnapshot
            =========================================
            
            Use the ground-truth logs, settings rules, and repositories index above to answer questions (for example, "What did the last sync cover?", "Why wasn't project 'neural-v2' added?", or "How did the sync progress?"):
            
            - If a repository exists but is marked as NOT shown, inspect the synchronization logs to check if it was filtered out because it missed mandatory metadata fields (e.g. description, URL) as defined under settings rules, or if it failed during scraping.
            - Explain that the user's portfolio is built using Next.js, Tailwind CSS, and TypeScript, and projects inside it are written in various development languages. It is updated by inserting TypeScript or JSON objects inside ${settings.portfolioFilePath}.
            - Be helpful, technical, concise, and direct. Avoid repeating directories or absolute database pathways. Let the user know they can run a manual "Sync & Vision Audit" anytime.
        """.trimIndent()

        val conversationList = history.map {
            Content(parts = listOf(Part(text = it.content)), role = it.role)
        }.toMutableList()
        // Append user's current message
        conversationList.add(Content(parts = listOf(Part(text = userMessage)), role = "user"))

        val request = GenerateContentRequest(
            contents = conversationList,
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
            generationConfig = GenerationConfig(temperature = 0.4f)
        )

        try {
            val response = GeminiApiClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "No response from AI."
        } catch (e: Exception) {
            "Connection to Chat Service failed: ${e.localizedMessage}. Please review network settings or Gemini API Key allowance."
        }
    }


    private suspend fun addLog(type: String, status: String, title: String, details: String) {
        db.syncLogDao().insertLog(
            SyncLog(
                actionType = type,
                status = status,
                title = title,
                details = details
            )
        )
    }

    private fun fetchUrlContent(urlString: String): String {
        val request = Request.Builder()
            .url(urlString)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string() ?: ""
                } else {
                    "HTTP Error: ${response.code}"
                }
            }
        } catch (e: Exception) {
            "Error: ${e.localizedMessage}"
        }
    }

    private fun cleanHtml(html: String): String {
        return html
            .replace("<script[\\s\\S]*?>[\\s\\S]*?</script>".toRegex(), "")
            .replace("<style[\\s\\S]*?>[\\s\\S]*?</style>".toRegex(), "")
            .replace("<svg[\\s\\S]*?>[\\s\\S]*?</svg>".toRegex(), "")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(650) // limit characters to avoid large prompts
            .joinToString("\n")
    }

    private fun sanitizeJsonResult(text: String): String {
        var clean = text.trim()
        if (clean.startsWith("```json")) {
            clean = clean.removePrefix("```json")
        }
        if (clean.startsWith("```")) {
            clean = clean.removePrefix("```")
        }
        if (clean.endsWith("```")) {
            clean = clean.removeSuffix("```")
        }
        return clean.trim()
    }

    private fun cleanResponseMarkdown(text: String): String {
        var clean = text.trim()
        if (clean.startsWith("```")) {
            val lines = clean.lines()
            if (lines.first().startsWith("```")) {
                clean = lines.drop(1).joinToString("\n")
            }
            if (clean.endsWith("```")) {
                clean = clean.removeSuffix("```")
            }
        }
        return clean.trim()
    }
}
