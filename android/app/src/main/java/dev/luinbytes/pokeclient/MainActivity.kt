package dev.luinbytes.pokeclient

import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.qmdeve.liquidglass.widget.LiquidGlassView
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

private val LocalGlassSource = staticCompositionLocalOf<ViewGroup?> { null }
private val LocalDarkMode = staticCompositionLocalOf { false }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PokeApp(viewModel: PokeViewModel, intent: Intent?) {
    val settings by viewModel.settings.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var setupVisible by rememberSaveable { mutableStateOf(settings.backendBaseUrl.isBlank()) }
    var glassSource by remember { mutableStateOf<ViewGroup?>(null) }
    val darkMode = isSystemInDarkTheme()
    val view = LocalView.current

    LaunchedEffect(settings.backendBaseUrl, settings.pokeUserId) {
        if (settings.backendBaseUrl.isNotBlank() && settings.pokeUserId.isNotBlank()) {
            setupVisible = false
            viewModel.observeBackend(settings)
        }
    }

    SideEffect {
        val window = view.context.findActivity()?.window
        val lightSystemBars = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window?.statusBarColor = AndroidColor.TRANSPARENT
        window?.navigationBarColor = AndroidColor.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window?.isNavigationBarContrastEnforced = false
        }
        window?.insetsController?.setSystemBarsAppearance(
            if (darkMode) 0 else lightSystemBars,
            lightSystemBars
        )
    }

    MaterialTheme {
        CompositionLocalProvider(
            LocalGlassSource provides glassSource,
            LocalDarkMode provides darkMode
        ) {
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.matchParentSize(),
                    factory = { context ->
                        FrameLayout(context).apply {
                            setGlassBackground(darkMode)
                            addView(
                                GlassBackdropView(context).apply { this.darkMode = darkMode },
                                FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.MATCH_PARENT
                                )
                            )
                            glassSource = this
                        }
                    },
                    update = {
                        it.setGlassBackground(darkMode)
                        (it.getChildAt(0) as? GlassBackdropView)?.darkMode = darkMode
                        glassSource = it
                    }
                )
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        ChatHeader(
                            status = uiState.status,
                            connected = uiState.backendConnected,
                            onSetup = { setupVisible = true },
                            onClear = viewModel::clearConversation
                        )
                    },
                    bottomBar = {
                        Composer(
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
                        containerColor = Color.Transparent,
                        scrimColor = Color.Black.copy(alpha = 0.10f)
                    ) {
                        SetupSheet(
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
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun ChatHeader(
    status: String,
    connected: Boolean,
    onSetup: () -> Unit,
    onClear: () -> Unit
) {
    val primaryText = appPrimaryText()
    val secondaryText = appSecondaryText()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LiquidGlassSurface(
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
                    Text("Poke", fontWeight = FontWeight.Bold, color = primaryText)
                    Text(
                        status,
                        color = secondaryText,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        LiquidGlassSurface(cornerRadius = 28.dp) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onClear) { Text("Clear", color = primaryText) }
                TextButton(onClick = onSetup) { Text("Setup", color = primaryText) }
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
        val primaryText = appPrimaryText()
        val secondaryText = appSecondaryText()
        Box(modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No messages yet", fontWeight = FontWeight.Bold, color = primaryText)
                Text("Send a message when Poke is connected.", color = secondaryText)
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
private fun Composer(sending: Boolean, initialText: String, onSend: (String) -> Unit) {
    var text by rememberSaveable(initialText) { mutableStateOf(initialText) }
    val primaryText = appPrimaryText()
    val secondaryText = appSecondaryText()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LiquidGlassSurface(
            modifier = Modifier.weight(1f),
            cornerRadius = 34.dp
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = primaryText),
                minLines = 1,
                maxLines = 5,
                decorationBox = { innerTextField ->
                    if (text.isBlank()) {
                        Text("Message Poke", color = secondaryText, style = MaterialTheme.typography.bodyLarge)
                    }
                    innerTextField()
                }
            )
        }
        LiquidGlassSurface(
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
                    color = primaryText.copy(alpha = if (text.isNotBlank() && !sending) 1f else 0.58f),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SetupSheet(
    settings: AppSettings,
    onSave: (AppSettings) -> Unit,
    onUseLocal: () -> Unit
) {
    var backendUrl by rememberSaveable(settings.backendBaseUrl) { mutableStateOf(settings.backendBaseUrl) }
    var pokeUserId by rememberSaveable(settings.pokeUserId) { mutableStateOf(settings.pokeUserId) }
    val primaryText = appPrimaryText()
    val secondaryText = appSecondaryText()

    LiquidGlassSurface(
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
            Text("Connect Poke", color = primaryText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            SetupGlassTextField(
                value = backendUrl,
                onValueChange = { backendUrl = it },
                placeholder = "Backend URL",
                primaryText = primaryText,
                secondaryText = secondaryText
            )
            SetupGlassTextField(
                value = pokeUserId,
                onValueChange = { pokeUserId = it },
                placeholder = "Poke user ID",
                primaryText = primaryText,
                secondaryText = secondaryText
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SetupGlassButton(onClick = { onSave(AppSettings(backendUrl.trim(), pokeUserId.trim())) }) {
                    Text("Save", color = primaryText, fontWeight = FontWeight.SemiBold)
                }
                SetupGlassButton(onClick = onUseLocal) {
                    Text("Local QA", color = primaryText, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SetupGlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    primaryText: Color,
    secondaryText: Color
) {
    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        cornerRadius = 18.dp
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = primaryText),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    if (value.isBlank()) {
                        Text(placeholder, color = secondaryText, style = MaterialTheme.typography.bodyLarge)
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
private fun SetupGlassButton(
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    LiquidGlassSurface(
        modifier = Modifier
            .height(48.dp)
            .width(124.dp)
            .clickable(onClick = onClick),
        cornerRadius = 24.dp,
        content = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
                content = content
            )
        }
    )
}

@Composable
private fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp,
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    val source = LocalGlassSource.current
    val darkMode = LocalDarkMode.current
    val style = if (darkMode) DarkLiquidGlassStyle else LightLiquidGlassStyle
    val cornerPx = with(density) { cornerRadius.toPx() }
    val refractionHeightPx = with(density) { style.refractionHeight.toPx() }
    val refractionOffsetPx = with(density) { style.refractionOffset.toPx() }
    val blurRadiusPx = with(density) { style.blurRadius.toPx() }

    Box(
        modifier
            .graphicsLayer {
                shadowElevation = 18.dp.toPx()
                shape = RoundedCornerShape(cornerRadius)
                clip = false
                ambientShadowColor = Color.Black.copy(alpha = 0.12f)
                spotShadowColor = Color.Black.copy(alpha = 0.16f)
            }
            .drawWithContent {
                drawContent()
                val radius = cornerRadius.toPx()
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = style.edgeShineAlpha),
                            Color.White.copy(alpha = style.edgeMidAlpha),
                            Color.Transparent
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    ),
                    cornerRadius = CornerRadius(radius, radius),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.65.dp.toPx())
                )
                drawRoundRect(
                    color = Color.Black.copy(alpha = style.edgeShadowAlpha),
                    cornerRadius = CornerRadius(radius, radius),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.45.dp.toPx())
                )
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = style.innerShineAlpha),
                            Color.Transparent,
                            Color.Black.copy(alpha = style.innerShadowAlpha)
                        ),
                        start = Offset(size.width * 0.08f, 0f),
                        end = Offset(size.width * 0.90f, size.height)
                    ),
                    cornerRadius = CornerRadius(radius, radius)
                )
            },
    ) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { context ->
                LiquidGlassView(context).apply {
                    configureQmGlass(source, cornerPx, refractionHeightPx, refractionOffsetPx, blurRadiusPx, style)
                }
            },
            update = {
                it.configureQmGlass(source, cornerPx, refractionHeightPx, refractionOffsetPx, blurRadiusPx, style)
            }
        )
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Color.White.copy(alpha = style.overlayAlpha),
                    RoundedCornerShape(cornerRadius)
                )
        )
        content()
    }
}

private fun LiquidGlassView.configureQmGlass(
    source: ViewGroup?,
    cornerPx: Float,
    refractionHeightPx: Float,
    refractionOffsetPx: Float,
    blurRadiusPx: Float,
    style: LiquidGlassStyle
) {
    bind(source)
    setCornerRadius(cornerPx)
    setRefractionHeight(refractionHeightPx)
    setRefractionOffset(refractionOffsetPx)
    setBlurRadius(blurRadiusPx)
    setDispersion(style.dispersion)
    setTintAlpha(style.tintAlpha)
}

private data class LiquidGlassStyle(
    val refractionHeight: Dp,
    val refractionOffset: Dp,
    val blurRadius: Dp,
    val dispersion: Float,
    val tintAlpha: Float,
    val overlayAlpha: Float,
    val edgeShineAlpha: Float,
    val edgeMidAlpha: Float,
    val edgeShadowAlpha: Float,
    val innerShineAlpha: Float,
    val innerShadowAlpha: Float
)

private val DarkLiquidGlassStyle = LiquidGlassStyle(
    refractionHeight = 46.dp,
    refractionOffset = 124.dp,
    blurRadius = 0.01.dp,
    dispersion = 0.92f,
    tintAlpha = 0.04f,
    overlayAlpha = 0f,
    edgeShineAlpha = 0.30f,
    edgeMidAlpha = 0.08f,
    edgeShadowAlpha = 0.14f,
    innerShineAlpha = 0.10f,
    innerShadowAlpha = 0.10f
)

private val LightLiquidGlassStyle = LiquidGlassStyle(
    refractionHeight = 22.dp,
    refractionOffset = 76.dp,
    blurRadius = 8.dp,
    dispersion = 0.86f,
    tintAlpha = 0.04f,
    overlayAlpha = 0.015f,
    edgeShineAlpha = 0.28f,
    edgeMidAlpha = 0.08f,
    edgeShadowAlpha = 0.10f,
    innerShineAlpha = 0.07f,
    innerShadowAlpha = 0.04f
)

@Composable
private fun appPrimaryText(): Color =
    if (LocalDarkMode.current) Color(0xFFF6F8FF) else Color(0xFF101828)

@Composable
private fun appSecondaryText(): Color =
    if (LocalDarkMode.current) Color(0xB8E7ECF8) else Color(0xFF667085)

private fun FrameLayout.setGlassBackground(darkMode: Boolean) {
    background = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        if (darkMode) {
            intArrayOf(
                AndroidColor.rgb(5, 10, 22),
                AndroidColor.rgb(11, 18, 31),
                AndroidColor.rgb(19, 13, 22)
            )
        } else {
            intArrayOf(
                AndroidColor.rgb(248, 251, 255),
                AndroidColor.rgb(234, 249, 243),
                AndroidColor.rgb(255, 248, 250)
            )
        }
    )
}

private class GlassBackdropView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var darkMode: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        paint.shader = LinearGradient(
            0f,
            0f,
            w,
            h,
            if (darkMode) {
                intArrayOf(
                    AndroidColor.rgb(5, 10, 22),
                    AndroidColor.rgb(9, 20, 27),
                    AndroidColor.rgb(22, 13, 24)
                )
            } else {
                intArrayOf(
                    AndroidColor.rgb(248, 251, 255),
                    AndroidColor.rgb(232, 249, 241),
                    AndroidColor.rgb(255, 247, 252)
                )
            },
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)

        paint.shader = null
        if (darkMode) {
            drawBloom(canvas, w * 0.16f, h * 0.18f, w * 0.72f, AndroidColor.rgb(34, 120, 255), 70)
            drawBloom(canvas, w * 0.90f, h * 0.12f, w * 0.62f, AndroidColor.rgb(220, 68, 122), 54)
            drawBloom(canvas, w * 0.72f, h * 0.82f, w * 0.78f, AndroidColor.rgb(35, 180, 122), 50)
            drawBloom(canvas, w * 0.26f, h * 0.96f, w * 0.52f, AndroidColor.rgb(116, 92, 255), 40)
            drawBloom(canvas, w * 0.18f, h * 0.70f, w * 0.42f, AndroidColor.rgb(80, 120, 255), 42)
            drawBloom(canvas, w * 0.92f, h * 0.66f, w * 0.46f, AndroidColor.rgb(255, 110, 180), 32)
        } else {
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = 18f
            val colors = intArrayOf(
                AndroidColor.rgb(10, 132, 255),
                AndroidColor.rgb(48, 209, 88),
                AndroidColor.rgb(255, 55, 95)
            )
            for (i in -3..8) {
                paint.color = colors[Math.floorMod(i, colors.size)]
                paint.alpha = 10
                val y = i * h / 7f
                canvas.drawLine(-w * 0.2f, y, w * 1.2f, y + h * 0.34f, paint)
            }
        }
        paint.style = Paint.Style.FILL
        paint.alpha = 255
    }

    private fun drawBloom(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int, alpha: Int) {
        paint.shader = RadialGradient(
            cx,
            cy,
            radius,
            intArrayOf(
                AndroidColor.argb(alpha, AndroidColor.red(color), AndroidColor.green(color), AndroidColor.blue(color)),
                AndroidColor.TRANSPARENT
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        canvas.drawCircle(cx, cy, radius, paint)
    }
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
