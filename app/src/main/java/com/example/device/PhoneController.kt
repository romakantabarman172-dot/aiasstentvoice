package com.example.device

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class InstalledAppInfo(
    val label: String,
    val packageName: String
)

data class ContactMatch(
    val name: String,
    val number: String
)

data class DeviceActionResult(
    val success: Boolean,
    val message: String,
    val resolvedTarget: String? = null
)

class PhoneController(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val _isFlashlightOn = MutableStateFlow(false)
    val isFlashlightOn: StateFlow<Boolean> = _isFlashlightOn.asStateFlow()

    private var torchCallbackRegistered = false

    init {
        registerTorchCallback()
    }

    private fun registerTorchCallback() {
        if (torchCallbackRegistered || cameraManager == null) return
        try {
            cameraManager.registerTorchCallback(
                object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                        _isFlashlightOn.value = enabled
                    }
                },
                null
            )
            torchCallbackRegistered = true
        } catch (_: Exception) {
        }
    }

    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun findContact(query: String): ContactMatch? {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) return null
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null

        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$trimmed%")

        return try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) else trimmed
                    val number = if (numIdx >= 0) cursor.getString(numIdx) else ""
                    if (number.isNotBlank()) ContactMatch(name, number) else null
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    fun makeCall(contactNameOrNumber: String): DeviceActionResult {
        val raw = contactNameOrNumber.trim()
        if (raw.isEmpty()) {
            return DeviceActionResult(false, "No contact name or phone number provided.")
        }

        val isDigits = raw.replace(Regex("[+\\s\\-()]"), "").all { it.isDigit() } &&
            raw.any { it.isDigit() }

        val resolvedContact = if (!isDigits) findContact(raw) else null
        val targetNumber = resolvedContact?.number ?: if (isDigits) raw else null
        val displayLabel = resolvedContact?.name ?: raw

        if (targetNumber == null) {
            // Open the phone dialer so user can pick the contact if READ_CONTACTS isn't granted or match wasn't found
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            return try {
                context.startActivity(dialIntent)
                val hint = if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
                    "Opened Dialer. Grant Contacts permission in Phone Hub so Aura can auto-dial '$raw'."
                } else {
                    "Could not find '$raw' in Contacts. Opened Phone Dialer."
                }
                DeviceActionResult(true, hint, displayLabel)
            } catch (e: Exception) {
                DeviceActionResult(false, "Unable to open Phone Dialer: ${e.message}")
            }
        }

        val cleanNumber = targetNumber.replace(Regex("[^+0-9*#]"), "")
        val canDirectCall = hasPermission(Manifest.permission.CALL_PHONE)
        val intent = Intent(
            if (canDirectCall) Intent.ACTION_CALL else Intent.ACTION_DIAL,
            Uri.parse("tel:$cleanNumber")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            if (canDirectCall) {
                DeviceActionResult(true, "Calling $displayLabel ($cleanNumber)", displayLabel)
            } else {
                DeviceActionResult(true, "Dialer ready for $displayLabel ($cleanNumber)", displayLabel)
            }
        } catch (e: Exception) {
            DeviceActionResult(false, "Could not place call: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun manageCall(action: String): DeviceActionResult {
        val normalized = action.trim().lowercase()
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            ?: return DeviceActionResult(false, "Telecom service is unavailable on this device.")

        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val hasPhoneState = hasPermission(Manifest.permission.READ_PHONE_STATE)
        val hasAnswerPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            hasPermission(Manifest.permission.ANSWER_PHONE_CALLS)
        } else {
            true
        }

        val callState = if (hasPhoneState && telephonyManager != null) {
            @Suppress("DEPRECATION")
            telephonyManager.callState
        } else {
            TelephonyManager.CALL_STATE_IDLE
        }

        return when (normalized) {
            "answer", "accept", "pickup", "uthao" -> {
                if (!hasAnswerPerm) {
                    return DeviceActionResult(
                        false,
                        "Answer Phone Calls permission is needed. Tap Grant in Phone Hub to enable hands-free call answering."
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (hasPhoneState && callState != TelephonyManager.CALL_STATE_RINGING) {
                        return DeviceActionResult(
                            false,
                            "No incoming call is currently ringing on the device."
                        )
                    }
                    try {
                        @Suppress("DEPRECATION")
                        telecomManager.acceptRingingCall()
                        DeviceActionResult(true, "Answered the incoming call.")
                    } catch (e: Exception) {
                        DeviceActionResult(false, "Could not answer call: ${e.message ?: "No active ringing call"}")
                    }
                } else {
                    DeviceActionResult(false, "Hands-free call answering requires Android 8.0 or higher.")
                }
            }

            "decline", "reject", "end", "hangup", "kaato" -> {
                if (!hasAnswerPerm) {
                    return DeviceActionResult(
                        false,
                        "Answer Phone Calls permission is needed to decline or end calls."
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try {
                        @Suppress("DEPRECATION")
                        val ended = telecomManager.endCall()
                        if (ended) {
                            DeviceActionResult(true, "Call ended.")
                        } else {
                            DeviceActionResult(false, "No active or ringing call to end right now.")
                        }
                    } catch (e: Exception) {
                        DeviceActionResult(false, "Could not end call: ${e.message}")
                    }
                } else {
                    DeviceActionResult(false, "Ending calls programmatically requires Android 9 or higher.")
                }
            }

            "speaker" -> {
                openSystemPanel("volume")
            }

            else -> DeviceActionResult(false, "Unsupported call action: $action")
        }
    }

    fun sendMessage(recipient: String, message: String, channel: String = "sms"): DeviceActionResult {
        val cleanRecipient = recipient.trim()
        val contact = if (cleanRecipient.isNotEmpty()) findContact(cleanRecipient) else null
        val number = contact?.number?.replace(Regex("[^+0-9]"), "")
            ?: cleanRecipient.takeIf { it.any(Char::isDigit) }?.replace(Regex("[^+0-9]"), "")
        val displayLabel = contact?.name ?: cleanRecipient.ifEmpty { "Recipient" }

        val useWhatsApp = channel.lowercase().contains("whatsapp") || channel.lowercase().contains("wa")

        if (useWhatsApp) {
            if (!number.isNullOrBlank()) {
                val waDigits = number.removePrefix("+")
                val waUri = Uri.parse("https://wa.me/$waDigits?text=${Uri.encode(message)}")
                val waIntent = Intent(Intent.ACTION_VIEW, waUri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context.startActivity(waIntent)
                    return DeviceActionResult(true, "Opened WhatsApp chat for $displayLabel.", displayLabel)
                } catch (_: Exception) {
                }
            }
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
                setPackage("com.whatsapp")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(sendIntent)
                return DeviceActionResult(true, "Opened WhatsApp with your message ready.", displayLabel)
            } catch (_: Exception) {
                // Fall back to SMS composer if WhatsApp isn't installed
            }
        }

        val smsUri = Uri.parse("smsto:${number ?: ""}")
        val smsIntent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
            putExtra("sms_body", message)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(smsIntent)
            DeviceActionResult(true, "Opened Messages for $displayLabel with text prefilled.", displayLabel)
        } catch (e: Exception) {
            DeviceActionResult(false, "Could not open messaging app: ${e.message}")
        }
    }

    fun getInstalledLauncherApps(): List<InstalledAppInfo> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        val selfPkg = context.packageName

        return resolveInfos
            .asSequence()
            .filter { it.activityInfo.packageName != selfPkg }
            .map { info ->
                InstalledAppInfo(
                    label = info.loadLabel(pm).toString(),
                    packageName = info.activityInfo.packageName
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun openApp(appName: String): DeviceActionResult {
        val query = appName.trim().lowercase()
        if (query.isEmpty()) return DeviceActionResult(false, "Please tell me which app to open.")

        // Handle built-in system destinations cleanly first
        when {
            query in listOf("settings", "setting") -> return openSystemPanel("settings")
            query in listOf("camera", "photo") -> return openSystemPanel("camera")
            query in listOf("alarm", "alarms", "clock", "timer") -> return openSystemPanel("alarms")
            query in listOf("phone", "dialer", "call") -> {
                val intent = Intent(Intent.ACTION_DIAL).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                return try {
                    context.startActivity(intent)
                    DeviceActionResult(true, "Opened Phone.", "Phone")
                } catch (e: Exception) {
                    DeviceActionResult(false, "Could not open Phone: ${e.message}")
                }
            }
        }

        val aliases = mapOf(
            "wa" to "whatsapp",
            "whats app" to "whatsapp",
            "yt" to "youtube",
            "insta" to "instagram",
            "ig" to "instagram",
            "fb" to "facebook",
            "maps" to "maps",
            "google maps" to "maps",
            "browser" to "chrome",
            "mail" to "gmail",
            "music" to "spotify",
            "sms" to "messages",
            "calc" to "calculator"
        )
        val resolvedQuery = aliases[query] ?: query

        val apps = getInstalledLauncherApps()
        val exact = apps.firstOrNull { it.label.equals(resolvedQuery, ignoreCase = true) }
        val startsWith = apps.firstOrNull { it.label.lowercase().startsWith(resolvedQuery) }
        val contains = apps.firstOrNull {
            it.label.lowercase().contains(resolvedQuery) ||
                it.packageName.lowercase().contains(resolvedQuery)
        }
        val match = exact ?: startsWith ?: contains

        if (match != null) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(match.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return try {
                    context.startActivity(launchIntent)
                    DeviceActionResult(true, "Opened ${match.label}.", match.label)
                } catch (e: Exception) {
                    DeviceActionResult(false, "Failed to launch ${match.label}: ${e.message}")
                }
            }
        }

        return DeviceActionResult(false, "App '$appName' wasn't found on this device.")
    }

    fun toggleFlashlight(enable: Boolean): DeviceActionResult {
        val manager = cameraManager
            ?: return DeviceActionResult(false, "Flashlight hardware is unavailable.")
        return try {
            val cameraId = manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (cameraId == null) {
                DeviceActionResult(false, "No camera flash found on this device.")
            } else {
                manager.setTorchMode(cameraId, enable)
                _isFlashlightOn.value = enable
                DeviceActionResult(true, if (enable) "Flashlight turned on." else "Flashlight turned off.")
            }
        } catch (e: Exception) {
            DeviceActionResult(false, "Could not toggle flashlight: ${e.message}")
        }
    }

    fun openSystemPanel(setting: String): DeviceActionResult {
        val normalized = setting.trim().lowercase()
        val intent = when (normalized) {
            "wifi", "wi-fi", "internet" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Intent(Settings.Panel.ACTION_WIFI)
                } else {
                    Intent(Settings.ACTION_WIFI_SETTINGS)
                }
            }
            "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "volume", "sound", "audio" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Intent(Settings.Panel.ACTION_VOLUME)
                } else {
                    Intent(Settings.ACTION_SOUND_SETTINGS)
                }
            }
            "alarms", "alarm", "clock" -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
            "camera" -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            else -> Intent(Settings.ACTION_SETTINGS)
        }.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            val label = normalized.replaceFirstChar { it.uppercase() }
            DeviceActionResult(true, "Opened $label controls.", label)
        } catch (e: Exception) {
            // Fallback to main Settings if a panel isn't supported on emulator/ROM
            try {
                context.startActivity(
                    Intent(Settings.ACTION_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                )
                DeviceActionResult(true, "Opened device Settings.", "Settings")
            } catch (ex: Exception) {
                DeviceActionResult(false, "Unable to open $setting: ${ex.message}")
            }
        }
    }
}
