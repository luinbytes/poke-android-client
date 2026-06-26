package dev.luinbytes.pokeclient

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AssistChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<PokeViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedText = intent.takeIf { it.action == Intent.ACTION_SEND }
            ?.getStringExtra(Intent.EXTRA_TEXT)
            .orEmpty()
        setContent {
            MaterialTheme {
                PokeApp(viewModel, sharedText)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PokeApp(viewModel: PokeViewModel, sharedText: String) {
    val settings by viewModel.settings.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var setupVisible by rememberSaveable { mutableStateOf(settings.backendBaseUrl.isBlank() || settings.pokeUserId.isBlank()) }
    var draft by remember { mutableStateOf(sharedText) }

    LaunchedEffect(settings.backendBaseUrl, settings.pokeUserId) {
        viewModel.observeBackend(settings)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Poke") },
                actions = {
                    TextButton(onClick = viewModel::clearConversation) {
                        Text("Clear")
                    }
                    TextButton(onClick = { setupVisible = !setupVisible }) {
                        Text(if (setupVisible) "Chat" else "Setup")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8F7F4))
                .padding(padding)
        ) {
            StatusStrip(uiState)
            if (setupVisible) {
                SetupPane(settings, onSave = {
                    viewModel.saveSettings(it)
                    setupVisible = false
                }, onUseLocalQa = {
                    viewModel.useLocalQaSettings()
                    setupVisible = false
                })
            } else {
                ChatPane(
                    messages = messages,
                    uiState = uiState,
                    draft = draft,
                    onDraftChange = { draft = it },
                    onSend = {
                        viewModel.send(draft)
                        draft = ""
                    },
                    onAction = viewModel::performAction,
                    onRetry = viewModel::retry
                )
            }
        }
    }
}

@Composable
fun StatusStrip(uiState: UiState) {
    Surface(
        color = if (uiState.backendConnected) Color(0xFFE3F8EA) else Color(0xFFFFF4D8),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = uiState.status,
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF344054),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun SetupPane(
    settings: AppSettings,
    onSave: (AppSettings) -> Unit,
    onUseLocalQa: () -> Unit
) {
    var backend by remember(settings.backendBaseUrl) { mutableStateOf(settings.backendBaseUrl) }
    var pokeUserId by remember(settings.pokeUserId) { mutableStateOf(settings.pokeUserId) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Connect Poke", style = MaterialTheme.typography.headlineMedium)
        Text("Connect to the companion backend. The backend keeps the Poke API key and handles bidirectional delivery.")
        OutlinedTextField(
            value = backend,
            onValueChange = { backend = it },
            label = { Text("Backend URL") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = pokeUserId,
            onValueChange = { pokeUserId = it },
            label = { Text("Poke user ID") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { onSave(AppSettings(backendBaseUrl = backend, pokeUserId = pokeUserId)) }) {
            Text("Save")
        }
        if (BuildConfig.DEBUG) {
            OutlinedButton(onClick = onUseLocalQa) {
                Text("Use local QA backend")
            }
        }
    }
}

@Composable
fun ChatPane(
    messages: List<ChatMessage>,
    uiState: UiState,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onAction: (String, RichAction) -> Unit,
    onRetry: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (messages.isEmpty()) {
            EmptyState(Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(Modifier.height(8.dp)) }
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message, onAction, onRetry)
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(12.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message Poke") },
                minLines = 1,
                maxLines = 5
            )
            Button(onClick = onSend, enabled = draft.isNotBlank() && !uiState.sending) {
                Text(if (uiState.sending) "..." else "Send")
            }
        }
    }
}

@Composable
fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Ready for Poke", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Send a message, then test inbound delivery by posting a webhook event to the companion backend.",
            color = Color(0xFF667085)
        )
    }
}

@Composable
fun MessageBubble(
    message: ChatMessage,
    onAction: (String, RichAction) -> Unit,
    onRetry: (String) -> Unit
) {
    val outbound = message.direction == MessageDirection.Outbound
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = when {
            message.direction == MessageDirection.System -> Arrangement.Center
            outbound -> Arrangement.End
            else -> Arrangement.Start
        }
    ) {
        Surface(
            color = when (message.direction) {
                MessageDirection.Outbound -> Color(0xFFDCF3E4)
                MessageDirection.System -> Color(0xFFEAF1FF)
                MessageDirection.Inbound -> Color.White
            },
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 1.dp,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(message.text)
                Text(
                    text = message.status.name.lowercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF667085)
                )
                if (message.direction == MessageDirection.Outbound && message.status == MessageStatus.Failed) {
                    Spacer(Modifier.height(6.dp))
                    AssistChip(
                        onClick = { onRetry(message.id) },
                        label = { Text("Retry") }
                    )
                }
                if (message.actions.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        message.actions.forEach { action ->
                            AssistChip(
                                onClick = { onAction(message.id, action) },
                                label = { Text(action.label) }
                            )
                        }
                    }
                }
            }
        }
    }
}
