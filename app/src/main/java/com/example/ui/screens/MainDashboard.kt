package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.AppSettings
import com.example.data.database.GithubRepo
import com.example.data.database.SyncLog
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Chat
import com.example.ui.theme.*
import com.example.ui.viewmodel.GitPortfolioViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboardScreen(viewModel: GitPortfolioViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val repos by viewModel.repos.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val syncingRepoId by viewModel.syncingRepoId.collectAsStateWithLifecycle()
    val socketConnected by viewModel.webSocketConnected.collectAsStateWithLifecycle()
    val socketMessage by viewModel.webSocketMessage.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(0) }
    val context = LocalContext.current

    LaunchedEffect(socketMessage) {
        if (socketMessage.isNotBlank()) {
            Toast.makeText(context, "Live Update: $socketMessage", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding() // Keep clear of notch and system status elements
                    .fillMaxWidth()
            ) {
                // Top Header Action Row with Logo
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GitSyncLogo(logoSize = 38.dp)
                        Column {
                            Text(
                                text = "GitSync AI",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Portfolio Showcase Automation",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }

                    // WebSocket Connection Badge
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(
                                if (socketConnected) Color(0xFF1B3B2B) else Color(0xFF381E1E)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (socketConnected) Color(0xFF00E676) else Color(0xFFFF5252))
                        )
                        Text(
                            text = if (socketConnected) "Live Link Connected" else "Live Link Offline",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (socketConnected) Color(0xFF81C784) else Color(0xFFE57373)
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Screen contents
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 84.dp) // Generous space to keep lists fully clear of floating dock
            ) {
                when (activeTab) {
                    0 -> ReposScreen(
                        repos = repos,
                        logs = logs,
                        isRefreshing = isRefreshing,
                        syncingRepoId = syncingRepoId,
                        onRefresh = { viewModel.refreshRepos() },
                        onSync = { viewModel.syncRepo(it) },
                        hasCredentials = settings != null && (settings!!.githubToken.isNotBlank() || settings!!.githubOAuthToken.isNotBlank())
                    )
                    1 -> LogsScreen(
                        logs = logs,
                        onClear = { viewModel.clearLogs() }
                    )
                    2 -> ChatbotScreen(viewModel = viewModel)
                    3 -> SettingsScreen(
                        settings = settings ?: AppSettings(),
                        onSave = { token, url, repo, branch, path, sect, tags, desc, hasUrl, lang, useCustom, provider, key, model, autoFile, autoSect ->
                            viewModel.saveSettings(token, url, repo, branch, path, sect, tags, desc, hasUrl, lang, useCustom, provider, key, model, autoFile, autoSect)
                        },
                        viewModel = viewModel
                    )
                }
            }

            // Glassmorphic Floating bottom Dock Navigation
            GlassmorphicDock(
                activeTab = activeTab,
                onTabSelected = { activeTab = it },
                logsCount = logs.size,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding() // Edge-to-edge system nav pill cushion safety
                    .padding(bottom = 4.dp)
            )
        }
    }
}

@Composable
fun ReposScreen(
    repos: List<GithubRepo>,
    logs: List<SyncLog>,
    isRefreshing: Boolean,
    syncingRepoId: Long?,
    onRefresh: () -> Unit,
    onSync: (GithubRepo) -> Unit,
    hasCredentials: Boolean
) {
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current

    val previewRepos = remember {
        listOf(
            GithubRepo(
                id = -11,
                name = "nextjs-tailwind-portfolio",
                fullName = "demo-profile/nextjs-tailwind-portfolio",
                description = "✨ Demo Preview Content: A sleek developer website made with Next.js & Tailwind CSS. Ready to be updated dynamically with project showcases.",
                htmlUrl = "https://github.com/demo-profile/nextjs-tailwind-portfolio",
                language = "TypeScript",
                isSynced = true,
                category = "Web Apps",
                tags = "Next.js, Tailwind, TypeScript"
            ),
            GithubRepo(
                id = -22,
                name = "android-copilot-app",
                fullName = "demo-profile/android-copilot-app",
                description = "✨ Demo Preview Content: Modern Jetpack Compose Android app utilizing local database caching, background synchronization, and AI engines.",
                htmlUrl = "https://github.com/demo-profile/android-copilot-app",
                language = "Kotlin",
                isSynced = false,
                category = "",
                tags = ""
            )
        )
    }

    val displayRepos = if (hasCredentials) repos else previewRepos
    val filteredRepos = remember(displayRepos, searchQuery) {
        displayRepos.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    (it.language ?: "").contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Warning Banner for Demo Mode / Missing Credentials
        if (!hasCredentials) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)),
                border = BorderStroke(2.dp, SecondaryCyan)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("🔑", fontSize = 20.sp)
                    Column {
                        Text(
                            text = "Connected in Preview Mode",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Connect your GitHub account in the Settings tab to scan and showcase your live projects!",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Main Repos view Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search repo by name...") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp), // Fully rounded pill shape
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                singleLine = true
            )

            Button(
                onClick = {
                    if (hasCredentials) {
                        onRefresh()
                    } else {
                        Toast.makeText(context, "Connect GitHub in settings to fetch user repositories!", Toast.LENGTH_LONG).show()
                    }
                },
                enabled = !isRefreshing,
                shape = RoundedCornerShape(24.dp), // Fully rounded pill shape
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                } else {
                    Text("Fetch Repos", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (displayRepos.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ContributionDashboardComponent(
                    logs = logs,
                    modifier = Modifier.fillMaxWidth()
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No Repositories Loaded", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "Tap 'Fetch Repos' to pull repos directly from your profile.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    // Interactive Git Contribution Heatmap & Metric Trend Chart
                    ContributionDashboardComponent(
                        logs = if (hasCredentials) logs else emptyList(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    )
                }

                items(filteredRepos, key = { it.id }) { repo ->
                    RepoCard(
                        repo = repo,
                        isSyncing = syncingRepoId == repo.id,
                        onSync = {
                            if (hasCredentials) {
                                onSync(repo)
                            } else {
                                Toast.makeText(context, "Authorization required. Please connect GitHub in Settings!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RepoCard(
    repo: GithubRepo,
    isSyncing: Boolean,
    onSync: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = repo.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = repo.fullName,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Rounded Pill Status chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            if (repo.isSynced) Color(0xFFDCFCE7) else Color(0xFFF1F5F9)
                        )
                        .border(
                            BorderStroke(1.dp, if (repo.isSynced) Color(0xFF86EFAC) else Color.Transparent),
                            RoundedCornerShape(24.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (repo.isSynced) "Showcased" else "Not Syncing",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (repo.isSynced) Color(0xFF166534) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!repo.description.isNullOrBlank()) {
                Text(
                    text = repo.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Language chip
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when (repo.language?.lowercase()) {
                                    "kotlin", "java" -> Color(0xFF7C4DFF)
                                    "typescript", "javascript" -> Color(0xFF00E5FF)
                                    "python" -> Color(0xFFFFD600)
                                    "html", "css" -> Color(0xFFFF6D00)
                                    else -> Color.Gray
                                }
                            )
                    )
                    Text(
                        text = repo.language ?: "Other",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onSync,
                    enabled = !isSyncing,
                    shape = RoundedCornerShape(24.dp), // Pill Shape button!
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (repo.isSynced) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary,
                        contentColor = if (repo.isSynced) MaterialTheme.colorScheme.primary else Color.Black
                    ),
                    modifier = Modifier.border(
                        BorderStroke(1.dp, if (repo.isSynced) PrimaryNeon.copy(alpha=0.4f) else Color.Transparent),
                        RoundedCornerShape(24.dp)
                    )
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), color = Color.White)
                    } else {
                        Text(
                            text = if (repo.isSynced) "Force Resync" else "Showcase & Sync",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (repo.isSynced && !repo.category.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(16.dp), // Pill sub-card
                    border = BorderStroke(1.dp, SecondaryCyan.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "AI Metadata Placement Info:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(text = "Section: ${repo.category}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text(text = "Tags: ${repo.tags}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogsScreen(
    logs: List<SyncLog>,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Showcase Activity Logging",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White
            )

            if (logs.isNotEmpty()) {
                TextButton(
                    onClick = onClear,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Clear Activity", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No system logs generated yet.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(logs) { log ->
                    LogCard(log)
                }
            }
        }
    }
}

@Composable
fun LogCard(log: SyncLog) {
    val chipColor = when (log.status) {
        "SUCCESS" -> Color(0xFFDCFCE7)
        "WARNING" -> Color(0xFFFEF9C3)
        else -> Color(0xFFFEE2E2)
    }
    val textColor = when (log.status) {
        "SUCCESS" -> Color(0xFF15803D)
        "WARNING" -> Color(0xFFA16207)
        else -> Color(0xFFB91C1C)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp), // Match consistent pill-rounded layout
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), // Elegant white container
        border = BorderStroke(
            1.dp, 
            if (log.status == "SUCCESS") Color(0xFF86EFAC) 
            else if (log.status == "WARNING") Color(0xFFFDE047)
            else Color(0xFFFCA5A5)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp)) // Pill shape chip
                            .background(chipColor)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(text = log.status, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = textColor)
                    }
                    Text(
                        text = log.actionType,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                val formattedTime = remember(log.timestamp) {
                    val date = java.util.Date(log.timestamp)
                    val format = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                    format.format(date)
                }
                Text(text = formattedTime, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text(text = log.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(text = log.details, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSave: (String, String, String, String, String, String, String, Boolean, Boolean, Boolean, Boolean, String, String, String, Boolean, Boolean) -> Unit,
    viewModel: GitPortfolioViewModel
) {
    var editableToken by remember { mutableStateOf("") }
    var editableUrl by remember { mutableStateOf("") }
    var editableRepo by remember { mutableStateOf("") }
    var editableBranch by remember { mutableStateOf("") }
    var editableFilePath by remember { mutableStateOf("") }
    var defaultSectionName by remember { mutableStateOf("") }
    var desiredTags by remember { mutableStateOf("") }
    var requireDescription by remember { mutableStateOf(false) }
    var requireUrl by remember { mutableStateOf(false) }
    var requireLanguage by remember { mutableStateOf(false) }
    var tokenVisible by remember { mutableStateOf(false) }

    // Custom AI provider custom settings
    var useCustomAiSettings by remember { mutableStateOf(false) }
    var aiProvider by remember { mutableStateOf("Gemini") }
    var customApiKey by remember { mutableStateOf("") }
    var customModelName by remember { mutableStateOf("gemini-1.5-flash") }
    var autoDetectConfig by remember { mutableStateOf(true) }
    var autoDetectSectionAndStack by remember { mutableStateOf(true) }
    var customKeyVisible by remember { mutableStateOf(false) }

    val fetchedModels by viewModel.fetchedModels.collectAsStateWithLifecycle()
    val validationStatus by viewModel.validationStatus.collectAsStateWithLifecycle()
    val validationError by viewModel.validationError.collectAsStateWithLifecycle()
    var dropdownExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current

    LaunchedEffect(settings) {
        editableToken = settings.githubToken
        editableUrl = settings.portfolioUrl
        editableRepo = settings.portfolioRepo
        editableBranch = settings.portfolioBranch
        editableFilePath = settings.portfolioFilePath
        defaultSectionName = settings.defaultSectionName
        desiredTags = settings.desiredTags
        requireDescription = settings.requireDescription
        requireUrl = settings.requireUrl
        requireLanguage = settings.requireLanguage
        useCustomAiSettings = settings.useCustomAiSettings
        aiProvider = settings.aiProvider
        customApiKey = settings.customApiKey
        customModelName = settings.customModelName
        autoDetectConfig = settings.autoDetectConfig
        autoDetectSectionAndStack = settings.autoDetectSectionAndStack
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Portfolio Settings",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Configure how you want the AI assistant to automatically update your website or portfolio.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Section: Secure GitHub Authentication
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Secure GitHub Connection",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )

                    Text(
                        text = "Choose your connection method. You can launch direct GitHub OAuth verification or paste a Personal Access Token.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Toggle Button selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                            .padding(4.dp)
                    ) {
                        val useOAuth = settings.useOAuth
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (useOAuth) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { viewModel.setUseOAuth(true) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "GitHub OAuth Login",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (useOAuth) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (!useOAuth) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { viewModel.setUseOAuth(false) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Personal Access Token",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!useOAuth) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (settings.useOAuth) {
                        // OAuth layout
                        if (settings.githubOAuthToken.isNotBlank()) {
                            Surface(
                                color = Color(0xFF1B3B2B),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Connected Account Status", fontSize = 11.sp, color = Color(0xFF81C784), fontWeight = FontWeight.Bold)
                                        Text("Direct GitHub Link Active", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = { viewModel.disconnectOAuth() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("Disconnect", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            // Direct Button to request integration
                            Button(
                                onClick = {
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://github.com/login/oauth/authorize" +
                                                "?client_id=${viewModel.oauthClientId}" +
                                                "&scope=repo,user" +
                                                "&redirect_uri=gitsync://oauth")
                                    )
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF24292E)) // Github branding color
                            ) {
                                Text("Connect & Authorize with GitHub", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        // Advanced OAuth settings
                        var showAdvancedAuth by remember { mutableStateOf(true) }
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "Why am I seeing a 404 error on GitHub?",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "GitHub shows a 404 page if you attempt connection using an invalid or default Client ID. To fix this, register an OAuth Application under your GitHub Settings > Developer Settings, set the Authorization Callback URL to \"gitsync://oauth\", and paste your credentials below.",
                                        fontSize = 10.sp,
                                        lineHeight = 13.sp,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }

                            Text(
                                text = "Custom OAuth Application Specs",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable { showAdvancedAuth = !showAdvancedAuth }
                                    .padding(vertical = 4.dp)
                            )
                            if (showAdvancedAuth) {
                                var clientIp by remember { mutableStateOf(viewModel.oauthClientId) }
                                var secretIp by remember { mutableStateOf(viewModel.oauthClientSecret) }
                                
                                // Sync initial values from viewModel
                                LaunchedEffect(viewModel.oauthClientId, viewModel.oauthClientSecret) {
                                    clientIp = viewModel.oauthClientId
                                    secretIp = viewModel.oauthClientSecret
                                }

                                OutlinedTextField(
                                    value = clientIp,
                                    onValueChange = {
                                        clientIp = it
                                        viewModel.oauthClientId = it
                                    },
                                    label = { Text("GitHub OAuth Client ID") },
                                    placeholder = { Text("Paste custom Client ID") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = secretIp,
                                    onValueChange = {
                                        secretIp = it
                                        viewModel.oauthClientSecret = it
                                    },
                                    label = { Text("GitHub OAuth Client Secret") },
                                    placeholder = { Text("Paste custom Client Secret") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }
                        }

                    } else {
                        // Personal Token textfield input
                        OutlinedTextField(
                            value = editableToken,
                            onValueChange = { editableToken = it },
                            label = { Text("GitHub Personal Access Token") },
                            placeholder = { Text("ghp_...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { tokenVisible = !tokenVisible }) {
                                    val textSymbol = if (tokenVisible) "Hide" else "Show"
                                    Text(textSymbol, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )
                    }
                }
            }
        }

        // Section: Target Portfolio Codebase setup
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Portfolio Target Codebase Specs",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )

                    OutlinedTextField(
                        value = editableRepo,
                        onValueChange = { editableRepo = it },
                        label = { Text("Portfolio Repository (owner/repo)") },
                        placeholder = { Text("octocat/my-portfolio") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = editableBranch,
                            onValueChange = { editableBranch = it },
                            label = { Text("Branch") },
                            placeholder = { Text("main") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = editableFilePath,
                            onValueChange = { editableFilePath = it },
                            label = { Text("Config Filepath") },
                            placeholder = { Text("src/data/projects.json") },
                            enabled = !autoDetectConfig,
                            modifier = Modifier.weight(2.3f),
                            singleLine = true
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)

                    // Auto Detect File Path Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Find project file automatically", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(text = "We will search and find the projects file in your portfolio repo dynamically.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = autoDetectConfig,
                            onCheckedChange = { autoDetectConfig = it }
                        )
                    }

                    // Auto Detect Section & Stack Tags Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Detect section and tech tags automatically", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(text = "The AI will automatically look at your website structure and choose the best placement and stack tags.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = autoDetectSectionAndStack,
                            onCheckedChange = { autoDetectSectionAndStack = it }
                        )
                    }
                }
            }
        }

        // Custom AI Bring Your Own Key Config
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bring Your Own AI Key",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Turn this on if you want to use your own custom API key and custom models.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = useCustomAiSettings,
                            onCheckedChange = { useCustomAiSettings = it }
                        )
                    }

                    if (useCustomAiSettings) {
                        Divider(color = MaterialTheme.colorScheme.outlineVariant)

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Pick your AI model creator:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("Gemini", "OpenAI Compatible").forEach { prov ->
                                    val isSelected = aiProvider == prov
                                    OutlinedButton(
                                        onClick = { aiProvider = prov },
                                        shape = RoundedCornerShape(100.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        ),
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(prov, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = customApiKey,
                            onValueChange = { customApiKey = it },
                            label = { Text("Your custom API Key") },
                            placeholder = { Text(if (aiProvider == "Gemini") "AIzaSy..." else "sk-...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (customKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { customKeyVisible = !customKeyVisible }) {
                                    val textSymbol = if (customKeyVisible) "Hide" else "Show"
                                    Text(textSymbol, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )

                        // Validate API Key button
                        val isKeyBlank = customApiKey.isBlank()
                        Button(
                            onClick = { viewModel.validateAndFetchModelList(aiProvider, customApiKey) },
                            enabled = !isKeyBlank && validationStatus != "loading",
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("validate_key_button")
                        ) {
                            if (validationStatus == "loading") {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Validating & Fetching Models...", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Validate key",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Validate API Key & Import Models", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Validation message boxes
                        if (validationStatus == "success") {
                            Surface(
                                color = Color(0xFF14532D).copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, Color(0xFF22C55E).copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Success",
                                        tint = Color(0xFF22C55E),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "API Key verified! Loaded ${fetchedModels.size} models.",
                                        color = Color(0xFF4ADE80),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        } else if (validationStatus == "error") {
                            Surface(
                                color = Color(0xFF7F1D1D).copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, Color(0xFFF87171).copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Error",
                                        tint = Color(0xFFF87171),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = validationError.ifEmpty { "Validation failed. Verify key or connectivity." },
                                        color = Color(0xFFFCA5A5),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        // Selected AI Model section
                        if (fetchedModels.isNotEmpty()) {
                            Text(
                                text = "Select from detected models list:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                            )

                            Box(modifier = Modifier.fillMaxWidth().testTag("models_dropdown_box")) {
                                OutlinedButton(
                                    onClick = { dropdownExpanded = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = customModelName.ifEmpty { "Select Model" },
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Toggle dropdown selector",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = dropdownExpanded,
                                    onDismissRequest = { dropdownExpanded = false },
                                    modifier = Modifier
                                        .fillMaxWidth(0.9f)
                                        .background(Color(0xFF131926))
                                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)), RoundedCornerShape(8.dp))
                                ) {
                                    fetchedModels.forEach { model ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = model,
                                                    color = if (customModelName == model) MaterialTheme.colorScheme.primary else Color.White,
                                                    fontWeight = if (customModelName == model) FontWeight.Bold else FontWeight.Normal,
                                                    fontSize = 12.sp
                                                )
                                            },
                                            onClick = {
                                                customModelName = model
                                                dropdownExpanded = false
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = customModelName,
                            onValueChange = { customModelName = it },
                            label = { Text(if (fetchedModels.isNotEmpty()) "Selected Model (Manual Override)" else "Model name or size") },
                            placeholder = { Text(if (aiProvider == "Gemini") "gemini-1.5-flash" else "gpt-4o") },
                            modifier = Modifier.fillMaxWidth().testTag("custom_model_textfield"),
                            singleLine = true
                        )
                        
                        Text(
                            text = "Auto-suggested Models:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )

                        val suggestedModels = if (aiProvider == "Gemini") {
                            listOf("gemini-1.5-flash", "gemini-1.5-pro", "gemini-2.5-flash", "gemini-2.0-flash-exp")
                        } else {
                            listOf("gpt-4o", "gpt-4o-mini", "deepseek-chat", "deepseek-coder")
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 4.dp)
                        ) {
                            suggestedModels.forEach { modelName ->
                                val isSelected = customModelName == modelName
                                Surface(
                                    onClick = { customModelName = modelName },
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    Text(
                                        text = modelName,
                                        fontSize = 11.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Custom Showcasing Filters & Auto-Add Parameters Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Visual Section & Default Custom Filters",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )

                    OutlinedTextField(
                        value = defaultSectionName,
                        onValueChange = { defaultSectionName = it },
                        label = { Text("Default Portfolio Section Name") },
                        placeholder = { Text("Projects") },
                        enabled = !autoDetectSectionAndStack,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = desiredTags,
                        onValueChange = { desiredTags = it },
                        label = { Text("Technology tags to assign (comma separated)") },
                        placeholder = { Text("Next.js, Tailwind, TypeScript") },
                        enabled = !autoDetectSectionAndStack,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Project Sync Filters",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "Only sync projects that have these checked items on GitHub:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Switches
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Require project description", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(text = "The repo must have a written description on GitHub", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = requireDescription,
                            onCheckedChange = { requireDescription = it }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Require a live website link", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(text = "The repo must have a website URL specified in its settings", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = requireUrl,
                            onCheckedChange = { requireUrl = it }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Require coding language tag", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(text = "The repository must declare a primary language (like TypeScript or Kotlin)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = requireLanguage,
                            onCheckedChange = { requireLanguage = it }
                        )
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    onSave(
                        editableToken,
                        editableUrl,
                        editableRepo,
                        editableBranch,
                        editableFilePath,
                        defaultSectionName,
                        desiredTags,
                        requireDescription,
                        requireUrl,
                        requireLanguage,
                        useCustomAiSettings,
                        aiProvider,
                        customApiKey,
                        customModelName,
                        autoDetectConfig,
                        autoDetectSectionAndStack
                    )
                    Toast.makeText(context, "Configurations and credentials saved!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = "Save Settings Configuration", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ChatbotScreen(viewModel: GitPortfolioViewModel) {
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isSending by viewModel.isSendingChat.collectAsStateWithLifecycle()

    var chatText by remember { mutableStateOf("") }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Scroll to bottom when message list expands
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Chat Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Agentic AI Chatbot",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Your developer copilot. Ask to sync portfolios, customize settings, or clear logs.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = { viewModel.clearChat() }
            ) {
                Text("Clear", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            }
        }

        // Messages List
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp)
        ) {
            if (messages.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "💬 GitSync AI Chatbot",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "I can guide you on setting up GitHub Personal Tokens, registering OAuth apps, verifying branches, and editing dynamic Next.js data templates.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { message ->
                        val isUser = message.role == "user"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 14.dp,
                                            topEnd = 14.dp,
                                            bottomStart = if (isUser) 14.dp else 2.dp,
                                            bottomEnd = if (isUser) 2.dp else 14.dp
                                        )
                                    )
                                    .background(
                                        if (isUser) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    )
                                    .border(
                                        BorderStroke(1.dp, if (isUser) SecondaryCyan else PrimaryNeon.copy(alpha = 0.5f)),
                                        RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp,
                                            bottomStart = if (isUser) 16.dp else 2.dp,
                                            bottomEnd = if (isUser) 2.dp else 16.dp
                                        )
                                    )
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = if (isUser) message.content else cleanMarkdownFormatting(message.content),
                                    color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                    if (isSending) {
                        item {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "AI is typing...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Input Box
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = chatText,
                onValueChange = { chatText = it },
                placeholder = { Text("Ask me anything...") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )

            Button(
                onClick = {
                    viewModel.sendChatMessage(chatText)
                    chatText = ""
                },
                enabled = chatText.isNotBlank() && !isSending,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Send", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

fun cleanMarkdownFormatting(text: String): String {
    return text
        .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        .replace(Regex("\\*([^*]+)\\*"), "$1")
        .replace(Regex("(?m)^#+ (.*)$"), "$1")
        .replace(Regex("^[\\s]*\\*[\\s]+"), "• ")
        .replace(Regex("^#+\\s*"), "")
}

@Composable
fun GitSyncLogo(
    modifier: Modifier = Modifier,
    logoSize: Dp = 38.dp,
    glowing: Boolean = true
) {
    Box(
        modifier = modifier
            .size(logoSize)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E293B),
                        Color(0xFF0F172A)
                    )
                ),
                shape = CircleShape
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    colors = listOf(
                        PrimaryNeon,
                        SecondaryCyan
                    )
                ),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val w = size.width
            val h = size.height

            // Dual curved paths representing Git branch/synchronizer loop
            val strokeWidth = 2.dp.toPx()

            val path1 = Path().apply {
                moveTo(w * 0.3f, h * 0.7f)
                cubicTo(
                    w * 0.3f, h * 0.4f,
                    w * 0.7f, h * 0.3f,
                    w * 0.7f, h * 0.5f
                )
            }
            drawPath(
                path = path1,
                color = SecondaryCyan,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            val path2 = Path().apply {
                moveTo(w * 0.7f, h * 0.3f)
                cubicTo(
                    w * 0.7f, h * 0.6f,
                    w * 0.3f, h * 0.7f,
                    w * 0.3f, h * 0.5f
                )
            }
            drawPath(
                path = path2,
                color = PrimaryNeon,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Central core node
            drawCircle(
                color = Color.White,
                radius = 3.dp.toPx(),
                center = Offset(w * 0.5f, h * 0.5f)
            )

            // Side terminals
            drawCircle(
                color = SecondaryCyan,
                radius = 2.5.dp.toPx(),
                center = Offset(w * 0.3f, h * 0.7f)
            )
            drawCircle(
                color = PrimaryNeon,
                radius = 2.5.dp.toPx(),
                center = Offset(w * 0.7f, h * 0.3f)
            )
        }
    }
}

@Composable
fun GlassmorphicDock(
    activeTab: Int,
    onTabSelected: (Int) -> Unit,
    logsCount: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp),
        color = Color.Transparent,
        shape = CircleShape // Pill shape
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xF2FFFFFF), // Ultra frosty white
                            Color(0xE6F1F5F9)  // Soft light-slate gradient base
                        )
                    ),
                    shape = CircleShape // Pill shape
                )
                .border(
                    BorderStroke(
                        1.5.dp, // Premium soft light highlight border
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.82f),
                                Color(0x1F000000) // Sleek high-end drop shadow edge
                            )
                        )
                    ),
                    shape = CircleShape // Pill shape
                )
                .padding(horizontal = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tabs = listOf(
                    DockTabItem("Home", Icons.Outlined.Folder, Icons.Filled.Folder, 0),
                    DockTabItem("Logs", Icons.Outlined.History, Icons.Filled.History, 1, badgeCount = logsCount),
                    DockTabItem("AI Chat", Icons.Outlined.Chat, Icons.Filled.Chat, 2),
                    DockTabItem("Settings", Icons.Outlined.Settings, Icons.Filled.Settings, 3)
                )

                tabs.forEach { tab ->
                    val isSelected = activeTab == tab.index
                    val animScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.15f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "tabScaleAnimation"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onTabSelected(tab.index) }
                            .graphicsLayer {
                                scaleX = animScale
                                scaleY = animScale
                            }
                            .testTag("dock_tab_${tab.index}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(contentAlignment = Alignment.TopEnd) {
                                Icon(
                                    imageVector = if (isSelected) tab.activeIcon else tab.inactiveIcon,
                                    contentDescription = tab.label,
                                    tint = if (isSelected) PrimaryNeon else TextSecondary.copy(alpha = 0.8f),
                                    modifier = Modifier.size(24.dp)
                                )

                                if (tab.badgeCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .offset(x = 10.dp, y = (-6).dp)
                                            .background(PrimaryNeon, CircleShape)
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = tab.badgeCount.toString(),
                                            color = DarkBg,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(3.dp))

                            Text(
                                text = tab.label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) PrimaryNeon else TextSecondary.copy(alpha = 0.7f)
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            AnimatedVisibility(
                                visible = isSelected,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(12.dp)
                                        .height(3.dp)
                                        .background(PrimaryNeon, RoundedCornerShape(1.5.dp))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

data class DockTabItem(
    val label: String,
    val inactiveIcon: ImageVector,
    val activeIcon: ImageVector,
    val index: Int,
    val badgeCount: Int = 0
)

