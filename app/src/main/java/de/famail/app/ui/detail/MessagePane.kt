package de.famail.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Forward
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Reply
import androidx.compose.material.icons.outlined.ReplyAll
import androidx.compose.material.icons.filled.Flag
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.famail.app.data.db.FolderRole
import de.famail.app.data.db.MessageEntity
import de.famail.app.data.mail.AttachmentInfo
import de.famail.app.data.mail.MessageParser
import de.famail.app.data.repo.ComposeMode
import de.famail.app.ui.MainViewModel
import de.famail.app.ui.components.DateLabels
import de.famail.app.ui.components.GlassCircleButton
import de.famail.app.ui.components.GlassPill
import de.famail.app.ui.components.InitialsAvatar
import de.famail.app.ui.components.PillIcon
import de.famail.app.ui.theme.MailTheme

/**
 * Rechte Seite (aufgeklappt) bzw. Vollbild-Nachricht (zugeklappt).
 * [twoPane] = true: schwebende Aktionsleiste am rechten Rand wie in der Referenz.
 */
@Composable
fun MessagePane(
    vm: MainViewModel,
    twoPane: Boolean,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val c = MailTheme.colors
    val msg by vm.selectedMessage.collectAsState()
    val loading by vm.bodyLoading.collectAsState()
    val settings by vm.settings.collectAsState()
    val allowedImages by vm.showRemoteImagesFor.collectAsState()
    val fullscreen by vm.fullscreen.collectAsState()

    Box(modifier.fillMaxSize().background(c.pane)) {
        val m = msg
        if (m == null) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Keine E-Mail ausgewählt", style = MaterialTheme.typography.titleMedium, color = c.textSecondary)
            }
            return@Box
        }

        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TopRow(vm, m, twoPane, fullscreen, onBack)
            HeaderBlock(m, endPadding = if (twoPane) 84 else 24)

            val attachments = remember(m.attachmentsJson) { MessageParser.attachmentsFromJson(m.attachmentsJson) }
            if (attachments.isNotEmpty()) AttachmentStrip(vm, m, attachments)

            val html = m.bodyHtml
            val allowRemote = settings.loadRemoteImages || m.id in allowedImages
            if (html != null && !allowRemote && hasRemoteContent(html)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = if (twoPane) 84.dp else 24.dp, bottom = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (c.isDark) androidx.compose.ui.graphics.Color(0xFF1C1C1E) else androidx.compose.ui.graphics.Color(0xFFEFEDF4))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Image, null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Externe Inhalte blockiert (Datenschutz)",
                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "Laden", color = c.accent, style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clickable { vm.allowRemoteImages(m.id) }.padding(4.dp)
                    )
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth().padding(end = if (twoPane) 64.dp else 0.dp)) {
                when {
                    !m.bodyLoaded && loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = c.accent)
                    !m.bodyLoaded -> Text(
                        m.preview.ifBlank { "Inhalt wird beim Öffnen geladen." },
                        color = c.textSecondary,
                        modifier = Modifier.padding(24.dp)
                    )
                    else -> key(m.id) {
                        val content = html ?: plainTextToHtml(m.bodyText ?: "")
                        HtmlBody(
                            html = content,
                            allowRemote = allowRemote,
                            dark = c.isDark,
                            initialScroll = vm.readerScroll[m.id] ?: 0,
                            onScroll = { y -> vm.readerScroll[m.id] = y },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        if (twoPane) {
            SideActionBar(vm, m, Modifier.align(Alignment.CenterEnd).padding(end = 14.dp))
        } else {
            BottomActionBar(
                vm, m,
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 14.dp)
            )
        }
    }
}

@Composable
private fun TopRow(vm: MainViewModel, m: MessageEntity, twoPane: Boolean, fullscreen: Boolean, onBack: (() -> Unit)?) {
    val c = MailTheme.colors
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = if (onBack != null) 8.dp else 24.dp, end = if (twoPane) 84.dp else 12.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Row(
                Modifier.clip(RoundedCornerShape(50)).clickable { onBack() }.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.ArrowBack, "Zurück", tint = c.accent)
                Spacer(Modifier.width(4.dp))
                Text("Posteingang", color = c.accent, style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            Text("1 Nachricht", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        }
        Spacer(Modifier.weight(1f))
        GlassPill {
            Box {
                PillIcon(
                    if (m.isFlagged) Icons.Filled.Flag else Icons.Outlined.Flag, "Markieren",
                    onClick = { menu = true },
                    tint = if (m.isFlagged) c.flag else c.textPrimary
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (m.isFlagged) "Markierung entfernen" else "Markieren") },
                        leadingIcon = { Icon(Icons.Outlined.Flag, null) },
                        onClick = { menu = false; vm.toggleFlag(m) }
                    )
                    DropdownMenuItem(
                        text = { Text(if (m.isRead) "Als ungelesen markieren" else "Als gelesen markieren") },
                        leadingIcon = { Icon(if (m.isRead) Icons.Outlined.MarkEmailUnread else Icons.Outlined.MarkEmailRead, null) },
                        onClick = { menu = false; vm.toggleRead(m) }
                    )
                    DropdownMenuItem(
                        text = { Text("Archivieren") },
                        leadingIcon = { Icon(Icons.Outlined.Archive, null) },
                        onClick = { menu = false; vm.archive(m) }
                    )
                }
            }
            if (twoPane) {
                PillIcon(
                    if (fullscreen) Icons.Outlined.CloseFullscreen else Icons.Outlined.OpenInFull,
                    if (fullscreen) "Vollbild beenden" else "Vollbild",
                    onClick = { vm.toggleFullscreen() }
                )
            }
        }
    }
}

@Composable
private fun HeaderBlock(m: MessageEntity, endPadding: Int) {
    val c = MailTheme.colors
    var expanded by remember(m.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = endPadding.dp, top = 10.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            InitialsAvatar(m.fromName, m.fromAddress, 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).clickable { expanded = !expanded }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        m.fromName,
                        style = MaterialTheme.typography.titleMedium,
                        color = c.textPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        DateLabels.list(m.dateMillis),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary
                    )
                }
                if (expanded) {
                    Text(m.fromAddress, style = MaterialTheme.typography.bodySmall, color = c.accent)
                    Text("An: ${m.toList}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    if (m.ccList.isNotBlank()) {
                        Text("Cc: ${m.ccList}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                    Text(DateLabels.full(m.dateMillis), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                } else {
                    Text(
                        "An: ${m.toList}",
                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        HorizontalDivider(color = c.separator, thickness = 0.5.dp, modifier = Modifier.padding(top = 14.dp, bottom = 14.dp))
        Text(
            m.subject,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = c.textPrimary
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun AttachmentStrip(vm: MainViewModel, m: MessageEntity, attachments: List<AttachmentInfo>) {
    val c = MailTheme.colors
    val downloading by vm.downloading.collectAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        attachments.forEach { att ->
            Row(
                Modifier
                    .widthIn(max = 260.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(0.5.dp, c.separator, RoundedCornerShape(12.dp))
                    .clickable(enabled = downloading == null) { vm.openAttachment(m, att) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (downloading == att.path) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = c.accent)
                } else {
                    Icon(
                        if (att.isPdf) Icons.Outlined.PictureAsPdf else Icons.Outlined.Description,
                        null,
                        tint = if (att.isPdf) c.danger else c.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(att.fileName, style = MaterialTheme.typography.labelLarge, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (att.size > 0) Text(formatSize(att.size), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                }
            }
        }
    }
}

private fun formatSize(bytes: Int): String = when {
    bytes >= 1024 * 1024 -> String.format(java.util.Locale.GERMANY, "%.1f MB", bytes / 1024f / 1024f)
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

@Composable
private fun SideActionBar(vm: MainViewModel, m: MessageEntity, modifier: Modifier) {
    var moveDialog by remember { mutableStateOf(false) }
    var replyMenu by remember { mutableStateOf(false) }
    val c = MailTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GlassCircleButton(Icons.Outlined.Delete, "Löschen", onClick = { vm.delete(m) })
        GlassCircleButton(Icons.Outlined.Folder, "Verschieben", onClick = { moveDialog = true })
        Box {
            GlassCircleButton(Icons.Outlined.Reply, "Antworten", onClick = { replyMenu = true })
            ReplyMenu(replyMenu, { replyMenu = false }, vm, m)
        }
        GlassCircleButton(Icons.Outlined.Create, "Neue E-Mail", onClick = { vm.compose() }, tint = c.accent)
    }
    if (moveDialog) MoveDialog(vm, m) { moveDialog = false }
}

@Composable
private fun BottomActionBar(vm: MainViewModel, m: MessageEntity, modifier: Modifier) {
    var moveDialog by remember { mutableStateOf(false) }
    var replyMenu by remember { mutableStateOf(false) }
    val c = MailTheme.colors
    GlassPill(modifier) {
        PillIcon(Icons.Outlined.Delete, "Löschen", onClick = { vm.delete(m) })
        PillIcon(Icons.Outlined.Folder, "Verschieben", onClick = { moveDialog = true })
        Box {
            PillIcon(Icons.Outlined.Reply, "Antworten", onClick = { replyMenu = true })
            ReplyMenu(replyMenu, { replyMenu = false }, vm, m)
        }
        PillIcon(Icons.Outlined.Create, "Neue E-Mail", onClick = { vm.compose() }, tint = c.accent)
    }
    if (moveDialog) MoveDialog(vm, m) { moveDialog = false }
}

@Composable
private fun ReplyMenu(expanded: Boolean, onDismiss: () -> Unit, vm: MainViewModel, m: MessageEntity) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        MenuEntry("Antworten", Icons.Outlined.Reply) { onDismiss(); vm.compose(ComposeMode.REPLY, m) }
        MenuEntry("Allen antworten", Icons.Outlined.ReplyAll) { onDismiss(); vm.compose(ComposeMode.REPLY_ALL, m) }
        MenuEntry("Weiterleiten", Icons.Outlined.Forward) { onDismiss(); vm.compose(ComposeMode.FORWARD, m) }
    }
}

@Composable
private fun MenuEntry(label: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(icon, null) }, onClick = onClick)
}

@Composable
private fun MoveDialog(vm: MainViewModel, m: MessageEntity, onDismiss: () -> Unit) {
    val c = MailTheme.colors
    val folders by vm.folders.collectAsState()
    val targets = folders
        .filter { it.accountId == m.accountId && it.id != m.folderId }
        .sortedBy { FolderRole.order.indexOf(it.role).let { i -> if (i < 0) 99 else i } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Verschieben nach …") },
        text = {
            LazyColumn(Modifier.height(360.dp)) {
                items(targets, key = { it.id }) { f ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onDismiss(); vm.moveTo(m, f) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Folder, null, tint = c.accent)
                        Spacer(Modifier.width(12.dp))
                        Text(MainViewModel.folderLabel(f), color = c.textPrimary)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
