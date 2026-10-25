package de.famail.app.ui.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.famail.app.data.db.MessageEntity
import de.famail.app.ui.ListFilter
import de.famail.app.ui.MainViewModel
import de.famail.app.ui.Screen
import de.famail.app.ui.SortOrder
import de.famail.app.ui.components.DateLabels
import de.famail.app.ui.components.GlassCircleButton
import de.famail.app.ui.components.GlassPill
import de.famail.app.ui.components.InitialsAvatar
import de.famail.app.ui.components.PillIcon
import de.famail.app.ui.components.SidebarButton
import de.famail.app.ui.theme.MailTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailListPane(
    vm: MainViewModel,
    listState: LazyListState,
    highlightSelection: Boolean,
    onOpen: (MessageEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = MailTheme.colors
    val messages by vm.messages.collectAsState()
    val selectedId by vm.selectedId.collectAsState()
    val syncing by vm.syncing.collectAsState()
    val query by vm.query.collectAsState()
    val filter by vm.filter.collectAsState()
    val sort by vm.sort.collectAsState()
    val lastSync by vm.lastSyncLabel.collectAsState()
    vm.mailbox.collectAsState().value // Titel bei Postfachwechsel neu zeichnen
    vm.folders.collectAsState().value
    vm.accounts.collectAsState().value

    BoxWithConstraints(modifier.fillMaxSize().background(c.pane)) {
        val narrow = maxWidth < 240.dp

        Column(Modifier.fillMaxSize()) {
            ListHeader(
                vm = vm,
                narrow = narrow,
                subtitle = listOf(vm.mailboxSubtitle(), lastSync).filter { it.isNotBlank() }.joinToString(" · "),
                filter = filter,
                sort = sort
            )

            PullToRefreshBox(
                isRefreshing = syncing,
                onRefresh = { vm.refresh() },
                modifier = Modifier.fillMaxSize()
            ) {
                if (messages.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            when {
                                query.isNotBlank() -> "Keine Treffer"
                                syncing -> "Wird geladen …"
                                filter != ListFilter.ALL -> "Keine E-Mails für „${filter.label}“"
                                else -> "Keine E-Mails"
                            },
                            color = c.textSecondary,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 110.dp)
                    ) {
                        itemsIndexed(messages, key = { _, m -> m.id }) { index, m ->
                            val isSelected = highlightSelection && m.id == selectedId
                            val nextSelected = highlightSelection && messages.getOrNull(index + 1)?.id == selectedId
                            SwipeableRow(vm, m) {
                                MailRow(
                                    m = m,
                                    selected = isSelected,
                                    showDivider = !isSelected && !nextSelected && index < messages.lastIndex,
                                    narrow = narrow,
                                    vm = vm,
                                    onClick = { onOpen(m) }
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchBar(query = query, onQuery = vm::setQuery, modifier = Modifier.weight(1f))
            if (!narrow) {
                Spacer(Modifier.width(10.dp))
                GlassCircleButton(Icons.Outlined.Create, "Neue E-Mail", onClick = { vm.compose() }, size = 52.dp, tint = c.accent)
            }
        }
    }
}

@Composable
private fun ListHeader(vm: MainViewModel, narrow: Boolean, subtitle: String, filter: ListFilter, sort: SortOrder) {
    val c = MailTheme.colors
    var filterMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SidebarButton(onClick = { vm.setMailboxOverlay(true) })
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                vm.mailboxTitle(),
                style = MaterialTheme.typography.titleLarge,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!narrow && subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (!narrow) {
            GlassPill {
                Box {
                    PillIcon(
                        Icons.Outlined.FilterList, "Filter",
                        onClick = { filterMenu = true },
                        tint = if (filter != ListFilter.ALL) c.accent else c.textPrimary
                    )
                    DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                        ListFilter.entries.forEach { f ->
                            DropdownMenuItem(
                                text = { Text(f.label) },
                                trailingIcon = { if (f == filter) Icon(Icons.Outlined.Check, null) },
                                onClick = { vm.setFilter(f); filterMenu = false }
                            )
                        }
                    }
                }
                Box {
                    PillIcon(Icons.Outlined.MoreHoriz, "Mehr", onClick = { moreMenu = true })
                    DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Neue E-Mail") },
                            leadingIcon = { Icon(Icons.Outlined.Create, null) },
                            onClick = { moreMenu = false; vm.compose() }
                        )
                        DropdownMenuItem(
                            text = { Text("Aktualisieren") },
                            leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                            onClick = { moreMenu = false; vm.refresh() }
                        )
                        HorizontalDivider()
                        SortOrder.entries.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.label) },
                                trailingIcon = { if (s == sort) Icon(Icons.Outlined.Check, null) },
                                onClick = { vm.setSort(s); moreMenu = false }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Einstellungen") },
                            leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                            onClick = { moreMenu = false; vm.push(Screen.Settings) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableRow(vm: MainViewModel, m: MessageEntity, content: @Composable () -> Unit) {
    val c = MailTheme.colors
    val current by rememberUpdatedState(m)
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> vm.delete(current)
                SwipeToDismissBoxValue.StartToEnd -> vm.toggleRead(current)
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false // Zeile springt zurück; die Liste aktualisiert sich über die Datenbank
        }
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val toDelete = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Box(
                Modifier
                    .fillMaxSize()
                    .background(if (toDelete) c.danger else c.accent)
                    .padding(horizontal = 24.dp),
                contentAlignment = if (toDelete) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Icon(
                    if (toDelete) Icons.Outlined.Delete
                    else if (m.isRead) Icons.Outlined.MarkEmailUnread else Icons.Outlined.MarkEmailRead,
                    contentDescription = null,
                    tint = Color.White
                )
            }
        }
    ) { content() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MailRow(
    m: MessageEntity,
    selected: Boolean,
    showDivider: Boolean,
    narrow: Boolean,
    vm: MainViewModel,
    onClick: () -> Unit
) {
    val c = MailTheme.colors
    var menu by remember { mutableStateOf(false) }
    val primary = if (selected) c.onAccent else c.textPrimary
    val secondary = if (selected) c.selectedSecondary else c.textSecondary

    Box(Modifier.fillMaxWidth().background(c.pane)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = if (narrow) 6.dp else 10.dp, vertical = 1.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(if (selected) c.accent else c.pane)
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .padding(start = if (narrow) 10.dp else 6.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                // Ungelesen-Punkt
                Box(Modifier.width(if (narrow) 10.dp else 14.dp).padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    if (!m.isRead) {
                        Box(
                            Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(if (selected) Color.White else c.unreadDot)
                        )
                    }
                }
                if (!narrow) {
                    InitialsAvatar(m.fromName, m.fromAddress, 50.dp, Modifier.padding(top = 2.dp))
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            m.fromName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (m.isRead) FontWeight.SemiBold else FontWeight.Bold,
                            color = primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (m.isFlagged) {
                            Icon(
                                Icons.Filled.Flag, "Markiert",
                                tint = if (selected) Color.White else c.flag,
                                modifier = Modifier.size(15.dp).padding(end = 2.dp)
                            )
                        }
                        Text(
                            DateLabels.list(m.dateMillis),
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondary,
                            maxLines = 1
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            m.subject,
                            style = MaterialTheme.typography.bodyLarge,
                            color = primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (m.hasAttachments) {
                            Icon(
                                Icons.Outlined.AttachFile, "Anhang",
                                tint = secondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    if (m.preview.isNotBlank()) {
                        Text(
                            m.preview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondary,
                            maxLines = if (narrow) 1 else 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        if (showDivider) {
            HorizontalDivider(
                color = c.separator,
                thickness = 0.5.dp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = if (narrow) 26.dp else 92.dp, end = 16.dp)
            )
        }

        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(if (m.isRead) "Als ungelesen markieren" else "Als gelesen markieren") },
                leadingIcon = { Icon(if (m.isRead) Icons.Outlined.MarkEmailUnread else Icons.Outlined.MarkEmailRead, null) },
                onClick = { menu = false; vm.toggleRead(m) }
            )
            DropdownMenuItem(
                text = { Text(if (m.isFlagged) "Markierung entfernen" else "Markieren") },
                leadingIcon = { Icon(Icons.Outlined.Flag, null) },
                onClick = { menu = false; vm.toggleFlag(m) }
            )
            DropdownMenuItem(
                text = { Text("Archivieren") },
                leadingIcon = { Icon(Icons.Outlined.Archive, null) },
                onClick = { menu = false; vm.archive(m) }
            )
            DropdownMenuItem(
                text = { Text("Löschen", color = c.danger) },
                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = c.danger) },
                onClick = { menu = false; vm.delete(m) }
            )
        }
    }
}

@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = MailTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .shadow(10.dp, RoundedCornerShape(50), clip = false, ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
            .clip(RoundedCornerShape(50))
            .background(c.glass)
            .border(0.5.dp, c.glassBorder, RoundedCornerShape(50))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Outlined.Search, null, tint = c.textSecondary, modifier = Modifier.size(22.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text("Suchen", style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.textPrimary),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                Icons.Outlined.Close, "Suche löschen",
                tint = c.textSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable { onQuery("") }
            )
        }
    }
}
