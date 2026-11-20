package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Message
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.ExecutedToolCard
import com.example.ui.AssistantViewModel
import com.example.ui.ConversationEntry
import com.example.ui.components.VoiceOrb
import com.example.ui.theme.AuraCobalt
import com.example.ui.theme.AuraCyan
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.SemanticDanger
import com.example.ui.theme.SemanticSuccess
import com.example.ui.theme.SemanticWarning
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class QuickPromptItem(
    val label: String,
    val command: String
)

private val QuickPrompts = listOf(
    QuickPromptItem("10 min baad reminder", "10 minute baad paani peene ka reminder lagao"),
    QuickPromptItem("Add daily task", "Add a high priority task: Review project notes today"),
    QuickPromptItem("Open WhatsApp", "WhatsApp kholo"),
    QuickPromptItem("Call Mom", "Mom ko call karo"),
    QuickPromptItem("Answer call", "Call uthao"),
    QuickPromptItem("Flashlight on", "Flashlight on karo")
)

@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val isThinking by viewModel.isThinking.collectAsStateWithLifecycle()
    val partialTranscript by viewModel.partialTranscript.collectAsStateWithLifecycle()
    val rmsLevel by viewModel.rmsLevel.collectAsStateWithLifecycle()
    val isVoiceEnabled by viewModel.isVoiceEnabled.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    var showKeyboardInput by remember { mutableStateOf(false) }
    var typedCommand by remember { mutableStateOf("") }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceCapture()
        } else {
            showKeyboardInput = true
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, isThinking) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val pendingTaskCount = tasks.count { !it.isCompleted }
    val activeReminderCount = reminders.count { !it.isDismissed && !it.isFired }
    val todayDate = remember {
        SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date())
    }
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..20 -> "Good evening"
            else -> "Good night"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // Clean single-row top header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Aura",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Text(
                    text = "·  $todayDate",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { viewModel.toggleVoiceOutput() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isVoiceEnabled) {
                            Icons.AutoMirrored.Rounded.VolumeUp
                        } else {
                            Icons.AutoMirrored.Rounded.VolumeOff
                        },
                        contentDescription = if (isVoiceEnabled) "Mute voice reply" else "Unmute voice reply",
                        tint = if (isVoiceEnabled) TextPrimary else TextMuted
                    )
                }
                if (messages.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.clearConversation() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Clear conversation",
                            tint = TextSecondary
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = ObsidianBorder)

        // Status banner (e.g., mic hint)
        AnimatedVisibility(
            visible = statusBanner != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            statusBanner?.let { banner ->
                Surface(
                    color = ObsidianSurfaceElevated,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = banner,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearStatusBanner() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Dismiss",
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Main conversation or welcome canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = greeting,
                        style = MaterialTheme.typography.headlineLarge,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "$pendingTaskCount pending tasks  ·  $activeReminderCount active reminders",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(22.dp))
                    Text(
                        text = "Speak or type naturally in English or Hinglish. I can manage tasks, schedule reminders, answer or place calls, send messages, and open apps.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                    Text(
                        text = "Try saying",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(end = 16.dp)
                    ) {
                        items(QuickPrompts) { item ->
                            Surface(
                                color = ObsidianSurface,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, ObsidianBorder),
                                modifier = Modifier
                                    .height(44.dp)
                                    .clickable { viewModel.submitCommand(item.command) }
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(messages, key = { it.id }) { entry ->
                        ConversationBubble(
                            entry = entry,
                            onReplayAction = { card ->
                                when (card.toolName) {
                                    "open_app" -> card.actionTarget?.let { viewModel.launchAppByName(it) }
                                    "make_call" -> card.actionTarget?.let { viewModel.callDirect(it) }
                                    "send_message" -> {
                                        val target = card.actionTarget.orEmpty()
                                        val msg = card.extraPayload.orEmpty()
                                        viewModel.sendMessageDirect(target, msg, "sms")
                                    }
                                }
                            }
                        )
                    }
                    if (isThinking) {
                        item(key = "thinking_row") {
                            Surface(
                                color = ObsidianSurface,
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, ObsidianBorder)
                            ) {
                                Text(
                                    text = "Processing your request...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Live speech transcript preview
        AnimatedVisibility(visible = isListening || partialTranscript.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = partialTranscript.ifBlank { "Listening..." },
                    style = MaterialTheme.typography.bodyLarge,
                    color = AuraCyan,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Optional text composer bar
        AnimatedVisibility(visible = showKeyboardInput) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = typedCommand,
                    onValueChange = { typedCommand = it },
                    placeholder = {
                        Text(
                            text = "Ask Aura (e.g. Mom ko call karo)",
                            color = TextMuted
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ObsidianSurface,
                        unfocusedContainerColor = ObsidianSurface,
                        focusedBorderColor = AuraCobalt,
                        unfocusedBorderColor = ObsidianBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (typedCommand.isNotBlank()) {
                                viewModel.submitCommand(typedCommand)
                                typedCommand = ""
                            }
                        }
                    ),
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    color = AuraCobalt,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(48.dp)
                        .clickable {
                            if (typedCommand.isNotBlank()) {
                                viewModel.submitCommand(typedCommand)
                                typedCommand = ""
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Send command",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Ergonomic thumb-zone Voice Orb dock
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Keyboard toggle
            Surface(
                color = if (showKeyboardInput) ObsidianSurfaceElevated else ObsidianSurface,
                shape = CircleShape,
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier
                    .size(48.dp)
                    .clickable { showKeyboardInput = !showKeyboardInput }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Keyboard,
                        contentDescription = "Toggle keyboard input",
                        tint = if (showKeyboardInput) AuraCobalt else TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                VoiceOrb(
                    isListening = isListening,
                    isThinking = isThinking,
                    rmsLevel = rmsLevel,
                    onClick = {
                        if (isListening) {
                            viewModel.stopVoiceCapture()
                        } else {
                            if (viewModel.phoneController.hasPermission(Manifest.permission.RECORD_AUDIO)) {
                                viewModel.startVoiceCapture()
                            } else {
                                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    }
                )
                Text(
                    text = when {
                        isListening -> "Tap to stop"
                        isThinking -> "Working..."
                        else -> "Tap to speak"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }

            // Quick call answer button in thumb zone
            Surface(
                color = ObsidianSurface,
                shape = CircleShape,
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier
                    .size(48.dp)
                    .clickable { viewModel.manageCallDirect("answer") }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.PhoneInTalk,
                        contentDescription = "Answer incoming call",
                        tint = SemanticSuccess,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationBubble(
    entry: ConversationEntry,
    onReplayAction: (ExecutedToolCard) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (entry.isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = when {
                entry.isError -> SemanticDanger.copy(alpha = 0.14f)
                entry.isUser -> AuraCobalt.copy(alpha = 0.22f)
                else -> ObsidianSurface
            },
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(
                width = 1.dp,
                color = when {
                    entry.isError -> SemanticDanger.copy(alpha = 0.4f)
                    entry.isUser -> AuraCobalt.copy(alpha = 0.35f)
                    else -> ObsidianBorder
                }
            ),
            modifier = Modifier.fillMaxWidth(0.88f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (entry.isError) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = SemanticDanger,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(top = 2.dp)
                    )
                }
                Text(
                    text = entry.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (entry.isError) SemanticDanger else TextPrimary
                )
            }
        }

        if (entry.cards.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(0.92f)
            ) {
                entry.cards.forEach { card ->
                    ToolResultCard(card = card, onClick = { onReplayAction(card) })
                }
            }
        }
    }
}

@Composable
private fun ToolResultCard(
    card: ExecutedToolCard,
    onClick: () -> Unit
) {
    val icon: ImageVector = when (card.toolName) {
        "add_task", "complete_task", "delete_task" -> Icons.Rounded.TaskAlt
        "set_reminder" -> Icons.Rounded.Alarm
        "make_call", "manage_call" -> Icons.Rounded.Call
        "send_message" -> Icons.Rounded.Message
        "open_app" -> Icons.Rounded.Apps
        else -> Icons.Rounded.FlashlightOn
    }

    val tint = if (card.success) {
        when (card.toolName) {
            "set_reminder" -> SemanticWarning
            "make_call", "manage_call" -> SemanticSuccess
            else -> AuraCyan
        }
    } else {
        SemanticDanger
    }

    val isActionable = card.toolName in listOf("open_app", "make_call", "send_message")

    Surface(
        color = ObsidianSurfaceElevated,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isActionable) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = tint.copy(alpha = 0.14f),
                shape = CircleShape,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = card.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (card.success) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Success",
                    tint = SemanticSuccess,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
