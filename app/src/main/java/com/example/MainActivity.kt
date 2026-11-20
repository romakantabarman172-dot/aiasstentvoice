package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.device.ReminderReceiver
import com.example.ui.AssistantViewModel
import com.example.ui.screens.AssistantScreen
import com.example.ui.screens.PhoneHubScreen
import com.example.ui.screens.TasksRemindersScreen
import com.example.ui.theme.AuraCobalt
import com.example.ui.theme.AuraTheme
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

private enum class AuraTab(
    val label: String,
    val icon: ImageVector
) {
    ASSISTANT("Assistant", Icons.Rounded.AutoAwesome),
    AGENDA("Tasks", Icons.Rounded.TaskAlt),
    PHONE("Phone Hub", Icons.Rounded.PhoneAndroid)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReminderReceiver.ensureNotificationChannel(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            AuraTheme {
                AuraRootApp()
            }
        }
    }
}

@Composable
private fun AuraRootApp(
    viewModel: AssistantViewModel = viewModel()
) {
    var currentTab by rememberSaveable { mutableStateOf(AuraTab.ASSISTANT) }

    Scaffold(
        containerColor = ObsidianBg,
        contentColor = TextPrimary,
        bottomBar = {
            Box {
                HorizontalDivider(color = ObsidianBorder)
                NavigationBar(
                    containerColor = ObsidianSurface,
                    tonalElevation = 0.dp
                ) {
                    AuraTab.entries.forEach { tab ->
                        val selected = tab == currentTab
                        NavigationBarItem(
                            selected = selected,
                            onClick = { currentTab = tab },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label
                                )
                            },
                            label = {
                                Text(
                                    text = tab.label,
                                    maxLines = 1
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AuraCobalt,
                                selectedTextColor = TextPrimary,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted,
                                indicatorColor = AuraCobalt.copy(alpha = 0.16f)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Crossfade(
            targetState = currentTab,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            label = "tab_crossfade"
        ) { tab ->
            when (tab) {
                AuraTab.ASSISTANT -> AssistantScreen(viewModel = viewModel)
                AuraTab.AGENDA -> TasksRemindersScreen(viewModel = viewModel)
                AuraTab.PHONE -> PhoneHubScreen(viewModel = viewModel)
            }
        }
    }
}
