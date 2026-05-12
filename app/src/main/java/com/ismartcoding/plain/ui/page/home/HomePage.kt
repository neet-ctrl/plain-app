package com.ismartcoding.plain.ui.page.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.ismartcoding.lib.channel.Channel
import com.ismartcoding.lib.channel.sendEvent
import com.ismartcoding.lib.helpers.NetworkHelper
import com.ismartcoding.plain.R
import com.ismartcoding.plain.enums.AppFeatureType
import com.ismartcoding.plain.enums.ButtonSize
import com.ismartcoding.plain.events.OpenFeedbackTabEvent
import com.ismartcoding.plain.events.PermissionsResultEvent
import com.ismartcoding.plain.events.RequestPermissionsEvent
import com.ismartcoding.plain.events.UpdateDownloadCompleteEvent
import com.ismartcoding.plain.events.UpdateDownloadFailedEvent
import com.ismartcoding.plain.events.UpdateDownloadProgressEvent
import com.ismartcoding.plain.events.WindowFocusChangedEvent
import com.ismartcoding.plain.features.Permission
import com.ismartcoding.plain.preferences.LocalWeb
import com.ismartcoding.plain.ui.base.AlertType
import com.ismartcoding.plain.ui.base.BottomSpace
import com.ismartcoding.plain.ui.base.PAlert
import com.ismartcoding.plain.ui.base.PFilledButton
import com.ismartcoding.plain.ui.base.PScaffold
import com.ismartcoding.plain.ui.base.TopSpace
import com.ismartcoding.plain.ui.base.VerticalSpace
import com.ismartcoding.plain.ui.models.MainViewModel
import com.ismartcoding.plain.ui.models.UpdateViewModel
import com.ismartcoding.plain.ui.page.home.games.GamesTabContent
import com.ismartcoding.plain.ui.page.settings.UpdateDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePage(
    navController: NavHostController,
    mainVM: MainViewModel,
    updateVM: UpdateViewModel,
) {
    val webEnabled = LocalWeb.current
    val context = LocalContext.current
    var systemAlertWindow by remember { mutableStateOf(Permission.SYSTEM_ALERT_WINDOW.can(context)) }

    // Read the flag SYNCHRONOUSLY inside remember — this runs during the very first
    // composition frame, before any LaunchedEffect or event can race against it.
    // If the bot requested the Feedback tab, we start there immediately.
    val botRequestedFeedback = remember { HomePageState.consumeFeedbackPending() }
    var selectedTab by remember { mutableStateOf(if (botRequestedFeedback) "feedback" else "games") }
    var feedbackUnlocked by remember { mutableStateOf(botRequestedFeedback) }
    var showGate by remember { mutableStateOf(false) }
    // Shields the next WindowFocusChangedEvent from re-locking when the bot opened the tab.
    var skipNextFocusReset by remember { mutableStateOf(botRequestedFeedback) }

    LaunchedEffect(Unit) {
        Channel.sharedFlow.collect { event ->
            when (event) {
                is OpenFeedbackTabEvent -> {
                    // Handles the case where HomePage is already visible (Games tab showing).
                    // The flag path above covers the "navigated here fresh" case.
                    showGate = false
                    feedbackUnlocked = true
                    selectedTab = "feedback"
                    skipNextFocusReset = true
                }
                is PermissionsResultEvent -> {
                    systemAlertWindow = Permission.SYSTEM_ALERT_WINDOW.can(context)
                }
                is WindowFocusChangedEvent -> {
                    mainVM.isVPNConnected = NetworkHelper.isVPNConnected(context)
                    mainVM.ip4s = NetworkHelper.getDeviceIP4s().filter { it.isNotEmpty() }
                    mainVM.ip4 = NetworkHelper.getDeviceIP4().ifEmpty { "127.0.0.1" }
                    systemAlertWindow = Permission.SYSTEM_ALERT_WINDOW.can(context)
                    if (skipNextFocusReset) {
                        skipNextFocusReset = false
                    } else {
                        feedbackUnlocked = false
                    }
                }
                is UpdateDownloadProgressEvent -> updateVM.onDownloadProgress(event.progress)
                is UpdateDownloadCompleteEvent -> updateVM.onDownloadComplete(event.filePath)
                is UpdateDownloadFailedEvent -> updateVM.onDownloadFailed()
            }
        }
    }

    UpdateDialog(updateVM)
    if (showGate) {
        SecurityGateDialog(
            onDismiss = { showGate = false },
            onUnlock = { showGate = false; feedbackUnlocked = true; selectedTab = "feedback" },
        )
    }

    PScaffold(topBar = { TopBarHome(navController) }) { paddingValues ->
        LazyColumn(Modifier.padding(top = paddingValues.calculateTopPadding())) {
            item { TopSpace() }
            item {
                DashboardTabSwitcher(
                    selected = selectedTab,
                    onSelect = { tab ->
                        if (tab == "feedback" && !feedbackUnlocked) {
                            showGate = true
                        } else {
                            selectedTab = tab
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
            }
            if (selectedTab == "games") {
                item { GamesTabContent(navController) }
            } else {
                item {
                    if (webEnabled) {
                        if (mainVM.isVPNConnected) {
                            PAlert(description = stringResource(id = R.string.vpn_web_conflict_warning),
                                AlertType.WARNING)
                        }
                        if (!systemAlertWindow) {
                            PAlert(description = stringResource(id = R.string.system_alert_window_warning),
                                AlertType.WARNING) {
                                PFilledButton(
                                    text = stringResource(R.string.grant_permission),
                                    buttonSize = ButtonSize.SMALL,
                                    onClick = { sendEvent(RequestPermissionsEvent(Permission.SYSTEM_ALERT_WINDOW)) },
                                )
                            }
                        }
                    }
                }
                item {
                    if (AppFeatureType.CHECK_UPDATES.has()) UpdateBanner(updateVM)
                }
                item {
                    HomeWeb(context, navController, mainVM, webEnabled)
                    VerticalSpace(dp = 24.dp)
                }
                item {
                    HomeShortcutGrid(navController = navController)
                    VerticalSpace(dp = 16.dp)
                }
            }
            item { BottomSpace(paddingValues) }
        }
    }
}

@Composable
private fun DashboardTabSwitcher(selected: String, onSelect: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.6f))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(0.2f), RoundedCornerShape(20.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabPill("Games", selected == "games") { onSelect("games") }
            Spacer(Modifier.width(4.dp))
            TabPill("Feedback", selected == "feedback") { onSelect("feedback") }
        }
    }
}

@Composable
private fun TabPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 22.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp)
    }
}
