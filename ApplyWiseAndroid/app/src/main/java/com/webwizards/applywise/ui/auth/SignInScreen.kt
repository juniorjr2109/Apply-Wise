package com.webwizards.applywise.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.webwizards.applywise.data.remote.UserDto
import com.webwizards.applywise.data.repository.AuthRepository
import com.webwizards.applywise.domain.AppException
import com.webwizards.applywise.domain.Aps
import com.webwizards.applywise.ui.navigation.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val EMAIL_RE = Regex("^[^\\s@]{1,64}@[^\\s@]{1,190}\\.[A-Za-z]{2,24}$")
private const val MIN_PASSWORD = 8

@Immutable
data class SignInUiState(
    val submitting: Boolean = false,
    val error: String? = null,
    val isNetworkError: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val signedInUser: UserDto? = null
)

class SignInViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(SignInUiState())
    val state = _state.asStateFlow()

    fun clearErrors() = _state.update { it.copy(error = null, fieldErrors = emptyMap(), isNetworkError = false) }
    fun clearField(field: String) = _state.update { it.copy(fieldErrors = it.fieldErrors - field) }

    fun signIn(identifier: String, password: String) {
        val errors = buildMap {
            if (identifier.isBlank()) put("identifier", "Enter your email or username.")
            if (password.isEmpty()) put("password", "Enter your password.")
        }
        if (errors.isNotEmpty()) { _state.update { it.copy(fieldErrors = errors, error = null) }; return }
        execute { auth.signIn(identifier, password) }
    }

    fun register(firstName: String, lastName: String, email: String, password: String, confirm: String) {
        // Same rules as the server, so most mistakes are caught before a network round trip.
        val errors = buildMap {
            if (!Aps.isValidName(firstName)) put("firstName", "Enter your first name (letters only).")
            if (!Aps.isValidName(lastName)) put("lastName", "Enter your last name (letters only).")
            if (!EMAIL_RE.matches(email.trim())) put("email", "Enter a valid email address.")
            when {
                password.length < MIN_PASSWORD -> put("password", "Use at least $MIN_PASSWORD characters.")
                password.none(Char::isLetter) || password.none(Char::isDigit) -> put("password", "Use letters and at least one number.")
            }
            if (confirm != password) put("confirm", "Passwords don't match.")
        }
        if (errors.isNotEmpty()) { _state.update { it.copy(fieldErrors = errors, error = null) }; return }
        execute { auth.register(firstName, lastName, email, password) }
    }

    private fun execute(block: suspend () -> UserDto) {
        if (_state.value.submitting) return // ignore double taps
        _state.update { it.copy(submitting = true, error = null, fieldErrors = emptyMap()) }
        viewModelScope.launch {
            try {
                val user = block()
                _state.update { it.copy(submitting = false, signedInUser = user) }
            } catch (e: AppException) {
                // Server field names map onto ours; "username" is the sign-in identifier.
                val fields = e.fieldErrors.mapKeys { (k, _) -> if (k == "username") "identifier" else k }
                    .mapValues { (_, v) -> v.firstOrNull().orEmpty() }
                _state.update { it.copy(submitting = false, error = e.message, isNetworkError = e.isNetwork, fieldErrors = fields) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInScreen(
    initialMode: String,
    reason: String?,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onSignedIn: (UserDto) -> Unit
) {
    val container = appContainer()
    val vm: SignInViewModel = viewModel(factory = viewModelFactory { initializer { SignInViewModel(container.authRepository) } })
    val state by vm.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current

    var register by rememberSaveable { mutableStateOf(initialMode == "register") }
    var identifier by rememberSaveable { mutableStateOf("") }
    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    // Passwords are deliberately not saved across process death.
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.signedInUser) { state.signedInUser?.let(onSignedIn) }
    LaunchedEffect(state.error) { if (state.error != null && !state.isNetworkError && !register) password = "" }

    fun submit() {
        focus.clearFocus()
        if (register) vm.register(firstName, lastName, email, password, confirm) else vm.signIn(identifier, password)
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (register) "Create account" else "Sign in") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") } },
            actions = { IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Server settings") } }
        )
    }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(Modifier.widthIn(max = 440.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = !register, onClick = { register = false; vm.clearErrors() },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Sign in") }
                    SegmentedButton(selected = register, onClick = { register = true; vm.clearErrors() },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Create account") }
                }

                val notice = when (reason) {
                    "expired" -> "Your session expired. Please sign in again."
                    "admin" -> "The dashboard is for administrators. Sign in with an admin account."
                    else -> null
                }
                if (notice != null && state.error == null) Banner(notice, isError = false)
                state.error?.let { Banner(it, isError = true) }
                if (state.isNetworkError) TextButton(onClick = onSettings) { Text("Check server settings") }

                val passwordField: @Composable (String, String, String, (String) -> Unit, ImeAction) -> Unit = { key, label, value, onChange, ime ->
                    OutlinedTextField(
                        value = value, onValueChange = { onChange(it.take(256)); vm.clearField(key) },
                        label = { Text(label) }, singleLine = true, enabled = !state.submitting,
                        isError = key in state.fieldErrors, supportingText = state.fieldErrors[key]?.let { { Text(it) } },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (showPassword) "Hide password" else "Show password")
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ime),
                        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }, onGo = { submit() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                AnimatedContent(targetState = register, label = "auth-mode") { isRegister ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (isRegister) {
                            Text("Save your applications and see them on any phone.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TextInput("firstName", "First name", firstName, { firstName = it.take(50) }, state, vm,
                                    KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next), Modifier.weight(1f))
                                TextInput("lastName", "Last name", lastName, { lastName = it.take(50) }, state, vm,
                                    KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next), Modifier.weight(1f))
                            }
                            TextInput("email", "Email", email, { email = it.take(254) }, state, vm,
                                KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, autoCorrectEnabled = false))
                            passwordField("password", "Password (8+ characters, include a number)", password, { password = it }, ImeAction.Next)
                            passwordField("confirm", "Confirm password", confirm, { confirm = it }, ImeAction.Go)
                        } else {
                            Text("Students sign in with their email. Administrators use their username.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextInput("identifier", "Email or username", identifier, { identifier = it.take(254) }, state, vm,
                                KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, autoCorrectEnabled = false))
                            passwordField("password", "Password", password, { password = it }, ImeAction.Go)
                        }
                    }
                }

                Button(onClick = { submit() }, enabled = !state.submitting, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    if (state.submitting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(if (register) "Create account" else "Sign in")
                }
                TextButton(onClick = { register = !register; vm.clearErrors() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(if (register) "Already have an account? Sign in" else "New here? Create an account")
                }
                Text("Server: ${container.studentRepository.serverUrl}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TextInput(
    key: String, label: String, value: String, onChange: (String) -> Unit,
    state: SignInUiState, vm: SignInViewModel, keyboard: KeyboardOptions, modifier: Modifier = Modifier.fillMaxWidth()
) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = value, onValueChange = { onChange(it); vm.clearField(key) }, label = { Text(label) }, singleLine = true,
        enabled = !state.submitting, isError = key in state.fieldErrors,
        supportingText = state.fieldErrors[key]?.let { { Text(it) } },
        keyboardOptions = keyboard, keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
        modifier = modifier
    )
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
