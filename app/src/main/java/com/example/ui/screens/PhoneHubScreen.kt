package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Launch
import androidx.compose.material.icons.rounded.Message
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AssistantViewModel
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
import java.util.Date
import java.util.Locale

private data class PermissionEntry(
    val title: String,
    val detail: String,
    val permission: String
)

@Composable
fun PhoneHubScreen(
    viewModel: AssistantViewModel,
    modifier: Modifier = Modifier
) {
    val isFlashlightOn by viewModel.isFlashlightOn.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val actionLogs by viewModel.actionLogs.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    var callTarget by remember { mutableStateOf("") }
    var messageRecipient by remember { mutableStateOf("") }
    var messageBody by remember { mutableStateOf("") }
    var appFilter by remember { mutableStateOf("") }
    var permRefreshTick by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        permRefreshTick++
    }

    val permissions = remember(permRefreshTick) {
        buildList {
            add(
                PermissionEntry(
                    title = "Microphone",
                    detail = "Voice commands like Siri",
                    permission = Manifest.permission.RECORD_AUDIO
                )
            )
            add(
                PermissionEntry(
                    title = "Contacts",
                    detail = "Call and message contacts by name",
                    permission = Manifest.permission.READ_CONTACTS
                )
            )
            add(
                PermissionEntry(
                    title = "Place Phone Calls",
                    detail = "Direct dialing from voice commands",
                    permission = Manifest.permission.CALL_PHONE
                )
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                add(
                    PermissionEntry(
                        title = "Answer & End Calls",
                        detail = "Hands-free incoming call control",
                        permission = Manifest.permission.ANSWER_PHONE_CALLS
                    )
                )
            }
            add(
                PermissionEntry(
                    title = "Phone State",
                    detail = "Detect ringing calls",
                    permission = Manifest.permission.READ_PHONE_STATE
                )
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(
                    PermissionEntry(
                        title = "Notifications",
                        detail = "Fire scheduled reminder alerts",
                        permission = Manifest.permission.POST_NOTIFICATIONS
                    )
                )
            }
        }
    }

    val filteredApps = remember(installedApps, appFilter) {
        if (appFilter.isBlank()) {
            installedApps
        } else {
            installedApps.filter {
                it.label.contains(appFilter, ignoreCase = true) ||
                    it.packageName.contains(appFilter, ignoreCase = true)
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Phone Hub",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Text(
                    text = "${installedApps.size} installed apps  ·  Direct device controls",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }
        }

        HorizontalDivider(color = ObsidianBorder)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (statusBanner != null) {
                item(key = "status_card") {
                    Surface(
                        color = ObsidianSurfaceElevated,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, AuraCobalt.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = statusBanner.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.clearStatusBanner() }) {
                                Text("OK", color = AuraCyan)
                            }
                        }
                    }
                }
            }

            // Device Quick Controls
            item(key = "device_controls") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Quick Controls",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickControlTile(
                            label = if (isFlashlightOn) "Torch On" else "Flashlight",
                            icon = if (isFlashlightOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                            active = isFlashlightOn,
                            onClick = { viewModel.toggleFlashlightDirect() },
                            modifier = Modifier.weight(1f)
                        )
                        QuickControlTile(
                            label = "Wi-Fi",
                            icon = Icons.Rounded.Wifi,
                            active = false,
                            onClick = { viewModel.openSystemPanelDirect("wifi") },
                            modifier = Modifier.weight(1f)
                        )
                        QuickControlTile(
                            label = "Bluetooth",
                            icon = Icons.Rounded.Bluetooth,
                            active = false,
                            onClick = { viewModel.openSystemPanelDirect("bluetooth") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickControlTile(
                            label = "Volume",
                            icon = Icons.Rounded.VolumeUp,
                            active = false,
                            onClick = { viewModel.openSystemPanelDirect("volume") },
                            modifier = Modifier.weight(1f)
                        )
                        QuickControlTile(
                            label = "Alarms",
                            icon = Icons.Rounded.Alarm,
                            active = false,
                            onClick = { viewModel.openSystemPanelDirect("alarms") },
                            modifier = Modifier.weight(1f)
                        )
                        QuickControlTile(
                            label = "Camera",
                            icon = Icons.Rounded.CameraAlt,
                            active = false,
                            onClick = { viewModel.openSystemPanelDirect("camera") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Call Control & Dialer
            item(key = "call_control") {
                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Calls & Hands-Free Control",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { viewModel.manageCallDirect("answer") },
                                colors = ButtonDefaults.buttonColors(containerColor = SemanticSuccess),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PhoneInTalk,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("Answer", color = Color.White, maxLines = 1)
                            }
                            Button(
                                onClick = { viewModel.manageCallDirect("decline") },
                                colors = ButtonDefaults.buttonColors(containerColor = SemanticDanger),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CallEnd,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("End Call", color = Color.White, maxLines = 1)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = callTarget,
                                onValueChange = { callTarget = it },
                                placeholder = { Text("Contact name or number", color = TextMuted) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AuraCobalt,
                                    unfocusedBorderColor = ObsidianBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    if (callTarget.isNotBlank()) viewModel.callDirect(callTarget)
                                },
                                enabled = callTarget.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = AuraCobalt),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(52.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Call,
                                    contentDescription = "Place call",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // Quick Message Composer
            item(key = "message_composer") {
                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Quick Message",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        OutlinedTextField(
                            value = messageRecipient,
                            onValueChange = { messageRecipient = it },
                            placeholder = { Text("Recipient name or number", color = TextMuted) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AuraCobalt,
                                unfocusedBorderColor = ObsidianBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = messageBody,
                            onValueChange = { messageBody = it },
                            placeholder = { Text("Message text", color = TextMuted) },
                            maxLines = 3,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AuraCobalt,
                                unfocusedBorderColor = ObsidianBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.sendMessageDirect(messageRecipient, messageBody, "sms")
                                },
                                enabled = messageBody.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = AuraCobalt),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Message,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("Send SMS", color = Color.White, maxLines = 1)
                            }
                            Button(
                                onClick = {
                                    viewModel.sendMessageDirect(messageRecipient, messageBody, "whatsapp")
                                },
                                enabled = messageBody.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = ObsidianSurfaceElevated),
                                border = BorderStroke(1.dp, ObsidianBorder),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Text("WhatsApp", color = TextPrimary, maxLines = 1)
                            }
                        }
                    }
                }
            }

            // Installed Apps Launcher
            item(key = "app_launcher") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "App Launcher",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMuted
                        )
                        TextButton(onClick = { viewModel.openSystemPanelDirect("settings") }) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = null,
                                tint = AuraCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("System Settings", color = AuraCyan)
                        }
                    }

                    OutlinedTextField(
                        value = appFilter,
                        onValueChange = { appFilter = it },
                        placeholder = { Text("Search installed apps...", color = TextMuted) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ObsidianSurface,
                            unfocusedContainerColor = ObsidianSurface,
                            focusedBorderColor = AuraCobalt,
                            unfocusedBorderColor = ObsidianBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (filteredApps.isEmpty()) {
                        Text(
                            text = "No matching apps found on this device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredApps.take(25), key = { it.packageName }) { app ->
                                Surface(
                                    color = ObsidianSurface,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, ObsidianBorder),
                                    modifier = Modifier
                                        .height(46.dp)
                                        .clickable { viewModel.launchAppByPackage(app) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Launch,
                                            contentDescription = null,
                                            tint = AuraCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = app.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextPrimary,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Permissions Manager
            item(key = "permissions_section") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Device Permissions",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted
                    )
                    Surface(
                        color = ObsidianSurface,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            permissions.forEachIndexed { idx, perm ->
                                val granted = viewModel.phoneController.hasPermission(perm.permission)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = perm.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = perm.detail,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = TextSecondary
                                        )
                                    }
                                    if (granted) {
                                        Text(
                                            text = "Granted",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = SemanticSuccess
                                        )
                                    } else {
                                        Surface(
                                            color = AuraCobalt,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .height(38.dp)
                                                .clickable { permissionLauncher.launch(perm.permission) }
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(horizontal = 14.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "Grant",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                                if (idx < permissions.lastIndex) {
                                    HorizontalDivider(color = ObsidianBorder)
                                }
                            }
                        }
                    }
                }
            }

            // Recent Actions Log
            if (actionLogs.isNotEmpty()) {
                item(key = "recent_actions_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Phone Actions",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMuted
                        )
                        TextButton(onClick = { viewModel.clearActionLogs() }) {
                            Text("Clear", color = TextSecondary)
                        }
                    }
                }
                items(actionLogs, key = { "log_${it.id}" }) { log ->
                    val timeStr = remember(log.timestamp) {
                        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(log.timestamp))
                    }
                    Surface(
                        color = ObsidianSurface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = log.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = log.subtitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = timeStr,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickControlTile(
    label: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (active) SemanticWarning.copy(alpha = 0.16f) else ObsidianSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (active) SemanticWarning.copy(alpha = 0.5f) else ObsidianBorder
        ),
        modifier = modifier
            .height(74.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) SemanticWarning else AuraCyan,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
