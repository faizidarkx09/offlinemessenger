package com.offlinemessenger.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offlinemessenger.ui.VmFactory
import com.offlinemessenger.ui.components.Avatar
import com.offlinemessenger.ui.rememberLocator
import com.offlinemessenger.ui.theme.Emerald400
import com.offlinemessenger.ui.theme.InkMid
import com.offlinemessenger.viewmodel.ContactsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupCreateScreen(
    onBack: () -> Unit,
    onGroupCreated: (String) -> Unit,
) {
    val locator = rememberLocator()
    val contactsVm: ContactsViewModel = viewModel(factory = VmFactory { ContactsViewModel(locator) })
    val contacts by contactsVm.contacts.collectAsState(initial = emptyList())
    var name by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var error by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = remember { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main) }

    LaunchedEffect(error) {
        error?.let { snackbar.showSnackbar(it); error = null }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New group") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                val repo = locator.chatRepository
                                repo.createGroup(name, selected.toList())
                                    .onSuccess { gid -> onGroupCreated("group:$gid") }
                                    .onFailure { error = it.message }
                            }
                        },
                    ) {
                        Icon(Icons.Filled.Check, "create", tint = Emerald400)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("Group name") },
                singleLine = true,
            )
            Text(
                "Select members (${selected.size}) — only contacts with exchanged keys",
                style = MaterialTheme.typography.bodySmall,
                color = InkMid,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            LazyColumn(Modifier.fillMaxSize()) {
                items(contacts, key = { it.deviceId }) { c ->
                    val checked = c.deviceId in selected
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable {
                                selected = if (checked) selected - c.deviceId else selected + c.deviceId
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Spacer(Modifier.width(12.dp))
                        Avatar(name = c.displayName, size = 40.dp, photoB64 = c.photoB64)
                        Spacer(Modifier.width(12.dp))
                        Text(c.displayName, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
