package com.mrredhood.nexus.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mrredhood.nexus.R
import com.mrredhood.nexus.ui.components.OnboardingNotice
import com.mrredhood.nexus.ui.components.NexusHome
import kotlinx.coroutines.launch

private enum class Destination(val label: String) {
    HOME("Home"), SEARCH("Search"), AUTOMATIONS("Automations"), SETTINGS("Settings")
}

@Composable
fun NexusApp() {
    var noticeVisible by remember { mutableStateOf(true) }
    var current by remember { mutableStateOf(Destination.HOME) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "Nexus", style = MaterialTheme.typography.titleLarge) }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = current == Destination.HOME,
                    onClick = { current = Destination.HOME },
                    icon = { Icon(Icons.Rounded.Home, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = current == Destination.SEARCH,
                    onClick = { current = Destination.SEARCH },
                    icon = { Icon(Icons.Rounded.Search, contentDescription = "Search") },
                    label = { Text("Search") }
                )
                NavigationBarItem(
                    selected = current == Destination.AUTOMATIONS,
                    onClick = { current = Destination.AUTOMATIONS },
                    icon = { Icon(Icons.Rounded.Sync, contentDescription = "Automations") },
                    label = { Text("Automations") }
                )
                NavigationBarItem(
                    selected = current == Destination.SETTINGS,
                    onClick = { current = Destination.SETTINGS },
                    icon = { Icon(Icons.Rounded.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (current) {
                Destination.HOME -> NexusHome(padding)
                Destination.SEARCH -> PlaceholderPage("Universal search")
                Destination.AUTOMATIONS -> PlaceholderPage("Automations")
                Destination.SETTINGS -> PlaceholderPage("Settings")
            }

            if (noticeVisible) {
                OnboardingNotice(
                    onDismiss = { noticeVisible = false },
                    onPrivacy = {
                        noticeVisible = false
                        scope.launch { snackbarHostState.showSnackbar("Privacy policy will open from Settings") }
                    }
                )
            }
        }
    }
}

@Composable
private fun PlaceholderPage(title: String) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(
            "Foundation screen — capability is added behind this stable shell.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
