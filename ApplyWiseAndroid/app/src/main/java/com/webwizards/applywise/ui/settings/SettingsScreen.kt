package com.webwizards.applywise.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.webwizards.applywise.BuildConfig
import com.webwizards.applywise.data.repository.StudentRepository
import com.webwizards.applywise.domain.AppException
import com.webwizards.applywise.ui.navigation.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class SettingsUiState(
    val url: String,
    val testing: Boolean = false,
    val success: String? = null,
    val error: String? = null
)

class SettingsViewModel(private val repo: StudentRepository) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState(url = repo.serverUrl))
    val state = _state.asStateFlow()

    fun onUrlChange(v: String) = _state.update { it.copy(url = v.trim(), success = null, error = null) }

    fun save() {
        val raw = _state.value.url
        val url = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "http://$raw"
        _state.update { it.copy(url = url, testing = true, success = null, error = null) }
        viewModelScope.launch {
            try {
                val c = repo.changeServer(url)
                _state.update { it.copy(testing = false, success = "Connected — ${c.courses} courses at ${c.institutions} institutions. Saved.") }
            } catch (e: AppException) {
                _state.update { it.copy(testing = false, error = e.message) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val container = appContainer()
    val vm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(container.studentRepository) } })
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(topBar = {
        TopAppBar(title = { Text("Server settings") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") } })
    }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("The app talks to the same Apply-Wise server as the website, so web and app applications appear together on the dashboard.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = state.url, onValueChange = vm::onUrlChange, singleLine = true, label = { Text("Server address") },
                placeholder = { Text("http://192.168.1.20:3000") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = vm::save, enabled = !state.testing && state.url.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                if (state.testing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Test & save")
            }
            state.success?.let { Status(it, ok = true) }
            state.error?.let { Status(it, ok = false) }

            Text("Quick fill", style = MaterialTheme.typography.titleSmall)
            AssistChip(onClick = { vm.onUrlChange(BuildConfig.DEFAULT_SERVER_URL) }, label = { Text("Android emulator (10.0.2.2:3000)") })

            Spacer(Modifier.height(4.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Using a real phone?", style = MaterialTheme.typography.titleSmall)
                    Text("1. Connect the phone and computer to the same Wi-Fi.\n" +
                        "2. On the computer, add HOST=0.0.0.0 to the server's .env file and restart it.\n" +
                        "3. Find the computer's IP address (Windows: run ipconfig, look for IPv4).\n" +
                        "4. Enter http://<that IP>:3000 above.\n" +
                        "5. If it still fails, allow Node.js through Windows Firewall on private networks.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun Status(text: String, ok: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Card(colors = CardDefaults.cardColors(
        containerColor = if (ok) scheme.primaryContainer else scheme.errorContainer,
        contentColor = if (ok) scheme.onPrimaryContainer else scheme.onErrorContainer
    )) {
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, contentDescription = null)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
