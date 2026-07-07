package ru.homelab.kidguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
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
                // Edge-to-edge: без глобального Scaffold — каждый экран сам управляет system insets
                // (Scaffold с topBar/bottomBar на экранах либо safeDrawingPadding там, где Scaffold нет).
                KidGuardApp(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
