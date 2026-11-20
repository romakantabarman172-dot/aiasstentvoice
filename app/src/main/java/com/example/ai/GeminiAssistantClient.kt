package com.example.ai

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ExecutedToolCard(
    val toolName: String,
    val title: String,
    val subtitle: String,
    val success: Boolean,
    val actionTarget: String? = null,
    val extraPayload: String? = null
)

data class ToolExecutionOutcome(
    val resultJson: JSONObject,
    val card: ExecutedToolCard? = null
)

data class AssistantTurnResult(
    val replyText: String,
    val executedCards: List<ExecutedToolCard>
)

data class ChatTurn(
    val isUser: Boolean,
    val text: String
)

class GeminiAssistantClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun processCommand(
        userMessage: String,
        history: List<ChatTurn>,
        activeTasksSummary: String,
        activeRemindersSummary: String,
        toolExecutor: suspend (name: String, args: JSONObject) -> ToolExecutionOutcome
    ): AssistantTurnResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException(
                "Gemini API key is missing. Add GEMINI_API_KEY in Settings > Secrets and rebuild."
            )
        }

        val nowLabel = SimpleDateFormat("EEEE, MMM d, yyyy 'at' h:mm a", Locale.getDefault())
            .format(Date())

        val systemPrompt = """
            You are Aura, a warm, fast, Siri-like voice AI personal assistant running natively on Android.
            Current local date and time: $nowLabel.
            
            Language rule:
            - Understand English, Hindi, and Hinglish (Romanized Hindi + English, e.g., "kal subah 8 baje reminder lagao", "Mom ko call karo", "call uthao", "WhatsApp kholo", "message bhejo") fluently.
            - Respond in the same language/style the user used (natural Hinglish if they spoke Hinglish, English if they spoke English).
            - Keep spoken responses concise (1-2 crisp sentences), friendly, and direct—like Siri. Never use markdown asterisks or bullet lists in voice replies.
            
            User's current tasks:
            $activeTasksSummary
            
            User's active reminders:
            $activeRemindersSummary
            
            Tool rules:
            - Always call the appropriate tool immediately whenever the user wants to add/complete/delete a task, set a reminder, make a call, answer/decline/end a call, send a message, open an app, or toggle flashlight/settings.
            - For set_reminder: compute delayMinutes accurately relative to current time ($nowLabel). Minimum delayMinutes is 1. Always provide a human-friendly timeLabel (e.g., "Today, 6:30 PM" or "In 15 mins").
            - For manage_call: map "call uthao" / "answer call" / "pick up" -> "answer"; "call kaato" / "decline" / "hang up" / "end call" -> "decline".
            - After executing tools, summarize the actual tool outcome truthfully. If a tool failed or needs permission, explain clearly in one sentence.
        """.trimIndent()

        val contents = JSONArray()
        history.takeLast(8).forEach { turn ->
            contents.put(
                JSONObject().apply {
                    put("role", if (turn.isUser) "user" else "model")
                    put("parts", JSONArray().put(JSONObject().put("text", turn.text)))
                }
            )
        }
        contents.put(
            JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", userMessage)))
            }
        )

        val tools = buildToolDeclarations()
        val executedCards = mutableListOf<ExecutedToolCard>()

        var loopCount = 0
        var finalReply = ""

        while (loopCount < 3) {
            loopCount++
            val requestBody = JSONObject().apply {
                put(
                    "systemInstruction",
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                )
                put("contents", contents)
                put("tools", tools)
            }

            val responseJson = callGeminiEndpoint(apiKey, requestBody)
            val candidates = responseJson.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                throw IllegalStateException("Aura received an empty response from Gemini.")
            }

            val modelContent = candidates.getJSONObject(0).optJSONObject("content")
                ?: throw IllegalStateException("Malformed response from Gemini.")
            val parts = modelContent.optJSONArray("parts") ?: JSONArray()

            val functionCalls = mutableListOf<JSONObject>()
            val textBuilder = StringBuilder()

            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (part.has("functionCall")) {
                    functionCalls.add(part.getJSONObject("functionCall"))
                } else if (part.has("text")) {
                    val t = part.optString("text", "")
                    if (t.isNotBlank()) {
                        if (textBuilder.isNotEmpty()) textBuilder.append(" ")
                        textBuilder.append(t.trim())
                    }
                }
            }

            if (functionCalls.isEmpty()) {
                finalReply = textBuilder.toString().trim()
                break
            }

            // Preserve full model turn (including any thoughtSignature) before appending functionResponse
            contents.put(modelContent)

            val responseParts = JSONArray()
            for (call in functionCalls) {
                val fnName = call.getString("name")
                val fnArgs = call.optJSONObject("args") ?: JSONObject()
                val outcome = toolExecutor(fnName, fnArgs)
                outcome.card?.let { executedCards.add(it) }

                responseParts.put(
                    JSONObject().apply {
                        put(
                            "functionResponse",
                            JSONObject().apply {
                                put("name", fnName)
                                put("response", outcome.resultJson)
                            }
                        )
                    }
                )
            }

            contents.put(
                JSONObject().apply {
                    put("role", "user")
                    put("parts", responseParts)
                }
            )
        }

        if (finalReply.isBlank()) {
            finalReply = if (executedCards.isNotEmpty()) {
                executedCards.joinToString(". ") { it.subtitle }
            } else {
                "Done."
            }
        }

        AssistantTurnResult(
            replyText = finalReply,
            executedCards = executedCards
        )
    }

    private fun callGeminiEndpoint(apiKey: String, payload: JSONObject): JSONObject {
        val url =
            "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .header("Content-Type", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            val rawBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val errMsg = try {
                    JSONObject(rawBody).optJSONObject("error")?.optString("message")
                } catch (_: Exception) {
                    null
                }
                throw IllegalStateException(
                    errMsg ?: "Gemini request failed with HTTP ${response.code}."
                )
            }
            return JSONObject(rawBody)
        }
    }

    private fun buildToolDeclarations(): JSONArray {
        val declarations = JSONArray()

        declarations.put(
            fn(
                name = "add_task",
                description = "Add a new daily task or to-do item for the user.",
                props = JSONObject().apply {
                    put("title", strProp("Short actionable task title"))
                    put("category", strProp("Category such as Work, Personal, Health, Shopping, Errands"))
                    put("priority", strProp("Priority: High, Normal, or Low"))
                    put("dueLabel", strProp("Human-readable due time like Today, Evening, Tomorrow"))
                },
                required = listOf("title")
            )
        )

        declarations.put(
            fn(
                name = "complete_task",
                description = "Mark an existing task as completed.",
                props = JSONObject().apply {
                    put("taskTitle", strProp("Title or keyword of the task to mark complete"))
                },
                required = listOf("taskTitle")
            )
        )

        declarations.put(
            fn(
                name = "delete_task",
                description = "Delete an existing task from the user's list.",
                props = JSONObject().apply {
                    put("taskTitle", strProp("Title or keyword of the task to remove"))
                },
                required = listOf("taskTitle")
            )
        )

        declarations.put(
            fn(
                name = "set_reminder",
                description = "Schedule an Android reminder notification for the user.",
                props = JSONObject().apply {
                    put("title", strProp("What to remind the user about"))
                    put(
                        "delayMinutes",
                        JSONObject().apply {
                            put("type", "INTEGER")
                            put("description", "Minutes from now when the reminder should trigger (minimum 1)")
                        }
                    )
                    put("timeLabel", strProp("Readable time label, e.g., Today, 6:30 PM or In 15 mins"))
                },
                required = listOf("title", "delayMinutes", "timeLabel")
            )
        )

        declarations.put(
            fn(
                name = "make_call",
                description = "Place a phone call to a contact name or phone number.",
                props = JSONObject().apply {
                    put("contactNameOrNumber", strProp("Contact name (like Mom, Rahul) or phone number"))
                },
                required = listOf("contactNameOrNumber")
            )
        )

        declarations.put(
            fn(
                name = "manage_call",
                description = "Answer, decline, or end an incoming or active phone call.",
                props = JSONObject().apply {
                    put("action", strProp("One of: answer, decline, end, speaker"))
                },
                required = listOf("action")
            )
        )

        declarations.put(
            fn(
                name = "send_message",
                description = "Send an SMS or WhatsApp message to a contact or phone number.",
                props = JSONObject().apply {
                    put("recipient", strProp("Recipient contact name or phone number"))
                    put("message", strProp("Message body text to send"))
                    put("channel", strProp("Messaging app: sms or whatsapp"))
                },
                required = listOf("recipient", "message")
            )
        )

        declarations.put(
            fn(
                name = "open_app",
                description = "Open an installed app on the user's Android phone (e.g., WhatsApp, YouTube, Camera, Settings, Maps, Chrome, Instagram, Spotify, Gmail).",
                props = JSONObject().apply {
                    put("appName", strProp("Name of the app to open"))
                },
                required = listOf("appName")
            )
        )

        declarations.put(
            fn(
                name = "control_device",
                description = "Control device hardware or quick settings like flashlight, wifi, bluetooth, volume, alarms, camera, or settings.",
                props = JSONObject().apply {
                    put("setting", strProp("Setting name: flashlight, wifi, bluetooth, volume, alarms, camera, settings"))
                    put(
                        "enabled",
                        JSONObject().apply {
                            put("type", "BOOLEAN")
                            put("description", "True to turn on, false to turn off (used for flashlight)")
                        }
                    )
                },
                required = listOf("setting")
            )
        )

        return JSONArray().put(JSONObject().put("functionDeclarations", declarations))
    }

    private fun fn(
        name: String,
        description: String,
        props: JSONObject,
        required: List<String>
    ): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("description", description)
            put(
                "parameters",
                JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", props)
                    put("required", JSONArray(required))
                }
            )
        }
    }

    private fun strProp(description: String): JSONObject {
        return JSONObject().apply {
            put("type", "STRING")
            put("description", description)
        }
    }

    companion object {
        const val MODEL_NAME = "gemini-4-hydrogen"
    }
}
