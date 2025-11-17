package com.biketrainer.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.biketrainer.app.ui.screen.MainScreen
import com.biketrainer.app.ui.theme.BikeTrainerAppTheme

class MainActivity : ComponentActivity() {
    companion object {
        val stravaAuthCode = mutableStateOf<String?>(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle Strava OAuth callback
        handleStravaCallback(intent)

        setContent {
            BikeTrainerAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleStravaCallback(intent)
    }

    private fun handleStravaCallback(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri = intent.data
            if (uri?.scheme == "http" && uri.host == "localhost" && uri.path == "/exchange_token") {
                val code = uri.getQueryParameter("code")
                if (code != null) {
                    stravaAuthCode.value = code
                }
            }
        }
    }
}
