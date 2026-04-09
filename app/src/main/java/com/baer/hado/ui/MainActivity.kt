package com.baer.hado.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.baer.hado.data.local.TokenManager
import com.baer.hado.ui.navigation.NavGraph
import com.baer.hado.ui.navigation.Routes
import com.baer.hado.ui.theme.HadoTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            HadoTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val startDest = if (tokenManager.isLoggedIn) Routes.HOME else Routes.LOGIN

                    NavGraph(
                        navController = navController,
                        startDestination = startDest
                    )
                }
            }
        }
    }
}
