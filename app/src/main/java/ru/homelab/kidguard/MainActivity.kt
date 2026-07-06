package ru.homelab.kidguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import ru.homelab.kidguard.core.ui.KidGuardApp
import ru.homelab.kidguard.ui.theme.KidGuardTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KidGuardTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    KidGuardApp(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}
