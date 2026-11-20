package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ReminderEntity
import com.example.data.TaskEntity
import com.example.ui.AssistantViewModel
import com.example.ui.theme.AuraCobalt
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.SemanticSuccess
import com.example.ui.theme.SemanticWarning
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

private enum class AgendaFilter(val label: String) {
    ALL("All"),
    TASKS("Tasks"),
    REMINDERS("Reminders")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksRemindersScreen(
    viewModel: AssistantViewModel,
    modifier: Modifier = Modifier
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()

    var filter by remember { mutableStateOf(AgendaFilter.ALL) }
    var showAddSheet by remember { mutableStateOf(false) }

    val pendingCount = tasks.count { !it.isCompleted }
    val activeReminderCount = reminders.count { !it.isDismissed }

    Column(modifier = modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Tasks & Reminders",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Text(
                    text = "$pendingCount pending  ·  $activeReminderCount reminders",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }

            Surface(
                color = AuraCobalt,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(44.dp)
                    .clickable { showAddSheet = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Add",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }
            }
        }

        // Segmented filter tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .background(ObsidianSurface, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AgendaFilter.entries.forEach { tab ->
                val selected = tab == filter
                Surface(
                    color = if (selected) ObsidianSurfaceElevated else Color.Transparent,
                    shape = RoundedCornerShape(9.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clickable { filter = tab }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) TextPrimary else TextSecondary
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            color = ObsidianBorder,
            modifier = Modifier.padding(top = 8.dp)
        )

        val showReminders = filter == AgendaFilter.ALL || filter == AgendaFilter.REMINDERS
        val showTasks = filter == AgendaFilter.ALL || filter == AgendaFilter.TASKS
        val isEmpty = (!showReminders || reminders.isEmpty()) && (!showTasks || tasks.isEmpty())

        if (isEmpty) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Nothing scheduled yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap + Add above, or ask Aura in the Assistant tab to add tasks and reminders.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (showReminders && reminders.isNotEmpty()) {
                    item(key = "header_reminders") {
                        Text(
                            text = "Reminders",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMuted
                        )
                    }
                    items(reminders, key = { "rem_${it.id}" }) { reminder ->
                        ReminderRow(
                            reminder = reminder,
                            onDismiss = { viewModel.dismissReminder(reminder) }
                        )
                    }
                    if (showTasks && tasks.isNotEmpty()) {
                        item(key = "spacer_mid") { Spacer(modifier = Modifier.height(8.dp)) }
                    }
                }

                if (showTasks && tasks.isNotEmpty()) {
                    item(key = "header_tasks") {
                        Text(
                            text = "Daily Tasks",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMuted
                        )
                    }
                    items(tasks, key = { "task_${it.id}" }) { task ->
                        TaskRow(
                            task = task,
                            onToggle = { viewModel.toggleTaskCompleted(task) },
                            onDelete = { viewModel.deleteTask(task) }
                        )
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = sheetState,
            containerColor = ObsidianSurface,
            contentColor = TextPrimary
        ) {
            AddAgendaSheetContent(
                onAddTask = { title, category, priority, due ->
                    viewModel.addTaskManually(title, category, priority, due)
                    showAddSheet = false
                },
                onAddReminder = { title, mins ->
                    viewModel.addReminderManually(title, mins)
                    showAddSheet = false
                }
            )
        }
    }
}

@Composable
private fun ReminderRow(
    reminder: ReminderEntity,
    onDismiss: () -> Unit
) {
    Surface(
        color = ObsidianSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = SemanticWarning.copy(alpha = 0.14f),
                shape = CircleShape,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (reminder.isFired) {
                            Icons.Rounded.NotificationsActive
                        } else {
                            Icons.Rounded.Alarm
                        },
                        contentDescription = null,
                        tint = SemanticWarning,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val statusText = if (reminder.isFired) "Triggered" else "Scheduled"
                Text(
                    text = "$statusText  ·  ${reminder.timeLabel}",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Dismiss reminder",
                    tint = TextMuted
                )
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: TaskEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = ObsidianSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = if (task.isCompleted) SemanticSuccess else Color.Transparent,
                    shape = CircleShape,
                    border = BorderStroke(
                        width = 1.5.dp,
                        color = if (task.isCompleted) SemanticSuccess else TextMuted
                    ),
                    modifier = Modifier.size(24.dp)
                ) {
                    if (task.isCompleted) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Completed",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (task.isCompleted) TextMuted else TextPrimary,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${task.category}  ·  ${task.dueLabel}  ·  ${task.priority}",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete task",
                    tint = TextMuted
                )
            }
        }
    }
}

@Composable
private fun AddAgendaSheetContent(
    onAddTask: (title: String, category: String, priority: String, due: String) -> Unit,
    onAddReminder: (title: String, delayMinutes: Int) -> Unit
) {
    var isReminderMode by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Personal") }
    var priority by remember { mutableStateOf("Normal") }
    var delayMinutes by remember { mutableIntStateOf(15) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianSurfaceElevated, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(false to "New Task", true to "New Reminder").forEach { (mode, label) ->
                val selected = isReminderMode == mode
                Surface(
                    color = if (selected) AuraCobalt else Color.Transparent,
                    shape = RoundedCornerShape(9.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clickable { isReminderMode = mode }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) Color.White else TextSecondary
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(if (isReminderMode) "Remind me to..." else "Task title") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AuraCobalt,
                unfocusedBorderColor = ObsidianBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier.fillMaxWidth()
        )

        if (isReminderMode) {
            Text(
                text = "Trigger after",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(5 to "5m", 15 to "15m", 30 to "30m", 60 to "1h").forEach { (mins, label) ->
                    val selected = delayMinutes == mins
                    Surface(
                        color = if (selected) AuraCobalt else ObsidianSurfaceElevated,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clickable { delayMinutes = mins }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) Color.White else TextPrimary
                            )
                        }
                    }
                }
            }
        } else {
            Text(
                text = "Priority",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("High", "Normal", "Low").forEach { level ->
                    val selected = priority == level
                    Surface(
                        color = if (selected) AuraCobalt else ObsidianSurfaceElevated,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clickable { priority = level }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = level,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) Color.White else TextPrimary
                            )
                        }
                    }
                }
            }

            Text(
                text = "Category",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Personal", "Work", "Health", "Errands").forEach { cat ->
                    val selected = category == cat
                    Surface(
                        color = if (selected) ObsidianSurfaceElevated else Color.Transparent,
                        border = BorderStroke(1.dp, if (selected) AuraCobalt else ObsidianBorder),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clickable { category = cat }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = cat,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) TextPrimary else TextSecondary
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                if (title.isNotBlank()) {
                    if (isReminderMode) {
                        onAddReminder(title, delayMinutes)
                    } else {
                        onAddTask(title, category, priority, "Today")
                    }
                }
            },
            enabled = title.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = AuraCobalt),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text(
                text = if (isReminderMode) "Schedule Reminder" else "Save Task",
                color = Color.White
            )
        }
    }
}
