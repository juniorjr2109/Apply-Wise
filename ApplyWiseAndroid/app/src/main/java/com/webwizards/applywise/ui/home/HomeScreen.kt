package com.webwizards.applywise.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.webwizards.applywise.data.local.SavedResult
import com.webwizards.applywise.data.remote.UserDto
import com.webwizards.applywise.data.repository.AuthRepository
import com.webwizards.applywise.data.repository.StudentRepository
import com.webwizards.applywise.ui.navigation.DrawerDestination
import com.webwizards.applywise.ui.navigation.appContainer
import com.webwizards.applywise.ui.theme.LocalMatchColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Immutable
data class HomeEvents(val signingOut: Boolean = false, val message: String? = null)

class HomeViewModel(students: StudentRepository, private val auth: AuthRepository) : ViewModel() {
    val user: StateFlow<UserDto?> = auth.user
    val savedResults: StateFlow<List<SavedResult>> = students.local.savedResults
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = MutableStateFlow(HomeEvents())
    val events = _events.asStateFlow()

    fun signOut() {
        if (_events.value.signingOut) return
        _events.update { it.copy(signingOut = true) }
        viewModelScope.launch {
            auth.signOut()
            _events.update { HomeEvents(message = "You've signed out.") }
        }
    }
    fun messageShown() = _events.update { it.copy(message = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onNavigate: (DrawerDestination) -> Unit, onOpenResult: (String) -> Unit, onSignIn: (mode: String) -> Unit) {
    val container = appContainer()
    val vm: HomeViewModel = viewModel(factory = viewModelFactory {
        initializer { HomeViewModel(container.studentRepository, container.authRepository) }
    })
    val user by vm.user.collectAsStateWithLifecycle()
    val saved by vm.savedResults.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    var accountMenu by remember { mutableStateOf(false) }
    val accent = LocalMatchColors.current.accent

    /** Closes the drawer first so it isn't left open when the user comes back. */
    fun closeThen(action: () -> Unit) = scope.launch { drawerState.close(); action() }

    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    LaunchedEffect(events.message) { events.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                user = user,
                onDestination = { d -> closeThen { onNavigate(d) } },
                onSignIn = { closeThen { onSignIn("signin") } },
                onRegister = { closeThen { onSignIn("register") } },
                onSignOut = { closeThen { confirmSignOut = true } }
            )
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Outlined.Menu, contentDescription = "Open navigation menu")
                        }
                    },
                    title = {
                        Text(buildAnnotatedString {
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("apply") }
                            withStyle(SpanStyle(color = accent)) { append("-wise") }
                        }, style = MaterialTheme.typography.titleLarge)
                    },
                    actions = {
                        val u = user
                        if (u == null) {
                            TextButton(onClick = { onSignIn("signin") }) { Text("Sign in") }
                        } else {
                            // Box anchors the menu to the avatar button.
                            Box {
                            IconButton(onClick = { accountMenu = true }) { Avatar(u, size = 32) }
                            DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    Text(u.displayName, style = MaterialTheme.typography.titleSmall)
                                    Text(u.username, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("My applications") },
                                    leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                                    onClick = { accountMenu = false; onNavigate(DrawerDestination.MyApplications) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sign out") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null) },
                                    onClick = { accountMenu = false; confirmSignOut = true }
                                )
                            }
                            }
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbar) }
        ) { padding ->
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Hero(user, onStart = { onNavigate(DrawerDestination.Apply) }) }
                if (user == null) item { SignInPrompt(onSignIn = { onSignIn("signin") }, onRegister = { onSignIn("register") }) }
                if (saved.isNotEmpty()) {
                    item { Text("Recent results on this phone", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                    items(saved, key = { it.ref }) { r ->
                        OutlinedCard(onClick = { onOpenResult(r.ref) }) {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                                leadingContent = { Icon(Icons.Outlined.History, contentDescription = null) },
                                headlineContent = { Text("${r.firstName} · APS ${r.aps}") },
                                supportingContent = { Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(r.savedAt))) },
                                trailingContent = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Open") }
                            )
                        }
                    }
                }
                item { Text("How it works", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                item { Step(Icons.Outlined.EditNote, "Enter your results", "Add your NSC subjects and percentages.") }
                item { Step(Icons.Outlined.Calculate, "See your APS live", "Calculated from your best six subjects, excluding Life Orientation.") }
                item { Step(Icons.Outlined.TaskAlt, "Get matched", "Courses at 22 institutions ranked as Strong, Good or Reach.") }
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            icon = { Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null) },
            title = { Text("Sign out?") },
            text = { Text("You can sign in again at any time. Your applications stay saved to your account.") },
            confirmButton = { TextButton(enabled = !events.signingOut, onClick = { confirmSignOut = false; vm.signOut() }) { Text("Sign out") } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Hero(user: UserDto?, onStart: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(colors = CardDefaults.cardColors(containerColor = scheme.primary, contentColor = scheme.onPrimary)) {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(scheme.primary, scheme.primary.copy(alpha = 0.82f))))
                .padding(24.dp)
        ) {
            Icon(Icons.Outlined.School, contentDescription = null, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(12.dp))
            if (user != null) Text("Hi ${user.firstName.ifBlank { user.displayName }},", style = MaterialTheme.typography.titleMedium)
            Text("Apply smarter,\nnot harder.", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text("Find the qualifications and institutions you qualify for, based on your matric results.", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(containerColor = scheme.onPrimary, contentColor = scheme.primary),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Text("Find my courses")
                Spacer(Modifier.size(8.dp))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SignInPrompt(onSignIn: () -> Unit, onRegister: () -> Unit) {
    OutlinedCard {
        Column(Modifier.padding(18.dp)) {
            Text("Keep your applications", style = MaterialTheme.typography.titleMedium)
            Text("Create a free account to save every search and see it on any phone.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRegister) { Text("Create account") }
                TextButton(onClick = onSignIn) { Text("Sign in") }
            }
        }
    }
}

@Composable
private fun Step(icon: ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(10.dp).size(24.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
