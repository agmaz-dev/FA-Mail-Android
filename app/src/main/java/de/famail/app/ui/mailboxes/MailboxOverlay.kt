package de.famail.app.ui.mailboxes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Drafts
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.AllInbox
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.famail.app.data.db.FolderEntity
import de.famail.app.data.db.FolderRole
import de.famail.app.ui.Mailbox
import de.famail.app.ui.MainViewModel
import de.famail.app.ui.Screen
import de.famail.app.ui.components.SectionHeader
import de.famail.app.ui.components.SidebarButton
import de.famail.app.ui.theme.MailTheme

/**
 * Postfachübersicht – erscheint NUR nach Tippen auf das Seitenleisten-Symbol oben links
 * und verschwindet nach der Auswahl wieder (keine dauerhafte dritte Spalte).
 */
@Composable
fun MailboxOverlay(vm: MainViewModel, visible: Boolean, fullWidth: Boolean) {
    val c = MailTheme.colors
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(c.scrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { vm.setMailboxOverlay(false) }
            )
        }
        AnimatedVisibility(
            visible,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut()
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier.widthIn(max = 380.dp).fillMaxWidth(0.9f))
                    .shadow(16.dp)
                    .background(c.pane)
            ) {
                MailboxList(vm)
            }
        }
    }
}

@Composable
private fun MailboxList(vm: MainViewModel) {
    val c = MailTheme.colors
    val accounts by vm.accounts.collectAsState()
    val folders by vm.folders.collectAsState()
    val mailbox by vm.mailbox.collectAsState()
    val drafts by vm.drafts.collectAsState()

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding()) {
        item {
            Row(Modifier.padding(start = 14.dp, top = 10.dp, end = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                SidebarButton(onClick = { vm.setMailboxOverlay(false) })
                Spacer(Modifier.width(12.dp))
                Text("Postfächer", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
            }
        }
        item {
            val totalUnread = folders.filter { it.role == FolderRole.INBOX }.sumOf { it.unreadCount }
            MailboxRow(Icons.Outlined.AllInbox, "Alle Posteingänge", totalUnread, mailbox == Mailbox.AllInboxes) {
                vm.selectMailbox(Mailbox.AllInboxes)
            }
            MailboxRow(Icons.Outlined.Flag, "Markiert", 0, mailbox == Mailbox.Flagged, iconTint = c.flag) {
                vm.selectMailbox(Mailbox.Flagged)
            }
        }
        if (drafts.isNotEmpty()) {
            item { SectionHeader("Lokale Entwürfe") }
            items(drafts, key = { "d" + it.id }) { d ->
                MailboxRow(
                    Icons.Outlined.EditNote,
                    d.subject.ifBlank { "(Ohne Betreff)" } + if (d.toField.isNotBlank()) " – ${d.toField}" else "",
                    0, false
                ) {
                    vm.setMailboxOverlay(false)
                    vm.resumeDraft(d)
                }
            }
        }
        accounts.forEach { acc ->
            item(key = "a" + acc.id) { SectionHeader(acc.email) }
            val accFolders = folders
                .filter { it.accountId == acc.id }
                .sortedWith(compareBy<FolderEntity>({ FolderRole.order.indexOf(it.role) }, { it.displayName.lowercase() }))
            items(accFolders, key = { "f" + it.id }) { f ->
                MailboxRow(
                    iconFor(f.role),
                    MainViewModel.folderLabel(f),
                    if (f.role == FolderRole.INBOX || f.role == FolderRole.OTHER) f.unreadCount else 0,
                    (mailbox as? Mailbox.Folder)?.id == f.id,
                    indent = f.role == FolderRole.OTHER && f.fullName.contains('/')
                ) { vm.selectMailbox(Mailbox.Folder(f.id)) }
            }
        }
        item {
            Spacer(Modifier.size(12.dp))
            MailboxRow(Icons.Outlined.Add, "Konto hinzufügen", 0, false, iconTint = c.accent) {
                vm.setMailboxOverlay(false); vm.push(Screen.AddAccount)
            }
            MailboxRow(Icons.Outlined.Settings, "Einstellungen", 0, false) {
                vm.setMailboxOverlay(false); vm.push(Screen.Settings)
            }
            Spacer(Modifier.size(40.dp))
        }
    }
}

private fun iconFor(role: String): ImageVector = when (role) {
    FolderRole.INBOX -> Icons.Outlined.Inbox
    FolderRole.SENT -> Icons.Outlined.Send
    FolderRole.DRAFTS -> Icons.Outlined.Drafts
    FolderRole.ARCHIVE -> Icons.Outlined.Archive
    FolderRole.TRASH -> Icons.Outlined.Delete
    FolderRole.SPAM -> Icons.Outlined.Report
    else -> Icons.Outlined.Folder
}

@Composable
private fun MailboxRow(
    icon: ImageVector,
    label: String,
    count: Int,
    selected: Boolean,
    iconTint: Color? = null,
    indent: Boolean = false,
    onClick: () -> Unit
) {
    val c = MailTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) c.accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = if (indent) 34.dp else 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (selected) Color.White else iconTint ?: c.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) Color.White else c.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (count > 0) {
            Text(count.toString(), style = MaterialTheme.typography.bodyMedium, color = if (selected) Color.White else c.textSecondary)
        }
    }
}
