package com.baer.hado.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
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

    private var navController: NavHostController? = null

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask: a deep link arriving while the app is open is delivered here.
        setIntent(intent)
        val uri = intent.data ?: return
        val nav = navController ?: return
        if (!tokenManager.isLoggedIn || !nav.graph.hasDeepLink(uri)) return
        // Replace Home rather than stacking a second one, so back doesn't return to the old list.
        nav.navigate(uri, navOptions {
            launchSingleTop = true
            popUpTo(nav.graph.id) { inclusive = true }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            HadoTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    this@MainActivity.navController = navController
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
