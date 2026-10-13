package com.webwizards.applywise.ui.results

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.webwizards.applywise.data.remote.MatchDto
import com.webwizards.applywise.data.remote.ResultsDto
import com.webwizards.applywise.data.repository.StudentRepository
import com.webwizards.applywise.domain.AppException
import com.webwizards.applywise.domain.MatchType
import com.webwizards.applywise.ui.components.ErrorState
import com.webwizards.applywise.ui.components.InfoChip
import com.webwizards.applywise.ui.components.LoadingState
import com.webwizards.applywise.ui.components.MatchBadge
import com.webwizards.applywise.ui.navigation.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class ResultsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val data: ResultsDto? = null,
    val error: AppException? = null
)

class ResultsViewModel(private val ref: String, private val repo: StudentRepository) : ViewModel() {
    private val _state = MutableStateFlow(ResultsUiState())
    val state: StateFlow<ResultsUiState> = _state.asStateFlow()
    val shareLink get() = repo.shareLink(ref)

    init { load() }

    fun load(refresh: Boolean = false) {
        _state.update { it.copy(loading = !refresh && it.data == null, refreshing = refresh, error = null) }
        viewModelScope.launch {
            try {
                val data = repo.results(ref)
                _state.update { ResultsUiState(loading = false, data = data) }
            } catch (e: AppException) {
                if (e.status == 404) repo.local.forgetResult(ref) // deleted by an admin: drop it from "recent results"
                _state.update { it.copy(loading = false, refreshing = false, error = e) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(ref: String, onBack: () -> Unit, onNewSearch: () -> Unit) {
    val container = appContainer()
    val vm: ResultsViewModel = viewModel(key = "results-$ref", factory = viewModelFactory {
        initializer { ResultsViewModel(ref, container.studentRepository) }
    })
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(state.data?.let { "Hi ${it.student.firstName}" } ?: "Your matches") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (state.data != null) IconButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "My Apply-Wise course matches: ${vm.shareLink}")
                        }
                        context.startActivity(Intent.createChooser(send, "Share results"))
                    }) { Icon(Icons.Outlined.Share, contentDescription = "Share results") }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        val data = state.data
        when {
            state.loading -> LoadingState("Matching your profile…", Modifier.padding(padding))
            data == null -> {
                val e = state.error
                ErrorState(
                    title = if (e?.status == 404 || e?.status == 400) "Results not found" else "Couldn't load your results",
                    message = e?.message ?: "Unknown error",
                    isNetwork = e?.isNetwork == true,
                    onRetry = if (e?.status == 404) null else ({ vm.load() }),
                    secondaryLabel = "Start a new search", onSecondary = onNewSearch,
                    modifier = Modifier.padding(padding)
                )
            }
            else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.load(refresh = true) }, modifier = Modifier.padding(padding)) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item { Summary(data) }
                    if (data.matches.isEmpty()) item { NoMatches(onNewSearch) }
                    items(data.matches, key = { it.courseId }) { MatchCard(it) }
                    item {
                        Text("These recommendations are for guidance only. Requirements change every year — always confirm with the institution before applying.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun Summary(data: ResultsDto) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Your personalised matches", style = MaterialTheme.typography.titleLarge)
                Text("${data.subjectCount} subjects · ${data.student.province}", style = MaterialTheme.typography.bodyMedium)
                if (data.matches.isNotEmpty()) Text("Top ${data.matches.size} of ${data.totalEligible} eligible courses", style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${data.aps}", style = MaterialTheme.typography.displaySmall)
                Text("APS", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MatchCard(m: MatchDto) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(m.course, style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AccountBalance, contentDescription = null, Modifier.padding(end = 4.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(m.institution, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                MatchBadge(MatchType.from(m.matchType))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoChip("APS min ${m.minAps}")
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Place, null); InfoChip(m.province) }
                InfoChip(m.faculty)
            }
            Text(m.reason, style = MaterialTheme.typography.bodyMedium)
            if (m.requirements.isNotBlank()) Text(m.requirements, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NoMatches(onNewSearch: () -> Unit) {
    ElevatedCard {
        Column(Modifier.padding(20.dp)) {
            Text("No matches yet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text("Your APS or subjects don't meet the requirements in our catalogue. Consider a bridging programme, a TVET college or upgrading your results, and speak to a career counsellor.")
            Spacer(Modifier.height(12.dp))
            Button(onClick = onNewSearch) { Text("Try different results") }
        }
    }
}
