package de.famail.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import de.famail.app.ui.account.AddAccountScreen
import de.famail.app.ui.compose.ComposeScreen
import de.famail.app.ui.detail.MessagePane
import de.famail.app.ui.list.MailListPane
import de.famail.app.ui.mailboxes.MailboxOverlay
import de.famail.app.ui.pdf.PdfReaderScreen
import de.famail.app.ui.settings.SettingsScreen
import de.famail.app.ui.split.ResizableSplit
import de.famail.app.ui.theme.FaMailTheme
import de.famail.app.ui.theme.MailTheme

/** Ab dieser Breite (aufgeklapptes Fold) zwei Spalten, darunter Smartphone-Ansicht. */
private val TWO_PANE_MIN_WIDTH = 600.dp

@Composable
fun MailApp(vm: MainViewModel) {
    val settings by vm.settings.collectAsState()
    FaMailTheme(settings.themeMode, settings.fontScale) {
        val c = MailTheme.colors
        val context = LocalContext.current
        val accounts by vm.accounts.collectAsState()
        val loaded by vm.accountsLoaded.collectAsState()
        val snackbar = remember { SnackbarHostState() }
        // Listen-Scrollposition liegt hier oben, damit sie beim Auf-/Zuklappen erhalten bleibt
        val listState = rememberLazyListState()

        LaunchedEffect(Unit) {
            vm.events.collect { e ->
                when (e) {
                    is UiEvent.Message -> snackbar.showSnackbar(e.text)
                    is UiEvent.OpenExternal -> {
                        val ok = runCatching {
                            val uri = FileProvider.getUriForFile(context, context.packageName + ".files", e.file)
                            val intent = Intent(Intent.ACTION_VIEW)
                                .setDataAndType(uri, e.mimeType)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(Intent.createChooser(intent, e.file.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }.isSuccess
                        if (!ok) snackbar.showSnackbar("Keine App zum Öffnen dieses Anhangs gefunden")
                    }
                }
            }
        }

        val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        LaunchedEffect(accounts.isNotEmpty()) {
            if (accounts.isNotEmpty() && Build.VERSION.SDK_INT >= 33) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        Box(Modifier.fillMaxSize().background(c.background)) {
            when {
                !loaded -> Unit
                accounts.isEmpty() && vm.currentScreen != Screen.Settings ->
                    AddAccountScreen(vm, isFirstAccount = true, onCancel = {})
                else -> {
                    MainLayout(vm, listState)

                    val top = vm.currentScreen
                    AnimatedVisibility(
                        visible = top != Screen.Main,
                        enter = slideInVertically { it / 6 } + fadeIn(),
                        exit = slideOutVertically { it / 6 } + fadeOut()
                    ) {
                        when (top) {
                            Screen.AddAccount -> AddAccountScreen(vm, isFirstAccount = false, onCancel = { vm.pop() })
                            Screen.Settings -> SettingsScreen(vm, onBack = { vm.pop() })
                            Screen.Compose -> ComposeScreen(vm)
                            is Screen.Pdf -> PdfReaderScreen(top.path, top.title, onClose = { vm.pop() })
                            Screen.Main -> Unit
                        }
                    }
                    if (top == Screen.AddAccount || top == Screen.Settings) {
                        BackHandler { vm.pop() }
                    }
                }
            }

            SnackbarHost(
                snackbar,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.dp)
            )
        }
    }
}

@Composable
private fun MainLayout(vm: MainViewModel, listState: androidx.compose.foundation.lazy.LazyListState) {
    val selectedId by vm.selectedId.collectAsState()
    val overlay by vm.mailboxOverlay.collectAsState()
    val fraction by vm.splitFraction.collectAsState()
    val fullscreen by vm.fullscreen.collectAsState()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoPane = maxWidth >= TWO_PANE_MIN_WIDTH

        if (twoPane) {
            ResizableSplit(
                fraction = fraction,
                hideLeft = fullscreen,
                onDrag = vm::onSplitDrag,
                onDragEnd = vm::onSplitDragEnd,
                onReset = vm::resetSplit,
                left = {
                    MailListPane(vm, listState, highlightSelection = true, onOpen = { vm.openMessage(it.id) })
                },
                right = { MessagePane(vm, twoPane = true, onBack = null) }
            )
        } else {
            if (selectedId == null) {
                MailListPane(vm, listState, highlightSelection = false, onOpen = { vm.openMessage(it.id) })
            } else {
                MessagePane(vm, twoPane = false, onBack = { vm.closeMessage() })
            }
        }

        MailboxOverlay(vm, visible = overlay, fullWidth = !twoPane)

        // Zurück-Geste: erst Overlay, dann Vollbild, dann geöffnete Nachricht (zugeklappt)
        BackHandler(enabled = vm.currentScreen == Screen.Main && (overlay || fullscreen || (!twoPane && selectedId != null))) {
            when {
                overlay -> vm.setMailboxOverlay(false)
                fullscreen -> vm.toggleFullscreen()
                else -> vm.closeMessage()
            }
        }
    }
}
