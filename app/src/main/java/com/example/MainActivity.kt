package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.room.Room
import com.example.data.database.AppDatabase
import com.example.data.repository.PortfolioSyncRepository
import com.example.ui.screens.MainDashboardScreen
import com.example.ui.theme.GitSyncAITheme
import com.example.ui.viewmodel.GitPortfolioViewModel

class MainActivity : ComponentActivity() {

    private lateinit var database: AppDatabase
    private lateinit var repository: PortfolioSyncRepository
    private lateinit var viewModel: GitPortfolioViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()

        // Setup Room DB
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "gitsync_ai.db"
        )
        .fallbackToDestructiveMigration()
        .build()

        repository = PortfolioSyncRepository(database)
        
        val factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return GitPortfolioViewModel(repository) as T
            }
        }
        viewModel = androidx.lifecycle.ViewModelProvider(this, factory)[GitPortfolioViewModel::class.java]

        // Capture initial OAuth callback deep link
        intent?.let { handleDeepLink(it) }

        setContent {
            GitSyncAITheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainDashboardScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent) {
        val data: Uri? = intent.data
        if (data != null && data.scheme == "gitsync" && data.host == "oauth") {
            val code = data.getQueryParameter("code")
            if (code != null) {
                Toast.makeText(this, "Exchanging authorization token...", Toast.LENGTH_SHORT).show()
                viewModel.handleOAuthCallbackCode(code)
            } else {
                val error = data.getQueryParameter("error")
                Toast.makeText(this, "GitHub Auth Error: $error", Toast.LENGTH_LONG).show()
            }
        }
    }
}
