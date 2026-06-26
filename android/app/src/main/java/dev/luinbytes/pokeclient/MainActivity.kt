package dev.luinbytes.pokeclient

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    var setupVisible by remember(settings) { mutableStateOf(settings.pokeApiKey.isBlank()) }
    var draft by remember { mutableStateOf(sharedText) }

    LaunchedEffect(settings.backendBaseUrl, settings.pokeUserId) {
        viewModel.observeBackend(settings)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Poke") },
                actions = {
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
            if (setupVisible) {
                SetupPane(settings, onSave = {
                    viewModel.saveSettings(it)
                    setupVisible = false
                })
            } else {
                ChatPane(
                    messages = messages,
                    draft = draft,
                    onDraftChange = { draft = it },
                    onSend = {
                        viewModel.send(draft)
                        draft = ""
                    }
                )
            }
        }
    }
}

@Composable
fun SetupPane(settings: AppSettings, onSave: (AppSettings) -> Unit) {
    var apiKey by remember(settings.pokeApiKey) { mutableStateOf(settings.pokeApiKey) }
    var backend by remember(settings.backendBaseUrl) { mutableStateOf(settings.backendBaseUrl) }
    var pokeUserId by remember(settings.pokeUserId) { mutableStateOf(settings.pokeUserId) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Connect Poke", style = MaterialTheme.typography.headlineMedium)
        Text("Use a Poke API key for direct sends. Add the companion backend and Poke user ID for bidirectional delivery.")
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("Poke API key") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
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
        Button(onClick = { onSave(AppSettings(apiKey, backend, pokeUserId)) }) {
            Text("Save")
        }
    }
}

@Composable
fun ChatPane(
    messages: List<ChatMessage>,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            items(messages, key = { it.id }) { message ->
                MessageBubble(message)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
            Button(onClick = onSend, enabled = draft.isNotBlank()) {
                Text("Send")
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val outbound = message.direction == MessageDirection.Outbound
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (outbound) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (outbound) Color(0xFFDCF3E4) else Color.White,
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
                if (message.actions.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        message.actions.forEach { action ->
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFEAF1FF), RoundedCornerShape(999.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(action.label, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
