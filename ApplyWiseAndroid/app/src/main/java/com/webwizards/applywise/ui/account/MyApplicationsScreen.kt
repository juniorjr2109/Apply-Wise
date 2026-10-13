package com.webwizards.applywise.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.webwizards.applywise.data.remote.ApplicationSummary
import com.webwizards.applywise.data.remote.UserDto
import com.webwizards.applywise.data.repository.AuthRepository
import com.webwizards.applywise.domain.AppException
import com.webwizards.applywise.ui.components.ErrorState
import com.webwizards.applywise.ui.components.LoadingState
import com.webwizards.applywise.ui.navigation.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Immutable
data class MyApplicationsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val items: List<ApplicationSummary> = emptyList(),
    val error: AppException? = null
)

class MyApplicationsViewModel(private val auth: AuthRepository) : ViewModel() {
    val user: StateFlow<UserDto?> = auth.user
    private val _state = MutableStateFlow(MyApplicationsUiState())
    val state = _state.asStateFlow()

    init { if (auth.user.value != null) load() else _state.update { it.copy(loading = false) } }

    fun load(refresh: Boolean = false) {
        _state.update { it.copy(loading = !refresh && it.items.isEmpty(), refreshing = refresh, error = null) }
        viewModelScope.launch {
            try {
                val items = auth.myApplications()
                _state.update { MyApplicationsUiState(loading = false, items = items) }
            } catch (e: AppException) {
                _state.update { it.copy(loading = false, refreshing = false, error = e) }
            }
        }
    }
}

private val dateFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyApplicationsScreen(onBack: () -> Unit, onOpenResult: (String) -> Unit, onApply: () -> Unit, onSignIn: () -> Unit) {
    val container = appContainer()
    val vm: MyApplicationsViewModel = viewModel(factory = viewModelFactory { initializer { MyApplicationsViewModel(container.authRepository) } })
    val user by vm.user.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()

    // Signed out here (or the session expired while open): send to sign-in, which returns to this list.
    LaunchedEffect(user) { if (user == null) onSignIn() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My applications") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") } }
            )
        },
        floatingActionButton = {
            if (state.items.isNotEmpty()) ExtendedFloatingActionButton(onClick = onApply,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) }, text = { Text("New search") })
        }
    ) { padding ->
        when {
            state.loading -> LoadingState("Loading your applications…", Modifier.padding(padding))
            state.error != null && state.items.isEmpty() -> ErrorState(
                title = "Couldn't load your applications",
                message = state.error?.message.orEmpty(),
                isNetwork = state.error?.isNetwork == true,
                onRetry = { vm.load() },
                modifier = Modifier.padding(padding)
            )
            state.items.isEmpty() -> Column(
                Modifier.padding(padding).fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                Text("No applications yet", style = MaterialTheme.typography.titleLarge)
                Text("Searches you submit while signed in are saved here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 20.dp))
                Button(onClick = onApply) { Text("Find my courses") }
            }
            else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.load(refresh = true) }, modifier = Modifier.padding(padding)) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.items, key = { it.id }) { a ->
                        ElevatedCard(onClick = { onOpenResult(a.ref) }) {
                            ListItem(
                                leadingContent = {
                                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                                        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("${a.aps}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                            Text("APS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                        }
                                    }
                                },
                                headlineContent = { Text("${a.matchCount} course matches") },
                                supportingContent = {
                                    Text("${a.province} · Matric ${a.matricYear}\n" +
                                        runCatching { dateFormat.format(Instant.parse(a.createdAt)) }.getOrDefault(a.createdAt))
                                },
                                trailingContent = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Open results") }
                            )
                        }
                    }
                }
            }
        }
    }
}
