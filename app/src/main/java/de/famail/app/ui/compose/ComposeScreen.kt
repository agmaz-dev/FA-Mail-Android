package de.famail.app.ui.compose

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.famail.app.data.repo.ComposeMode
import de.famail.app.ui.MainViewModel
import de.famail.app.ui.theme.MailTheme

@Composable
fun ComposeScreen(vm: MainViewModel) {
    val c = MailTheme.colors
    val draft by vm.draft.collectAsState()
    val sending by vm.sending.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val d = draft ?: return
    val context = LocalContext.current

    var showCloseDialog by remember { mutableStateOf(false) }
    var showCcBcc by rememberSaveable { mutableStateOf(d.ccField.isNotBlank() || d.bccField.isNotBlank()) }
    var fromMenu by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        vm.updateDraft { cur ->
            val existing = cur.attachmentUris.lines().filter { it.isNotBlank() }
            cur.copy(attachmentUris = (existing + uris.map { it.toString() }).distinct().joinToString("\n"))
        }
    }

    BackHandler { showCloseDialog = true }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sheet = maxWidth > 700.dp
        Box(Modifier.fillMaxSize().background(if (sheet) c.scrim else c.pane))
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .then(
                    if (sheet) Modifier
                        .statusBarsPadding()
                        .padding(top = 24.dp, bottom = 24.dp)
                        .widthIn(max = 820.dp)
                        .fillMaxWidth(0.86f)
                        .shadow(24.dp, RoundedCornerShape(24.dp))
                        .clip(RoundedCornerShape(24.dp))
                    else Modifier.fillMaxSize().statusBarsPadding()
                )
                .background(c.pane)
                .imePadding()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
        ) {
            // Kopfzeile
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { showCloseDialog = true }, enabled = !sending) {
                    Text("Abbrechen", color = c.accent, style = MaterialTheme.typography.bodyLarge)
                }
                Text(
                    when (d.mode) {
                        ComposeMode.REPLY, ComposeMode.REPLY_ALL -> "Antwort"
                        ComposeMode.FORWARD -> "Weiterleiten"
                        else -> "Neue E-Mail"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = c.textPrimary,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    maxLines = 1
                )
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !sending) { picker.launch(arrayOf("*/*")) },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Outlined.AttachFile, "Anhang hinzufügen", tint = c.textPrimary) }
                Spacer(Modifier.width(6.dp))
                val canSend = d.toField.isNotBlank() && !sending
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (canSend) c.accent else c.textTertiary)
                        .clickable(enabled = canSend) { vm.send() },
                    contentAlignment = Alignment.Center
                ) {
                    if (sending) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.ArrowUpward, "Senden", tint = Color.White)
                }
            }

            Column(Modifier.fillMaxWidth().weight(1f, fill = !sheet).verticalScroll(rememberScrollState())) {
                FieldRow("An:") {
                    PlainField(d.toField, { v -> vm.updateDraft { it.copy(toField = v) } }, KeyboardType.Email)
                    if (!showCcBcc) {
                        Text(
                            "Cc/Bcc", color = c.accent, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.clickable { showCcBcc = true }.padding(4.dp)
                        )
                    }
                }
                if (showCcBcc) {
                    FieldRow("Cc:") { PlainField(d.ccField, { v -> vm.updateDraft { it.copy(ccField = v) } }, KeyboardType.Email) }
                    FieldRow("Bcc:") { PlainField(d.bccField, { v -> vm.updateDraft { it.copy(bccField = v) } }, KeyboardType.Email) }
                }
                val account = accounts.firstOrNull { it.id == d.accountId }
                FieldRow("Von:") {
                    Box(Modifier.weight(1f)) {
                        Row(
                            Modifier.clickable(enabled = accounts.size > 1) { fromMenu = true },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                account?.let { "${it.displayName} <${it.email}>" } ?: "–",
                                color = c.textPrimary, style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            if (accounts.size > 1) Icon(Icons.Outlined.KeyboardArrowDown, null, tint = c.textSecondary)
                        }
                        DropdownMenu(expanded = fromMenu, onDismissRequest = { fromMenu = false }) {
                            accounts.forEach { a ->
                                DropdownMenuItem(
                                    text = { Text("${a.displayName} <${a.email}>") },
                                    onClick = { fromMenu = false; vm.changeDraftAccount(a.id) }
                                )
                            }
                        }
                    }
                }
                FieldRow("Betreff:") { PlainField(d.subject, { v -> vm.updateDraft { it.copy(subject = v) } }) }

                val uris = d.attachmentUris.lines().filter { it.isNotBlank() }
                if (uris.isNotEmpty()) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        uris.forEach { u ->
                            val name = remember(u) { vm.attachmentName(u) }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(0.5.dp, c.separator, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.Description, null, tint = c.accent, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(name, modifier = Modifier.weight(1f), color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Icon(
                                    Icons.Outlined.Close, "Entfernen", tint = c.textSecondary,
                                    modifier = Modifier.size(20.dp).clickable {
                                        vm.updateDraft { cur ->
                                            cur.copy(attachmentUris = cur.attachmentUris.lines().filter { it.isNotBlank() && it != u }.joinToString("\n"))
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                if (d.mode == ComposeMode.FORWARD) {
                    Text(
                        "Anhänge der Originalnachricht werden beim Senden mitgeschickt.",
                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }

                BasicTextField(
                    value = d.body,
                    onValueChange = { v -> vm.updateDraft { it.copy(body = v) } },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.textPrimary),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 320.dp)
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .navigationBarsPadding()
                )
            }
        }
    }

    if (showCloseDialog) {
        AlertDialog(
            onDismissRequest = { showCloseDialog = false },
            title = { Text("E-Mail schließen?") },
            text = { Text("Der Entwurf kann lokal gesichert oder verworfen werden.") },
            confirmButton = {
                TextButton(onClick = { showCloseDialog = false; vm.keepDraftAndClose() }) { Text("Entwurf sichern") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showCloseDialog = false; vm.discardDraft() }) { Text("Löschen", color = c.danger) }
                    TextButton(onClick = { showCloseDialog = false }) { Text("Weiter bearbeiten") }
                }
            }
        )
    }
}

@Composable
private fun FieldRow(label: String, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val c = MailTheme.colors
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = c.textSecondary, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.width(8.dp))
            content()
        }
        HorizontalDivider(color = c.separator, thickness = 0.5.dp, modifier = Modifier.padding(start = 20.dp))
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.PlainField(
    value: String,
    onChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val c = MailTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.textPrimary),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.weight(1f)
    )
}
