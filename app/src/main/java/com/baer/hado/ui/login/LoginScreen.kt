package com.baer.hado.ui.login

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import com.baer.hado.ui.theme.AppSpacing

@Composable
fun LoginScreen(
    onAuthenticated: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
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
                    viewModel.setError(context.getString(R.string.login_auth_failed))
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
    var showTokenSection by remember { mutableStateOf(uiState.token.isNotBlank()) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.screenHorizontal, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sectionGap)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.large
        ) {
            androidx.compose.material3.Icon(
                painter = painterResource(id = R.mipmap.ic_launcher_monochrome),
                contentDescription = null,
                modifier = Modifier
                    .padding(20.dp)
                    .size(72.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = stringResource(R.string.app_subtitle),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Text(
                text = stringResource(R.string.login_primary_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.9f)
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.login_connect_oauth),
                    style = MaterialTheme.typography.titleLarge
                )

                OutlinedTextField(
                    value = uiState.serverUrl,
                    onValueChange = onServerUrlChanged,
                    label = { Text(stringResource(R.string.login_url_label)) },
                    placeholder = { Text(stringResource(R.string.login_url_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(onGo = { onConnectOAuth() }),
                    enabled = !uiState.isLoading
                )

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
                        Text(stringResource(R.string.login_connect_oauth))
                    }
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.login_or_token),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.login_token_supporting),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(
                    onClick = { showTokenSection = !showTokenSection },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        if (showTokenSection) {
                            stringResource(R.string.login_hide_token_section)
                        } else {
                            stringResource(R.string.login_show_token_section)
                        }
                    )
                }

                if (showTokenSection) {
                    OutlinedTextField(
                        value = uiState.token,
                        onValueChange = onTokenChanged,
                        label = { Text(stringResource(R.string.login_token_label)) },
                        placeholder = { Text(stringResource(R.string.login_token_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onSaveToken() }),
                        enabled = !uiState.isLoading
                    )

                    OutlinedButton(
                        onClick = onSaveToken,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isLoading && uiState.token.isNotBlank() && uiState.serverUrl.isNotBlank()
                    ) {
                        Text(stringResource(R.string.login_save_token))
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = stringResource(R.string.login_credentials_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.login_local_mode),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )

                Text(
                    text = stringResource(R.string.login_local_mode_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )

                OutlinedButton(
                    onClick = onTryDemo,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.login_local_mode))
                }
            }
        }

        Spacer(Modifier.height(8.dp))
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
