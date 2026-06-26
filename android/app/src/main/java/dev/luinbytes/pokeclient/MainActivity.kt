package dev.luinbytes.pokeclient

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.RoundedRectangle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: PokeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PokeApp(viewModel, intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

private sealed class ConversationRow {
    data class Day(val label: String) : ConversationRow()
    data class Message(val value: ChatMessage) : ConversationRow()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PokeApp(viewModel: PokeViewModel, intent: Intent?) {
    val settings by viewModel.settings.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var setupVisible by rememberSaveable { mutableStateOf(settings.backendBaseUrl.isBlank()) }

    LaunchedEffect(settings.backendBaseUrl, settings.pokeUserId) {
        if (settings.backendBaseUrl.isNotBlank() && settings.pokeUserId.isNotBlank()) {
            setupVisible = false
            viewModel.observeBackend(settings)
        }
    }

    MaterialTheme {
        val backdrop = rememberLayerBackdrop()
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .matchParentSize()
                    .layerBackdrop(backdrop)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFF8FBFF), Color(0xFFEAF9F3), Color(0xFFFFF8FA))
                        )
                    )
            )
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    ChatHeader(
                        backdrop = backdrop,
                        status = uiState.status,
                        connected = uiState.backendConnected,
                        onSetup = { setupVisible = true },
                        onClear = viewModel::clearConversation
                    )
                },
                bottomBar = {
                    Composer(
                        backdrop = backdrop,
                        sending = uiState.sending,
                        initialText = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty(),
                        onSend = viewModel::send
                    )
                }
            ) { padding ->
                Conversation(
                    messages = messages,
                    onRetry = viewModel::retry,
                    onAction = viewModel::performAction,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }

            if (setupVisible) {
                ModalBottomSheet(
                    onDismissRequest = { setupVisible = false },
                    containerColor = Color.Transparent
                ) {
                    SetupSheet(
                        backdrop = backdrop,
                        settings = settings,
                        onSave = viewModel::saveSettings,
                        onUseLocal = {
                            setupVisible = false
                            viewModel.useLocalQaSettings()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatHeader(
    backdrop: LayerBackdrop,
    status: String,
    connected: Boolean,
    onSetup: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LiquidGlassSurface(
            backdrop = backdrop,
            modifier = Modifier.weight(1f),
            cornerRadius = 34.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(if (connected) Color(0xFF0A84FF) else Color(0xFF7C8798), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("P", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text("Poke", fontWeight = FontWeight.Bold, color = Color(0xFF101828))
                    Text(
                        status,
                        color = Color(0xFF667085),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        LiquidGlassSurface(backdrop = backdrop, cornerRadius = 28.dp) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onClear) { Text("Clear") }
                TextButton(onClick = onSetup) { Text("Setup") }
            }
        }
    }
}

@Composable
private fun Conversation(
    messages: List<ChatMessage>,
    onRetry: (String) -> Unit,
    onAction: (String, RichAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = remember(messages) { conversationRows(messages) }
    val listState = rememberLazyListState()

    LaunchedEffect(rows.size) {
        if (rows.isNotEmpty()) listState.animateScrollToItem(rows.lastIndex)
    }

    if (rows.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No messages yet", fontWeight = FontWeight.Bold, color = Color(0xFF101828))
                Text("Send a message when Poke is connected.", color = Color(0xFF667085))
            }
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        items(rows, key = { row -> rowKey(row) }) { row ->
            when (row) {
                is ConversationRow.Day -> DaySeparator(row.label)
                is ConversationRow.Message -> MessageBubble(row.value, onRetry, onAction)
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun DaySeparator(label: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(color = Color(0xFFE8EDF5), shape = RoundedCornerShape(16.dp)) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                color = Color(0xFF667085),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    onRetry: (String) -> Unit,
    onAction: (String, RichAction) -> Unit
) {
    if (message.direction == MessageDirection.System) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Surface(color = Color(0xFFE8EDF5), shape = RoundedCornerShape(18.dp)) {
                Text(
                    message.text,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    color = Color(0xFF475467),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        return
    }

    val outbound = message.direction == MessageDirection.Outbound
    val bubbleColor = if (outbound) Color(0xFF0A84FF) else Color.White
    val textColor = if (outbound) Color.White else Color(0xFF101828)
    val metaColor = if (outbound) Color(0xCCEAF2FF) else Color(0xFF667085)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (outbound) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (outbound) 20.dp else 6.dp,
                bottomEnd = if (outbound) 6.dp else 20.dp
            ),
            shadowElevation = if (outbound) 0.dp else 1.dp,
            modifier = Modifier.widthIn(max = 312.dp)
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(message.text, color = textColor)
                Spacer(Modifier.height(4.dp))
                Text(
                    metaLabel(message),
                    color = metaColor,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        if (message.status == MessageStatus.Failed) {
            TextButton(onClick = { onRetry(message.id) }) { Text("Retry") }
        }
        if (!outbound && message.actions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .widthIn(max = 312.dp)
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
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

@Composable
private fun Composer(backdrop: LayerBackdrop, sending: Boolean, initialText: String, onSend: (String) -> Unit) {
    var text by rememberSaveable(initialText) { mutableStateOf(initialText) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LiquidGlassSurface(
            backdrop = backdrop,
            modifier = Modifier.weight(1f),
            cornerRadius = 34.dp
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                maxLines = 5,
                shape = RoundedCornerShape(24.dp),
                placeholder = { Text("Message Poke") }
            )
        }
        LiquidGlassSurface(
            backdrop = backdrop,
            modifier = Modifier
                .width(92.dp)
                .height(64.dp)
                .then(
                    if (text.isNotBlank() && !sending) {
                        Modifier.clickable {
                            val outbound = text
                            text = ""
                            onSend(outbound)
                        }
                    } else {
                        Modifier
                    }
                ),
            cornerRadius = 32.dp
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (sending) "..." else "Send",
                    color = Color(0xFF344054).copy(alpha = if (text.isNotBlank() && !sending) 1f else 0.58f),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SetupSheet(
    backdrop: LayerBackdrop,
    settings: AppSettings,
    onSave: (AppSettings) -> Unit,
    onUseLocal: () -> Unit
) {
    var backendUrl by rememberSaveable(settings.backendBaseUrl) { mutableStateOf(settings.backendBaseUrl) }
    var pokeUserId by rememberSaveable(settings.pokeUserId) { mutableStateOf(settings.pokeUserId) }

    LiquidGlassSurface(
        backdrop = backdrop,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        cornerRadius = 34.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Connect Poke", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = backendUrl,
                onValueChange = { backendUrl = it },
                label = { Text("Backend URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = pokeUserId,
                onValueChange = { pokeUserId = it },
                label = { Text("Poke user ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSave(AppSettings(backendUrl.trim(), pokeUserId.trim())) }) {
                    Text("Save")
                }
                TextButton(onClick = onUseLocal) {
                    Text("Local QA")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LiquidGlassSurface(
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    cornerRadius: Dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(cornerRadius) },
                effects = {
                    vibrancy()
                    blur(2.dp.toPx())
                    lens(22.dp.toPx(), 46.dp.toPx(), depthEffect = true, chromaticAberration = true)
                },
                highlight = { Highlight.Default },
                onDrawSurface = {
                    drawRect(Color.White.copy(alpha = 0.18f))
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(Color.White.copy(alpha = 0.42f), Color.Transparent),
                            start = Offset.Zero,
                            end = Offset(size.width * 0.7f, size.height * 0.7f)
                        )
                    )
                }
            )
            .drawWithContent {
                drawContent()
                val radius = cornerRadius.toPx()
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.White.copy(alpha = 0.82f), Color.Transparent),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    ),
                    cornerRadius = CornerRadius(radius, radius),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6.dp.toPx())
                )
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.10f),
                    cornerRadius = CornerRadius(radius, radius),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.8.dp.toPx())
                )
            },
        content = content
    )
}

private fun conversationRows(messages: List<ChatMessage>): List<ConversationRow> {
    val rows = mutableListOf<ConversationRow>()
    var lastTime: Long? = null
    messages.sortedBy { it.createdAt }.forEach { message ->
        val previousTime = lastTime
        if (previousTime == null || !sameDay(previousTime, message.createdAt)) {
            rows += ConversationRow.Day(formatDay(message.createdAt))
        }
        rows += ConversationRow.Message(message)
        lastTime = message.createdAt
    }
    return rows
}

private fun rowKey(row: ConversationRow): String = when (row) {
    is ConversationRow.Day -> "day-${row.label}"
    is ConversationRow.Message -> row.value.id
}

private fun metaLabel(message: ChatMessage): String {
    val time = formatTime(message.createdAt)
    val status = when (message.status) {
        MessageStatus.Sending -> "sending"
        MessageStatus.Sent -> "sent"
        MessageStatus.Failed -> "failed"
        MessageStatus.Received -> "received"
    }
    return if (message.direction == MessageDirection.Outbound) "$time - $status" else time
}

private fun formatDay(time: Long): String =
    SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(time))

private fun formatTime(time: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(time))

private fun sameDay(a: Long, b: Long): Boolean {
    val first = Calendar.getInstance().apply { timeInMillis = a }
    val second = Calendar.getInstance().apply { timeInMillis = b }
    return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
        first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
}
