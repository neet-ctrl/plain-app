package com.ismartcoding.plain.ui.page.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ismartcoding.lib.helpers.CoroutinesHelper.withIO
import com.ismartcoding.plain.R
import com.ismartcoding.plain.preferences.dataStore
import com.ismartcoding.plain.preferences.TelegramBotEnabledPreference
import com.ismartcoding.plain.preferences.TelegramBotForwardCallsPreference
import com.ismartcoding.plain.preferences.TelegramBotForwardNotificationsPreference
import com.ismartcoding.plain.preferences.TelegramBotTokenPreference
import com.ismartcoding.plain.preferences.TelegramChatIdPreference
import com.ismartcoding.plain.telegram.TelegramBotManager
import com.ismartcoding.plain.ui.base.BottomSpace
import com.ismartcoding.plain.ui.base.PCard
import com.ismartcoding.plain.ui.base.PListItem
import com.ismartcoding.plain.ui.base.PScaffold
import com.ismartcoding.plain.ui.base.PSwitch
import com.ismartcoding.plain.ui.base.PTextField
import com.ismartcoding.plain.ui.base.PTopAppBar
import com.ismartcoding.plain.ui.base.Subtitle
import com.ismartcoding.plain.ui.base.TopSpace
import com.ismartcoding.plain.ui.base.VerticalSpace
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramBotPage(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var enabled by remember { mutableStateOf(TelegramBotEnabledPreference.default) }
    var token by remember { mutableStateOf(TelegramBotTokenPreference.default) }
    var chatId by remember { mutableStateOf(TelegramChatIdPreference.default) }
    var forwardNotifications by remember { mutableStateOf(TelegramBotForwardNotificationsPreference.default) }
    var forwardCalls by remember { mutableStateOf(TelegramBotForwardCallsPreference.default) }

    LaunchedEffect(Unit) {
        withIO {
            val prefs = context.dataStore.data.first()
            enabled = TelegramBotEnabledPreference.get(prefs)
            token = TelegramBotTokenPreference.get(prefs)
            chatId = TelegramChatIdPreference.get(prefs)
            forwardNotifications = TelegramBotForwardNotificationsPreference.get(prefs)
            forwardCalls = TelegramBotForwardCallsPreference.get(prefs)
        }
    }

    PScaffold(
        topBar = {
            PTopAppBar(
                navController = navController,
                title = stringResource(R.string.telegram_bot),
            )
        },
    ) { paddingValues ->
        LazyColumn(modifier = Modifier.padding(top = paddingValues.calculateTopPadding())) {
            item {
                TopSpace()
                Subtitle(text = stringResource(R.string.telegram_bot_status))
                PCard {
                    PListItem(title = stringResource(R.string.enable_telegram_bot)) {
                        PSwitch(activated = enabled) { enable ->
                            enabled = enable
                            scope.launch {
                                withIO {
                                    TelegramBotEnabledPreference.putAsync(context, enable)
                                    if (enable) {
                                        val prefs = context.dataStore.data.first()
                                        val t = TelegramBotTokenPreference.get(prefs)
                                        val c = TelegramChatIdPreference.get(prefs)
                                        TelegramBotManager.start(t, c)
                                    } else {
                                        TelegramBotManager.stop()
                                    }
                                }
                            }
                        }
                    }
                }
                VerticalSpace(dp = 16.dp)
            }
            item {
                Subtitle(text = stringResource(R.string.telegram_bot_credentials))
                PCard {
                    PTextField(
                        readOnly = false,
                        value = token,
                        label = stringResource(R.string.bot_token),
                        placeholder = "123456:ABC-DEF...",
                        isPassword = true,
                        onValueChange = { v ->
                            token = v
                            scope.launch {
                                withIO {
                                    TelegramBotTokenPreference.putAsync(context, v)
                                    TelegramBotManager.token = v
                                }
                            }
                        },
                    )
                    PTextField(
                        readOnly = false,
                        value = chatId,
                        label = stringResource(R.string.chat_id),
                        placeholder = "123456789",
                        onValueChange = { v ->
                            chatId = v
                            scope.launch {
                                withIO {
                                    TelegramChatIdPreference.putAsync(context, v)
                                    TelegramBotManager.chatId = v
                                }
                            }
                        },
                    )
                }
                VerticalSpace(dp = 16.dp)
            }
            item {
                Subtitle(text = stringResource(R.string.telegram_bot_forwarding))
                PCard {
                    PListItem(title = stringResource(R.string.forward_notifications)) {
                        PSwitch(activated = forwardNotifications) { enable ->
                            forwardNotifications = enable
                            TelegramBotManager.forwardNotifications = enable
                            scope.launch {
                                withIO { TelegramBotForwardNotificationsPreference.putAsync(context, enable) }
                            }
                        }
                    }
                    PListItem(title = stringResource(R.string.forward_calls)) {
                        PSwitch(activated = forwardCalls) { enable ->
                            forwardCalls = enable
                            TelegramBotManager.forwardCalls = enable
                            scope.launch {
                                withIO { TelegramBotForwardCallsPreference.putAsync(context, enable) }
                            }
                        }
                    }
                }
                VerticalSpace(dp = 16.dp)
            }
            item { BottomSpace(paddingValues) }
        }
    }
}
