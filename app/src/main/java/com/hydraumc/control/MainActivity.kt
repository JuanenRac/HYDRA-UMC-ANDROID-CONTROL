package com.hydraumc.control

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.hydraumc.control.ui.CustomSplashScreen
import com.hydraumc.control.viewmodel.RobotViewModel

class MainActivity : ComponentActivity() {
    private val robotViewModel: RobotViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        var keepNativeSplash = true
        splashScreen.setKeepOnScreenCondition { keepNativeSplash }
        
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Remove native splash right away to show our custom Compose splash screen
            LaunchedEffect(Unit) {
                keepNativeSplash = false
            }

            var showSplash by remember { mutableStateOf(true) }

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showSplash) {
                        CustomSplashScreen(onTimeout = { showSplash = false })
                    } else {
                        MainScreen(robotViewModel)
                    }
                }
            }
        }
    }
}
