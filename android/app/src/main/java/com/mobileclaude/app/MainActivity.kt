package com.mobileclaude.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.mobileclaude.app.ui.ClaudeLinkApp
import com.mobileclaude.app.ui.ClaudeLinkTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<AppViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClaudeLinkTheme {
                ClaudeLinkApp(viewModel)
            }
        }
    }
}
