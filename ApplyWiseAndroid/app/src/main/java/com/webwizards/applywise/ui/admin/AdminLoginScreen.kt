package com.webwizards.applywise.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.webwizards.applywise.ui.theme.ApplyWiseTheme
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.webwizards.applywise.data.repository.AuthRepository
import com.webwizards.applywise.domain.AppException
import com.webwizards.applywise.ui.navigation.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class LoginUiState(
    val checkingSession: Boolean = true,
    val submitting: Boolean = false,
    val error: String? = null,
    val isNetworkError: Boolean = false,
    val signedIn: Boolean = false
)

class AdminLoginViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state = _state.asStateFlow()

    init {
        // Already signed in during this app session? Skip the form.
        viewModelScope.launch {
            val user = auth.user.value
            val ok = user != null && user.isAdmin
            _state.update { it.copy(checkingSession = false, signedIn = ok) }
        }
    }

    fun login(username: String, password: String) {
        if (_state.value.submitting) return
        if (username.isBlank() || password.isEmpty()) { _state.update { it.copy(error = "Enter your username and password.", isNetworkError = false) }; return }
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            try {
                val user = auth.signIn(username.trim(), password)
                if (user.isAdmin) {
                    _state.update { it.copy(submitting = false, signedIn = true) }
                } else {
                    _state.update { it.copy(submitting = false, error = "This account does not have administrator privileges.", isNetworkError = false) }
                }
            } catch (e: AppException) {
                _state.update { it.copy(submitting = false, error = e.message, isNetworkError = e.isNetwork) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminLoginScreen(reason: String?, onBack: () -> Unit, onSignedIn: () -> Unit, onSettings: () -> Unit) {
    val container = appContainer()
    val vm: AdminLoginViewModel = viewModel(factory = viewModelFactory { initializer { AdminLoginViewModel(container.authRepository) } })
    val state by vm.state.collectAsStateWithLifecycle()
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") } // cleared after each failed attempt
    var showPassword by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.signedIn) { if (state.signedIn) onSignedIn() }
    LaunchedEffect(state.error) { if (state.error != null && !state.isNetworkError) password = "" }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Admin") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") } },
            actions = { IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Server settings") } }
        )
    }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(Modifier.widthIn(max = 440.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(Icons.Outlined.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                Text("Administrator sign in", style = MaterialTheme.typography.headlineMedium)
                Text("Sign in to view applications and system statistics.", color = MaterialTheme.colorScheme.onSurfaceVariant)

                val notice = when (reason) { "expired" -> "Your session expired. Please sign in again."; "signedout" -> "You have been signed out."; else -> null }
                if (notice != null && state.error == null) Banner(notice, isError = false)
                state.error?.let { Banner(it, isError = true) }
                if (state.isNetworkError) TextButton(onClick = onSettings) { Text("Check server settings") }

                OutlinedTextField(
                    value = username, onValueChange = { username = it.take(64) }, label = { Text("Username") }, singleLine = true,
                    enabled = !state.submitting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it.take(256) }, label = { Text("Password") }, singleLine = true,
                    enabled = !state.submitting,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showPassword) "Hide password" else "Show password")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { vm.login(username, password) }),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = { vm.login(username, password) }, enabled = !state.submitting && !state.checkingSession, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    if (state.submitting || state.checkingSession) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text("Sign in")
                }
                Spacer(Modifier.height(4.dp))
                Text("Server: ${container.studentRepository.serverUrl}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Banner(text: String, isError: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Card(colors = CardDefaults.cardColors(
        containerColor = if (isError) scheme.errorContainer else scheme.secondaryContainer,
        contentColor = if (isError) scheme.onErrorContainer else scheme.onSecondaryContainer
    ), modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview(showBackground = true)
@Composable
private fun AdminLoginScreenPreview() {
    ApplyWiseTheme {
        AdminLoginScreen(reason = null, onBack = {}, onSignedIn = {}, onSettings = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun BannerPreview() {
    ApplyWiseTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Banner(text = "You have been signed out.", isError = false)
            Banner(text = "Invalid credentials. Please try again.", isError = true)
        }
    }
}
