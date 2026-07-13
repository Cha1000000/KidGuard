package ru.homelab.kidguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
                // Surface с фоном темы под всем приложением: детские экраны без Scaffold иначе
                // просвечивали бы светлым фоном окна в тёмной теме. Insets не трогает (не Scaffold).
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Edge-to-edge: без глобального Scaffold — каждый экран сам управляет system insets
                    // (Scaffold с topBar/bottomBar на экранах либо safeDrawingPadding там, где Scaffold нет).
                    KidGuardApp(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}
