package com.baer.hado.ui.login

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.baer.hado.R
import com.baer.hado.data.api.TokenRefreshInterceptor.Companion.AUTH_CLIENT_ID
import com.baer.hado.data.api.TokenRefreshInterceptor.Companion.AUTH_REDIRECT_URI

@Composable
fun LoginScreen(
    onAuthenticated: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showWebView by remember { mutableStateOf(false) }
    var authUrl by remember { mutableStateOf("") }

    LaunchedEffect(uiState.isAuthenticated) {
        if (uiState.isAuthenticated) {
            onAuthenticated()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (showWebView && authUrl.isNotEmpty()) {
            OAuthWebView(
                authUrl = authUrl,
                onCodeReceived = { code ->
                    showWebView = false
                    viewModel.handleAuthCallback(code)
                },
                onError = {
                    showWebView = false
                    viewModel.setError("Authentication failed")
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            LoginForm(
                uiState = uiState,
                onServerUrlChanged = viewModel::onServerUrlChanged,
                onTokenChanged = viewModel::onTokenChanged,
                onConnectOAuth = {
                    val url = viewModel.getAuthorizeUrl()
                    if (url != null) {
                        authUrl = url
                        showWebView = true
                    }
                },
                onSaveToken = { viewModel.saveWithToken() },
                onTryDemo = { viewModel.enterDemoMode() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        }
    }
}

@Composable
private fun LoginForm(
    uiState: LoginUiState,
    onServerUrlChanged: (String) -> Unit,
    onTokenChanged: (String) -> Unit,
    onConnectOAuth: () -> Unit,
    onSaveToken: () -> Unit,
    onTryDemo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(horizontal = 32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(48.dp))

        Icon(
            painter = painterResource(id = R.mipmap.ic_launcher_monochrome),
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "HAdo",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Home Assistant To-Do Widget",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(48.dp))

        OutlinedTextField(
            value = uiState.serverUrl,
            onValueChange = onServerUrlChanged,
            label = { Text("Home Assistant URL") },
            placeholder = { Text("https://homeassistant.local:8123") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go
            ),
            keyboardActions = KeyboardActions(onGo = { onConnectOAuth() }),
            enabled = !uiState.isLoading
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onConnectOAuth,
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoading
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Connect with OAuth")
            }
        }

        Spacer(Modifier.height(24.dp))

        HorizontalDivider()

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Or use a Long-Lived Access Token",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = uiState.token,
            onValueChange = onTokenChanged,
            label = { Text("Long-Lived Access Token") },
            placeholder = { Text("Paste token from HA profile") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSaveToken() }),
            enabled = !uiState.isLoading
        )

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = onSaveToken,
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoading && uiState.token.isNotBlank() && uiState.serverUrl.isNotBlank()
        ) {
            Text("Save Token")
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Your credentials are stored securely on-device.\nNo data is sent to external servers.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        HorizontalDivider()

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = onTryDemo,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Local Mode")
        }

        Text(
            text = "Use as a standalone to-do app — no Home Assistant required.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(48.dp))
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun OAuthWebView(
    authUrl: String,
    onCodeReceived: (String) -> Unit,
    onError: () -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        val url = request.url.toString()
                        if (url.startsWith(AUTH_REDIRECT_URI)) {
                            val code = request.url.getQueryParameter("code")
                            if (code != null) {
                                onCodeReceived(code)
                            } else {
                                onError()
                            }
                            return true
                        }
                        return false
                    }
                }
                loadUrl(authUrl)
            }
        },
        modifier = modifier
    )
}
