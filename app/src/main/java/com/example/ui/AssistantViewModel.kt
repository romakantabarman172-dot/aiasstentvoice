package com.example.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.ChatTurn
import com.example.ai.ExecutedToolCard
import com.example.ai.GeminiAssistantClient
import com.example.ai.ToolExecutionOutcome
import com.example.data.ActionLogEntity
import com.example.data.AuraDatabase
import com.example.data.ReminderEntity
import com.example.data.TaskEntity
import com.example.device.DeviceActionResult
import com.example.device.InstalledAppInfo
import com.example.device.PhoneController
import com.example.device.ReminderScheduler
import com.example.device.VoiceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ConversationEntry(
    val id: Long = System.nanoTime(),
    val isUser: Boolean,
    val text: String,
    val isError: Boolean = false,
    val cards: List<ExecutedToolCard> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AuraDatabase.getInstance(application).assistantDao()
    private val geminiClient = GeminiAssistantClient()
    val phoneController = PhoneController(application)
    private val reminderScheduler = ReminderScheduler(application)
    val voiceEngine = VoiceEngine(application)

    val tasks: StateFlow<List<TaskEntity>> = dao.observeTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val reminders: StateFlow<List<ReminderEntity>> = dao.observeReminders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val actionLogs: StateFlow<List<ActionLogEntity>> = dao.observeActionLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isListening: StateFlow<Boolean> = voiceEngine.isListening
    val partialTranscript: StateFlow<String> = voiceEngine.partialTranscript
    val rmsLevel: StateFlow<Float> = voiceEngine.rmsLevel
    val isVoiceEnabled: StateFlow<Boolean> = voiceEngine.isVoiceEnabled
    val isFlashlightOn: StateFlow<Boolean> = phoneController.isFlashlightOn

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private val _messages = MutableStateFlow<List<ConversationEntry>>(emptyList())
    val messages: StateFlow<List<ConversationEntry>> = _messages.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    init {
        refreshInstalledApps()
    }

    fun refreshInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _installedApps.value = phoneController.getInstalledLauncherApps()
        }
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun startVoiceCapture() {
        _statusBanner.value = null
        voiceEngine.startListening(
            onResult = { spokenText ->
                submitCommand(spokenText)
            },
            onError = { errMsg ->
                _statusBanner.value = errMsg
            }
        )
    }

    fun stopVoiceCapture() {
        voiceEngine.stopListening()
    }

    fun toggleVoiceOutput() {
        voiceEngine.toggleVoiceOutput()
    }

    fun clearConversation() {
        voiceEngine.stopSpeaking()
        _messages.value = emptyList()
        _statusBanner.value = null
    }

    fun submitCommand(rawInput: String) {
        val text = rawInput.trim()
        if (text.isEmpty() || _isThinking.value) return

        _statusBanner.value = null
        val userEntry = ConversationEntry(isUser = true, text = text)
        _messages.update { it + userEntry }
        _isThinking.value = true

        viewModelScope.launch {
            try {
                val history = _messages.value
                    .dropLast(1)
                    .filter { !it.isError }
                    .map { ChatTurn(isUser = it.isUser, text = it.text) }

                val currentTasks = dao.getAllTasksOnce()
                val tasksSummary = if (currentTasks.isEmpty()) {
                    "No tasks yet."
                } else {
                    currentTasks.take(15).joinToString("\n") { t ->
                        val status = if (t.isCompleted) "Done" else "Pending"
                        "- [$status] ${t.title} (${t.category}, ${t.priority}, ${t.dueLabel})"
                    }
                }

                val currentReminders = dao.getActiveRemindersOnce()
                val remindersSummary = if (currentReminders.isEmpty()) {
                    "No active reminders."
                } else {
                    currentReminders.take(10).joinToString("\n") { r ->
                        "- ${r.title} at ${r.timeLabel}"
                    }
                }

                val result = geminiClient.processCommand(
                    userMessage = text,
                    history = history,
                    activeTasksSummary = tasksSummary,
                    activeRemindersSummary = remindersSummary,
                    toolExecutor = { fnName, args -> executeTool(fnName, args) }
                )

                val assistantEntry = ConversationEntry(
                    isUser = false,
                    text = result.replyText,
                    cards = result.executedCards
                )
                _messages.update { it + assistantEntry }
                voiceEngine.speak(result.replyText)
            } catch (e: Exception) {
                val errText = e.message ?: "Unable to reach Aura right now."
                _messages.update {
                    it + ConversationEntry(
                        isUser = false,
                        text = errText,
                        isError = true
                    )
                }
            } finally {
                _isThinking.value = false
            }
        }
    }

    private suspend fun executeTool(name: String, args: JSONObject): ToolExecutionOutcome {
        return when (name) {
            "add_task" -> {
                val title = args.optString("title").trim().ifEmpty { "Untitled Task" }
                val category = args.optString("category", "Personal").ifBlank { "Personal" }
                val priority = args.optString("priority", "Normal").ifBlank { "Normal" }
                val dueLabel = args.optString("dueLabel", "Today").ifBlank { "Today" }

                val id = dao.insertTask(
                    TaskEntity(
                        title = title,
                        category = category,
                        priority = priority,
                        dueLabel = dueLabel
                    )
                )
                ToolExecutionOutcome(
                    resultJson = JSONObject()
                        .put("status", "added")
                        .put("id", id)
                        .put("title", title),
                    card = ExecutedToolCard(
                        toolName = "add_task",
                        title = title,
                        subtitle = "$category · $dueLabel · $priority",
                        success = true
                    )
                )
            }

            "complete_task" -> {
                val query = args.optString("taskTitle").trim()
                val match = if (query.isNotEmpty()) dao.findTaskByTitle(query) else null
                if (match != null) {
                    dao.updateTask(match.copy(isCompleted = true))
                    ToolExecutionOutcome(
                        resultJson = JSONObject()
                            .put("status", "completed")
                            .put("title", match.title),
                        card = ExecutedToolCard(
                            toolName = "complete_task",
                            title = match.title,
                            subtitle = "Marked complete",
                            success = true
                        )
                    )
                } else {
                    ToolExecutionOutcome(
                        resultJson = JSONObject()
                            .put("status", "not_found")
                            .put("query", query),
                        card = ExecutedToolCard(
                            toolName = "complete_task",
                            title = query.ifEmpty { "Task" },
                            subtitle = "No matching task found",
                            success = false
                        )
                    )
                }
            }

            "delete_task" -> {
                val query = args.optString("taskTitle").trim()
                val match = if (query.isNotEmpty()) dao.findTaskByTitle(query) else null
                if (match != null) {
                    dao.deleteTask(match)
                    ToolExecutionOutcome(
                        resultJson = JSONObject()
                            .put("status", "deleted")
                            .put("title", match.title),
                        card = ExecutedToolCard(
                            toolName = "delete_task",
                            title = match.title,
                            subtitle = "Removed from tasks",
                            success = true
                        )
                    )
                } else {
                    ToolExecutionOutcome(
                        resultJson = JSONObject().put("status", "not_found"),
                        card = ExecutedToolCard(
                            toolName = "delete_task",
                            title = query.ifEmpty { "Task" },
                            subtitle = "No matching task found",
                            success = false
                        )
                    )
                }
            }

            "set_reminder" -> {
                val title = args.optString("title").trim().ifEmpty { "Reminder" }
                val delayMins = args.optInt("delayMinutes", 10).coerceAtLeast(1)
                val triggerAt = System.currentTimeMillis() + delayMins * 60_000L
                val formattedTime = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(triggerAt))
                val label = args.optString("timeLabel").ifBlank { formattedTime }

                val id = dao.insertReminder(
                    ReminderEntity(
                        title = title,
                        triggerAtMillis = triggerAt,
                        timeLabel = "$label ($formattedTime)"
                    )
                )
                reminderScheduler.scheduleReminder(id, title, triggerAt, formattedTime)

                ToolExecutionOutcome(
                    resultJson = JSONObject()
                        .put("status", "scheduled")
                        .put("title", title)
                        .put("triggerTime", formattedTime),
                    card = ExecutedToolCard(
                        toolName = "set_reminder",
                        title = title,
                        subtitle = "Scheduled · $label ($formattedTime)",
                        success = true
                    )
                )
            }

            "make_call" -> {
                val target = args.optString("contactNameOrNumber").trim()
                val res = phoneController.makeCall(target)
                logAction("CALL", "Call ${res.resolvedTarget ?: target}", res.message)
                ToolExecutionOutcome(
                    resultJson = JSONObject()
                        .put("success", res.success)
                        .put("detail", res.message),
                    card = ExecutedToolCard(
                        toolName = "make_call",
                        title = res.resolvedTarget ?: target,
                        subtitle = res.message,
                        success = res.success,
                        actionTarget = target
                    )
                )
            }

            "manage_call" -> {
                val action = args.optString("action", "answer")
                val res = phoneController.manageCall(action)
                logAction("CALL_CONTROL", "Call: ${action.replaceFirstChar { it.uppercase() }}", res.message)
                ToolExecutionOutcome(
                    resultJson = JSONObject()
                        .put("success", res.success)
                        .put("detail", res.message),
                    card = ExecutedToolCard(
                        toolName = "manage_call",
                        title = "Call Control · ${action.replaceFirstChar { it.uppercase() }}",
                        subtitle = res.message,
                        success = res.success
                    )
                )
            }

            "send_message" -> {
                val recipient = args.optString("recipient").trim()
                val msg = args.optString("message").trim()
                val channel = args.optString("channel", "sms").ifBlank { "sms" }
                val res = phoneController.sendMessage(recipient, msg, channel)
                logAction("MESSAGE", "Message to ${res.resolvedTarget ?: recipient}", msg)
                ToolExecutionOutcome(
                    resultJson = JSONObject()
                        .put("success", res.success)
                        .put("detail", res.message),
                    card = ExecutedToolCard(
                        toolName = "send_message",
                        title = "To ${res.resolvedTarget ?: recipient}",
                        subtitle = msg,
                        success = res.success,
                        actionTarget = recipient,
                        extraPayload = msg
                    )
                )
            }

            "open_app" -> {
                val appName = args.optString("appName").trim()
                val res = phoneController.openApp(appName)
                logAction("OPEN_APP", "Open ${res.resolvedTarget ?: appName}", res.message)
                ToolExecutionOutcome(
                    resultJson = JSONObject()
                        .put("success", res.success)
                        .put("detail", res.message),
                    card = ExecutedToolCard(
                        toolName = "open_app",
                        title = res.resolvedTarget ?: appName,
                        subtitle = res.message,
                        success = res.success,
                        actionTarget = appName
                    )
                )
            }

            "control_device" -> {
                val setting = args.optString("setting").trim().lowercase()
                val enabled = args.optBoolean("enabled", true)
                val res: DeviceActionResult = if (setting == "flashlight" || setting == "torch") {
                    phoneController.toggleFlashlight(enabled)
                } else {
                    phoneController.openSystemPanel(setting)
                }
                logAction("DEVICE", setting.replaceFirstChar { it.uppercase() }, res.message)
                ToolExecutionOutcome(
                    resultJson = JSONObject()
                        .put("success", res.success)
                        .put("detail", res.message),
                    card = ExecutedToolCard(
                        toolName = "control_device",
                        title = setting.replaceFirstChar { it.uppercase() },
                        subtitle = res.message,
                        success = res.success
                    )
                )
            }

            else -> ToolExecutionOutcome(
                resultJson = JSONObject().put("error", "Unknown tool $name")
            )
        }
    }

    private suspend fun logAction(type: String, title: String, subtitle: String) {
        dao.insertActionLog(
            ActionLogEntity(
                actionType = type,
                title = title,
                subtitle = subtitle
            )
        )
    }

    // Manual Tasks & Reminders operations
    fun addTaskManually(title: String, category: String, priority: String, dueLabel: String) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            dao.insertTask(
                TaskEntity(
                    title = clean,
                    category = category,
                    priority = priority,
                    dueLabel = dueLabel
                )
            )
        }
    }

    fun toggleTaskCompleted(task: TaskEntity) {
        viewModelScope.launch {
            dao.updateTask(task.copy(isCompleted = !task.isCompleted))
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            dao.deleteTask(task)
        }
    }

    fun addReminderManually(title: String, delayMinutes: Int) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        val safeMins = delayMinutes.coerceAtLeast(1)
        val triggerAt = System.currentTimeMillis() + safeMins * 60_000L
        val formattedTime = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(triggerAt))
        val relative = when {
            safeMins < 60 -> "In $safeMins min"
            safeMins == 60 -> "In 1 hr"
            else -> "In ${safeMins / 60}h ${safeMins % 60}m"
        }
        viewModelScope.launch {
            val id = dao.insertReminder(
                ReminderEntity(
                    title = clean,
                    triggerAtMillis = triggerAt,
                    timeLabel = "$relative · $formattedTime"
                )
            )
            reminderScheduler.scheduleReminder(id, clean, triggerAt, formattedTime)
        }
    }

    fun dismissReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            reminderScheduler.cancelReminder(reminder.id)
            dao.dismissReminder(reminder.id)
        }
    }

    // Direct Phone Hub operations
    fun launchAppByPackage(app: InstalledAppInfo) {
        val launchIntent = getApplication<Application>().packageManager
            .getLaunchIntentForPackage(app.packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            getApplication<Application>().startActivity(launchIntent)
            viewModelScope.launch {
                logAction("OPEN_APP", "Opened ${app.label}", app.packageName)
            }
        }
    }

    fun launchAppByName(name: String) {
        val res = phoneController.openApp(name)
        _statusBanner.value = res.message
        viewModelScope.launch {
            logAction("OPEN_APP", "Open $name", res.message)
        }
    }

    fun toggleFlashlightDirect() {
        val target = !phoneController.isFlashlightOn.value
        val res = phoneController.toggleFlashlight(target)
        _statusBanner.value = res.message
        viewModelScope.launch {
            logAction("DEVICE", "Flashlight", res.message)
        }
    }

    fun openSystemPanelDirect(setting: String) {
        val res = phoneController.openSystemPanel(setting)
        _statusBanner.value = res.message
    }

    fun callDirect(target: String) {
        val res = phoneController.makeCall(target)
        _statusBanner.value = res.message
        viewModelScope.launch {
            logAction("CALL", "Call ${res.resolvedTarget ?: target}", res.message)
        }
    }

    fun manageCallDirect(action: String) {
        val res = phoneController.manageCall(action)
        _statusBanner.value = res.message
        viewModelScope.launch {
            logAction("CALL_CONTROL", "Call $action", res.message)
        }
    }

    fun sendMessageDirect(recipient: String, body: String, channel: String) {
        val res = phoneController.sendMessage(recipient, body, channel)
        _statusBanner.value = res.message
        viewModelScope.launch {
            logAction("MESSAGE", "Message $recipient", body)
        }
    }

    fun clearActionLogs() {
        viewModelScope.launch {
            dao.clearActionLogs()
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceEngine.release()
    }
}
