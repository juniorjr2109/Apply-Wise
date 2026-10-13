package com.webwizards.applywise.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.webwizards.applywise.data.remote.UserDto
import com.webwizards.applywise.ui.navigation.DrawerDestination

/** Navigation drawer opened from the menu button at the top left of Home. */
@Composable
fun AppDrawer(
    user: UserDto?,
    onDestination: (DrawerDestination) -> Unit,
    onSignIn: () -> Unit,
    onRegister: () -> Unit,
    onSignOut: () -> Unit
) {
    ModalDrawerSheet {
        Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
            DrawerHeader(user)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Item("Home", Icons.Outlined.Home, selected = true) { onDestination(DrawerDestination.Home) }
            Item("Find my courses", Icons.Outlined.School) { onDestination(DrawerDestination.Apply) }
            Item(if (user != null) "My applications" else "My applications (sign in)", Icons.Outlined.Description) {
                if (user != null) onDestination(DrawerDestination.MyApplications) else onSignIn()
            }
            if (user?.isAdmin == true) {
                Item("Admin dashboard", Icons.Outlined.AdminPanelSettings) { onDestination(DrawerDestination.Dashboard) }
            }
            Item("Server settings", Icons.Outlined.Settings) { onDestination(DrawerDestination.Settings) }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            if (user == null) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.AutoMirrored.Outlined.Login, contentDescription = null); Spacer(Modifier.size(8.dp)); Text("Sign in")
                    }
                    OutlinedButton(onClick = onRegister, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.PersonAdd, contentDescription = null); Spacer(Modifier.size(8.dp)); Text("Create account")
                    }
                }
            } else {
                Item("Sign out", Icons.AutoMirrored.Outlined.Logout, onClick = onSignOut)
            }
        }
    }
}

@Composable
private fun DrawerHeader(user: UserDto?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(user, size = 48)
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(user?.displayName ?: "Welcome to Apply-Wise", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    user == null -> "Sign in to save your applications"
                    user.isAdmin -> "Administrator · ${user.username}"
                    else -> user.username
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Initials in a circle, or a generic icon when signed out. */
@Composable
fun Avatar(user: UserDto?, size: Int = 36) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.size(size.dp)) {
        Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            if (user == null) Icon(Icons.AutoMirrored.Outlined.Login, contentDescription = null, Modifier.size((size / 2).dp))
            else Text(user.initials, style = if (size >= 44) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun Item(label: String, icon: ImageVector, selected: Boolean = false, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label) },
        icon = { Icon(icon, contentDescription = null) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}
