package com.webwizards.applywise.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.webwizards.applywise.data.remote.ApplicationDetail
import com.webwizards.applywise.data.remote.ApplicationSummary
import com.webwizards.applywise.data.remote.StatsDto
import com.webwizards.applywise.domain.Aps
import com.webwizards.applywise.domain.MatchType
import com.webwizards.applywise.ui.components.BarList
import com.webwizards.applywise.ui.components.DailyBars
import com.webwizards.applywise.ui.components.ErrorState
import com.webwizards.applywise.ui.components.InfoChip
import com.webwizards.applywise.ui.components.MatchBadge
import com.webwizards.applywise.ui.components.StatCard
import com.webwizards.applywise.ui.navigation.appContainer
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val dateFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.systemDefault())
private fun formatDate(iso: String) = runCatching { dateFormat.format(Instant.parse(iso)) }.getOrDefault(iso)
private fun Double?.fmt() = this?.let { if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it) } ?: "–"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onSessionEnded: (reason: String) -> Unit, onSignedOut: () -> Unit, onBack: () -> Unit) {
    val container = appContainer()
    val vm: DashboardViewModel = viewModel(factory = viewModelFactory {
        initializer { DashboardViewModel(container.adminRepository, container.authRepository) }
    })
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    LaunchedEffect(state.signedOutReason) { state.signedOutReason?.let(onSessionEnded) }
    LaunchedEffect(state.signedOut) { if (state.signedOut) onSignedOut() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }

    // Infinite scroll: load the next page when the last few rows become visible.
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { nearEnd }.distinctUntilChanged().filter { it }.collect { vm.loadMore() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Dashboard")
                        if (state.username.isNotBlank()) Text("Signed in as ${state.username}", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = vm::refresh) { Icon(Icons.Outlined.Refresh, contentDescription = "Refresh") }
                    IconButton(onClick = vm::logout) { Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = "Sign out") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.padding(padding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "stats") { StatsSection(state.stats, state.statsError) }
                item(key = "filters") { Filters(state, vm) }
                if (state.listLoading && state.items.isNotEmpty()) item(key = "progress") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                when {
                    state.listLoading && state.items.isEmpty() -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                    state.listError != null && state.items.isEmpty() -> item(key = "error") {
                        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
                            ErrorState("Couldn't load applications", state.listError ?: "", onRetry = vm::refresh)
                        }
                    }
                    state.items.isEmpty() -> item(key = "empty") {
                        Text(
                            if (state.query.isNotBlank() || state.province != null) "No applications match your filters."
                            else "No applications yet. They appear here as students submit the form.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                items(state.items, key = { it.id }) { item ->
                    ApplicationRow(item, onClick = { vm.openDetail(item.id) }, onDelete = { vm.requestDelete(item) },
                        modifier = Modifier.animateItem())
                }
                if (state.loadingMore) item(key = "more") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
                }
            }
        }
    }

    if (state.detailLoading || state.detail != null || state.detailError != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = vm::closeDetail, sheetState = sheetState) {
            DetailSheet(state, onDelete = { d ->
                state.items.firstOrNull { it.id == d.id }?.let(vm::requestDelete)
                    ?: vm.requestDelete(ApplicationSummary(d.id, d.ref, d.firstName, d.lastName, d.province, d.matricYear, d.aps, d.matches.size, d.createdAt))
            })
        }
    }

    state.pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = vm::cancelDelete,
            icon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
            title = { Text("Delete application?") },
            text = { Text("The application from ${item.firstName} ${item.lastName} will be permanently removed. This cannot be undone.") },
            confirmButton = { TextButton(onClick = vm::confirmDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = vm::cancelDelete) { Text("Cancel") } }
        )
    }
}

@Composable
private fun StatsSection(stats: StatsDto?, error: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (error != null && stats == null) {
            Text(error, color = MaterialTheme.colorScheme.error)
            return
        }
        val t = stats?.totals
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Applications", t?.applications?.toString() ?: "–", "${t?.today ?: 0} today", Modifier.weight(1f))
            StatCard("Average APS", t?.avgAps.fmt(), "out of ${Aps.MAX_APS}", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Avg matches", t?.avgMatches.fmt(), "per student", Modifier.weight(1f))
            StatCard("Top province", t?.topProvince ?: "–", "most applications", Modifier.weight(1f))
        }
        if (stats != null) {
            Panel("Applications — last 14 days") { DailyBars(stats.daily) }
            Panel("APS distribution") { BarList(stats.apsBands) }
            Panel("By province") { BarList(stats.byProvince) }
            Panel("Most common interests") { BarList(stats.topInterests) }
            Text("Catalogue: ${stats.catalogue.courses} courses · ${stats.catalogue.institutions} institutions · updated ${formatDate(stats.generatedAt)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Panel(title: String, content: @Composable () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 12.dp))
            content()
        }
    }
}

@Composable
private fun Filters(state: DashboardUiState, vm: DashboardViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        Text("Applications (${state.total})", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = state.query, onValueChange = vm::onQueryChange, singleLine = true,
            placeholder = { Text("Search by name") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = if (state.query.isNotEmpty()) { { IconButton(onClick = { vm.onQueryChange("") }) { Icon(Icons.Outlined.Clear, contentDescription = "Clear search") } } } else null,
            modifier = Modifier.fillMaxWidth()
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = state.province == null, onClick = { vm.onProvinceChange(null) }, label = { Text("All") }) }
            items(Aps.PROVINCES) { p ->
                FilterChip(selected = state.province == p, onClick = { vm.onProvinceChange(if (state.province == p) null else p) }, label = { Text(p) })
            }
        }
    }
}

@Composable
private fun ApplicationRow(item: ApplicationSummary, onClick: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text("${item.firstName} ${item.lastName}", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text("${item.province} · Matric ${item.matricYear} · ${item.matchCount} matches\n${formatDate(item.createdAt)}") },
            leadingContent = {
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${item.aps}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("APS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            },
            trailingContent = {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete ${item.firstName} ${item.lastName}", tint = MaterialTheme.colorScheme.error)
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailSheet(state: DashboardUiState, onDelete: (ApplicationDetail) -> Unit) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
        when {
            state.detailLoading -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.detailError != null -> Text(state.detailError, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
            state.detail != null -> {
                val d = state.detail
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        Text("${d.firstName} ${d.lastName}", style = MaterialTheme.typography.headlineSmall)
                        Text("${d.province} · Matric ${d.matricYear} · APS ${d.aps}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatDate(d.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item { SectionTitle("Subjects") }
                    items(d.subjects, key = { "s-${it.name}" }) { s ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(s.name, Modifier.weight(1f))
                            Text("${s.grade}% · ${s.points} pts", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                        HorizontalDivider()
                    }
                    item {
                        SectionTitle("Interests & goals")
                        if (d.interests.isEmpty()) Text("No interests selected.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { d.interests.forEach { InfoChip(it) } }
                        if (d.careerGoal.isNotBlank()) Text("Career goal: ${d.careerGoal}", Modifier.padding(top = 6.dp))
                        if (d.studyLocation.isNotBlank()) Text("Preferred location: ${d.studyLocation}")
                    }
                    item { SectionTitle("Current top matches (${d.matches.size})") }
                    items(d.matches, key = { "m-${it.courseId}" }) { m ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(m.course, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(m.institution, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            MatchBadge(MatchType.from(m.matchType))
                        }
                    }
                    item {
                        TextButton(onClick = { onDelete(d) }, modifier = Modifier.padding(vertical = 12.dp)) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(" Delete application", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}
