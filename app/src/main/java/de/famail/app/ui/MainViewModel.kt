package de.famail.app.ui

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.famail.app.FaMailApp
import de.famail.app.data.db.AccountEntity
import de.famail.app.data.db.DraftEntity
import de.famail.app.data.db.FolderEntity
import de.famail.app.data.db.FolderRole
import de.famail.app.data.db.MessageEntity
import de.famail.app.data.mail.AttachmentInfo
import de.famail.app.data.repo.ComposeMode
import de.famail.app.data.repo.NewAccountInput
import de.famail.app.data.repo.friendlyMessage
import de.famail.app.data.settings.AppSettings
import de.famail.app.data.settings.ThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

sealed interface Mailbox {
    data object AllInboxes : Mailbox
    data object Flagged : Mailbox
    data class Folder(val id: Long) : Mailbox
}

enum class ListFilter(val label: String) {
    ALL("Alle"), UNREAD("Ungelesen"), FLAGGED("Markiert"), ATTACHMENTS("Mit Anhang")
}

enum class SortOrder(val label: String) {
    NEWEST("Neueste zuerst"), OLDEST("Älteste zuerst"), SENDER("Nach Absender")
}

sealed interface Screen {
    data object Main : Screen
    data object AddAccount : Screen
    data object Settings : Screen
    data object Compose : Screen
    data class Pdf(val path: String, val title: String) : Screen
}

sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
    data class OpenExternal(val file: File, val mimeType: String) : UiEvent
}

data class AddAccountState(val busy: Boolean = false, val error: String? = null, val loginRejected: Boolean = false)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as FaMailApp).container
    private val repo = container.repository
    private val settingsStore = container.settings

    // ---------------------------------------------------------------- Grunddaten

    private val _accountsLoaded = MutableStateFlow(false)
    val accountsLoaded: StateFlow<Boolean> = _accountsLoaded.asStateFlow()

    val accounts: StateFlow<List<AccountEntity>> = repo.accounts
        .map { _accountsLoaded.value = true; it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val folders: StateFlow<List<FolderEntity>> = repo.folders
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val settings: StateFlow<AppSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    // ---------------------------------------------------------------- Navigation

    val screens = mutableStateListOf<Screen>(Screen.Main)
    val currentScreen: Screen get() = screens.last()

    fun push(screen: Screen) { screens.add(screen) }
    fun pop(): Boolean {
        if (screens.size <= 1) return false
        screens.removeAt(screens.lastIndex)
        return true
    }

    private val _mailboxOverlay = MutableStateFlow(false)
    val mailboxOverlay: StateFlow<Boolean> = _mailboxOverlay.asStateFlow()
    fun setMailboxOverlay(open: Boolean) { _mailboxOverlay.value = open }

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()
    private fun toast(text: String) { _events.tryEmit(UiEvent.Message(text)) }

    // ---------------------------------------------------------------- Liste

    private val _mailbox = MutableStateFlow<Mailbox>(Mailbox.AllInboxes)
    val mailbox: StateFlow<Mailbox> = _mailbox.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow(ListFilter.ALL)
    val filter: StateFlow<ListFilter> = _filter.asStateFlow()

    private val _sort = MutableStateFlow(SortOrder.NEWEST)
    val sort: StateFlow<SortOrder> = _sort.asStateFlow()

    private val rawMessages = combine(_mailbox, _query.debounce(200)) { mb, q -> mb to q }
        .flatMapLatest { (mb, q) ->
            when (mb) {
                Mailbox.AllInboxes -> repo.observeAllInboxes(q)
                Mailbox.Flagged -> repo.observeFlagged(q)
                is Mailbox.Folder -> repo.observeFolder(mb.id, q)
            }
        }

    val messages: StateFlow<List<MessageEntity>> = combine(rawMessages, _filter, _sort) { list, f, s ->
        val filtered = when (f) {
            ListFilter.ALL -> list
            ListFilter.UNREAD -> list.filter { !it.isRead }
            ListFilter.FLAGGED -> list.filter { it.isFlagged }
            ListFilter.ATTACHMENTS -> list.filter { it.hasAttachments }
        }
        when (s) {
            SortOrder.NEWEST -> filtered.sortedByDescending { it.dateMillis }
            SortOrder.OLDEST -> filtered.sortedBy { it.dateMillis }
            SortOrder.SENDER -> filtered.sortedWith(compareBy<MessageEntity>({ it.fromName.lowercase() }, { -it.dateMillis }))
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun selectMailbox(mb: Mailbox) {
        _mailbox.value = mb
        _mailboxOverlay.value = false
        if (mb is Mailbox.Folder) {
            viewModelScope.launch {
                runCatching { repo.syncFolderById(mb.id) }
                    .onFailure { toast("Ordner konnte nicht geladen werden: ${it.friendlyMessage()}") }
            }
        }
    }

    fun setQuery(q: String) { _query.value = q }
    fun setFilter(f: ListFilter) { _filter.value = f }
    fun setSort(s: SortOrder) { _sort.value = s }

    fun mailboxTitle(): String = when (val mb = _mailbox.value) {
        Mailbox.AllInboxes -> "Alle Posteingänge"
        Mailbox.Flagged -> "Markiert"
        is Mailbox.Folder -> folders.value.firstOrNull { it.id == mb.id }?.let { folderLabel(it) } ?: "Postfach"
    }

    fun mailboxSubtitle(): String {
        val accs = accounts.value
        return when (val mb = _mailbox.value) {
            is Mailbox.Folder -> {
                val f = folders.value.firstOrNull { it.id == mb.id }
                accs.firstOrNull { it.id == f?.accountId }?.email ?: ""
            }
            else -> if (accs.size == 1) accs.first().email else "${accs.size} Konten"
        }
    }

    // ---------------------------------------------------------------- Synchronisierung

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _lastSyncLabel = MutableStateFlow("")
    val lastSyncLabel: StateFlow<String> = _lastSyncLabel.asStateFlow()

    private var syncJob: Job? = null

    fun refresh() {
        if (syncJob?.isActive == true) return
        syncJob = viewModelScope.launch {
            _syncing.value = true
            try {
                when (val mb = _mailbox.value) {
                    is Mailbox.Folder -> repo.syncFolderById(mb.id)
                    else -> repo.syncAll()
                }
                _lastSyncLabel.value = "Gerade aktualisiert"
            } catch (e: Exception) {
                _lastSyncLabel.value = "Aktualisierung fehlgeschlagen"
                toast(e.friendlyMessage())
            } finally {
                _syncing.value = false
            }
        }
    }

    init {
        viewModelScope.launch {
            // Erste Synchronisierung nach dem Start, sobald Konten vorhanden sind
            val accs = repo.accounts.first()
            if (accs.isNotEmpty()) refresh()
        }
    }

    // ---------------------------------------------------------------- Ausgewählte Nachricht

    private val _selectedId = MutableStateFlow<Long?>(null)
    val selectedId: StateFlow<Long?> = _selectedId.asStateFlow()

    val selectedMessage: StateFlow<MessageEntity?> = _selectedId
        .flatMapLatest { id -> if (id == null) flowOf(null) else repo.observeMessage(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _bodyLoading = MutableStateFlow(false)
    val bodyLoading: StateFlow<Boolean> = _bodyLoading.asStateFlow()

    /** Leseposition je Nachricht – bleibt beim Auf-/Zuklappen und Verschieben der Trennlinie erhalten */
    val readerScroll = HashMap<Long, Int>()

    private val _showRemoteImagesFor = MutableStateFlow<Set<Long>>(emptySet())
    val showRemoteImagesFor: StateFlow<Set<Long>> = _showRemoteImagesFor.asStateFlow()
    fun allowRemoteImages(id: Long) { _showRemoteImagesFor.value = _showRemoteImagesFor.value + id }

    fun openMessage(id: Long) {
        _selectedId.value = id
        viewModelScope.launch {
            val msg = repo.getMessage(id) ?: return@launch
            if (!msg.bodyLoaded) {
                _bodyLoading.value = true
                try {
                    repo.loadBody(id)
                } catch (e: Exception) {
                    toast("Nachricht konnte nicht geladen werden: ${e.friendlyMessage()}")
                } finally {
                    _bodyLoading.value = false
                }
            }
            if (!msg.isRead) runCatching { repo.setRead(id, true) }
        }
    }

    fun closeMessage() { _selectedId.value = null; _fullscreen.value = false }

    private fun selectNeighbourOf(id: Long) {
        val list = messages.value
        val idx = list.indexOfFirst { it.id == id }
        val next = list.getOrNull(idx + 1) ?: list.getOrNull(idx - 1)
        if (next != null && next.id != id) openMessage(next.id) else closeMessage()
    }

    fun toggleRead(msg: MessageEntity) = viewModelScope.launch {
        runCatching { repo.setRead(msg.id, !msg.isRead) }
            .onFailure { toast(it.friendlyMessage()) }
    }

    fun toggleFlag(msg: MessageEntity) = viewModelScope.launch {
        runCatching { repo.setFlagged(msg.id, !msg.isFlagged) }
            .onFailure { toast(it.friendlyMessage()) }
    }

    fun delete(msg: MessageEntity) = viewModelScope.launch {
        if (_selectedId.value == msg.id) selectNeighbourOf(msg.id)
        runCatching { repo.delete(msg.id) }
            .onSuccess { toast("In den Papierkorb verschoben") }
            .onFailure { toast("Löschen fehlgeschlagen: ${it.friendlyMessage()}") }
    }

    fun archive(msg: MessageEntity) = viewModelScope.launch {
        runCatching { repo.archive(msg.id) }
            .onSuccess { ok ->
                if (ok) {
                    if (_selectedId.value == msg.id) selectNeighbourOf(msg.id)
                    toast("Archiviert")
                } else toast("Dieses Konto hat keinen Archivordner")
            }
            .onFailure { toast("Archivieren fehlgeschlagen: ${it.friendlyMessage()}") }
    }

    fun moveTo(msg: MessageEntity, folder: FolderEntity) = viewModelScope.launch {
        if (_selectedId.value == msg.id) selectNeighbourOf(msg.id)
        runCatching { repo.moveTo(msg.id, folder.id) }
            .onSuccess { toast("Verschoben nach „${folderLabel(folder)}“") }
            .onFailure { toast("Verschieben fehlgeschlagen: ${it.friendlyMessage()}") }
    }

    private val _downloading = MutableStateFlow<String?>(null)
    val downloading: StateFlow<String?> = _downloading.asStateFlow()

    fun openAttachment(msg: MessageEntity, att: AttachmentInfo) = viewModelScope.launch {
        _downloading.value = att.path
        try {
            val file = repo.downloadAttachment(msg.id, att)
            if (att.isPdf) push(Screen.Pdf(file.absolutePath, att.fileName))
            else _events.tryEmit(UiEvent.OpenExternal(file, att.mimeType))
        } catch (e: Exception) {
            toast("Anhang konnte nicht geladen werden: ${e.friendlyMessage()}")
        } finally {
            _downloading.value = null
        }
    }

    // ---------------------------------------------------------------- Bildschirmaufteilung

    private val _splitDrag = MutableStateFlow<Float?>(null)
    val splitFraction: StateFlow<Float> = combine(settings, _splitDrag) { s, drag -> drag ?: s.splitFraction }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings.DEFAULT_SPLIT)

    private val _fullscreen = MutableStateFlow(false)
    val fullscreen: StateFlow<Boolean> = _fullscreen.asStateFlow()
    fun toggleFullscreen() { _fullscreen.value = !_fullscreen.value }

    fun onSplitDrag(fraction: Float) {
        _splitDrag.value = fraction.coerceIn(AppSettings.MIN_SPLIT, AppSettings.MAX_SPLIT)
    }

    fun onSplitDragEnd() {
        val value = _splitDrag.value ?: return
        // Ohne "Position merken" gilt der Wert nur bis zum Neustart der App
        if (!settings.value.rememberSplit) return
        viewModelScope.launch {
            settingsStore.setSplit(value)
            // Erst freigeben, wenn der gespeicherte Wert angekommen ist – sonst springt die Linie kurz zurück
            withTimeoutOrNull(1000) { settings.first { abs(it.splitFraction - value) < 0.001f } }
            if (_splitDrag.value == value) _splitDrag.value = null
        }
    }

    fun resetSplit() {
        _splitDrag.value = null
        viewModelScope.launch { settingsStore.setSplit(AppSettings.DEFAULT_SPLIT) }
    }

    // ---------------------------------------------------------------- Verfassen

    private val _draft = MutableStateFlow<DraftEntity?>(null)
    val draft: StateFlow<DraftEntity?> = _draft.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private var draftSaveJob: Job? = null

    fun compose(mode: String = ComposeMode.NEW, source: MessageEntity? = null) = viewModelScope.launch {
        val accs = accounts.value
        if (accs.isEmpty()) {
            toast("Bitte zuerst ein E-Mail-Konto hinzufügen")
            return@launch
        }
        val account = source?.let { s -> accs.firstOrNull { it.id == s.accountId } }
            ?: (mailbox.value as? Mailbox.Folder)?.let { mb ->
                val f = folders.value.firstOrNull { it.id == mb.id }
                accs.firstOrNull { it.id == f?.accountId }
            }
            ?: accs.first()

        if (source != null && !source.bodyLoaded) runCatching { repo.loadBody(source.id) }
        val src = source?.let { repo.getMessage(it.id) ?: it }

        val signature = if (account.signature.isNotBlank()) "\n\n-- \n${account.signature}" else ""
        val draft = when (mode) {
            ComposeMode.REPLY, ComposeMode.REPLY_ALL -> {
                src!!
                val to = src.replyTo?.takeIf { it.isNotBlank() } ?: formatAddress(src.fromName, src.fromAddress)
                val cc = if (mode == ComposeMode.REPLY_ALL) {
                    (splitAddresses(src.toList) + splitAddresses(src.ccList))
                        .filterNot { it.contains(account.email, ignoreCase = true) }
                        .filterNot { to.contains(extractEmail(it), ignoreCase = true) }
                        .distinct().joinToString(", ")
                } else ""
                newDraft(account, mode, src.id, to, cc, prefixed("Re:", src.subject), signature + "\n\n" + quote(src))
            }
            ComposeMode.FORWARD -> {
                src!!
                newDraft(account, mode, src.id, "", "", prefixed("Fwd:", src.subject), signature + "\n\n" + forwardBlock(src))
            }
            else -> newDraft(account, ComposeMode.NEW, null, "", "", "", signature)
        }
        val id = repo.saveDraft(draft)
        _draft.value = draft.copy(id = id)
        push(Screen.Compose)
    }

    fun resumeDraft(d: DraftEntity) {
        _draft.value = d
        push(Screen.Compose)
    }

    private fun newDraft(
        account: AccountEntity, mode: String, sourceId: Long?, to: String, cc: String,
        subject: String, body: String
    ) = DraftEntity(
        accountId = account.id, mode = mode, sourceMessageId = sourceId,
        toField = to, ccField = cc, bccField = "", subject = subject, body = body,
        attachmentUris = "", outgoingMessageId = repo.newMessageId(account),
        updatedAt = System.currentTimeMillis()
    )

    fun updateDraft(transform: (DraftEntity) -> DraftEntity) {
        val current = _draft.value ?: return
        val updated = transform(current)
        _draft.value = updated
        draftSaveJob?.cancel()
        draftSaveJob = viewModelScope.launch {
            delay(600)
            repo.saveDraft(updated)
        }
    }

    fun changeDraftAccount(accountId: Long) = viewModelScope.launch {
        val acc = accounts.value.firstOrNull { it.id == accountId } ?: return@launch
        updateDraft { it.copy(accountId = acc.id, outgoingMessageId = repo.newMessageId(acc)) }
    }

    fun attachmentName(uri: String): String = repo.displayNameOf(android.net.Uri.parse(uri))

    fun send() {
        val d = _draft.value ?: return
        if (_sending.value) return
        viewModelScope.launch {
            _sending.value = true
            draftSaveJob?.cancel()
            try {
                repo.saveDraft(d)
                repo.send(d)
                _draft.value = null
                popCompose()
                toast("Gesendet")
            } catch (e: Exception) {
                toast("Senden fehlgeschlagen – Entwurf bleibt gespeichert.\n${e.friendlyMessage()}")
            } finally {
                _sending.value = false
            }
        }
    }

    fun keepDraftAndClose() {
        val d = _draft.value
        draftSaveJob?.cancel()
        viewModelScope.launch {
            if (d != null) repo.saveDraft(d)
            _draft.value = null
            popCompose()
            if (d != null) toast("Entwurf gesichert")
        }
    }

    fun discardDraft() {
        val d = _draft.value
        draftSaveJob?.cancel()
        viewModelScope.launch {
            if (d != null) repo.deleteDraft(d.id)
            _draft.value = null
            popCompose()
        }
    }

    private fun popCompose() {
        val idx = screens.indexOfLast { it == Screen.Compose }
        if (idx > 0) screens.removeAt(idx)
    }

    val drafts: StateFlow<List<DraftEntity>> = repo.observeDrafts()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun deleteDraft(id: Long) = viewModelScope.launch { repo.deleteDraft(id) }

    // ---------------------------------------------------------------- Konten

    private val _addState = MutableStateFlow(AddAccountState())
    val addState: StateFlow<AddAccountState> = _addState.asStateFlow()

    fun addAccount(input: NewAccountInput) {
        if (_addState.value.busy) return
        viewModelScope.launch {
            _addState.value = AddAccountState(busy = true)
            try {
                repo.addAccount(input)
                _addState.value = AddAccountState()
                if (currentScreen == Screen.AddAccount) pop()
                selectMailbox(Mailbox.AllInboxes)
                toast("Konto hinzugefügt")
            } catch (e: Exception) {
                val rejected = e is javax.mail.AuthenticationFailedException ||
                    (e.cause is javax.mail.AuthenticationFailedException)
                val msg = if (e is de.famail.app.data.repo.SmtpSetupException)
                    "Empfang klappt, aber der Postausgang (SMTP) nicht:\n" + (e.cause ?: e).friendlyMessage()
                else e.friendlyMessage()
                _addState.value = AddAccountState(error = msg, loginRejected = rejected)
            }
        }
    }

    fun clearAddError() { _addState.value = _addState.value.copy(error = null) }

    fun removeAccount(account: AccountEntity) = viewModelScope.launch {
        repo.removeAccount(account.id)
        _selectedId.value = null
        _mailbox.value = Mailbox.AllInboxes
        toast("Konto ${account.email} entfernt")
    }

    fun updateAccount(account: AccountEntity) = viewModelScope.launch { repo.updateAccount(account) }

    // ---------------------------------------------------------------- Einstellungen

    fun setTheme(m: ThemeMode) = viewModelScope.launch { settingsStore.setTheme(m) }
    fun setRememberSplit(v: Boolean) = viewModelScope.launch { settingsStore.setRememberSplit(v) }
    fun setLoadRemoteImages(v: Boolean) = viewModelScope.launch { settingsStore.setLoadRemoteImages(v) }
    fun setSyncInterval(m: Int) = viewModelScope.launch { settingsStore.setSyncInterval(m) }
    fun setNotifications(v: Boolean) = viewModelScope.launch { settingsStore.setNotifications(v) }
    fun setNotifyOnlyImportant(v: Boolean) = viewModelScope.launch { settingsStore.setNotifyOnlyImportant(v) }
    fun setHideLockscreen(v: Boolean) = viewModelScope.launch { settingsStore.setHideLockscreen(v) }
    fun setFontScale(v: Float) = viewModelScope.launch { settingsStore.setFontScale(v) }

    // ---------------------------------------------------------------- Hilfen

    companion object {
        fun folderLabel(f: FolderEntity): String = when (f.role) {
            FolderRole.INBOX -> "Posteingang"
            FolderRole.SENT -> "Gesendet"
            FolderRole.DRAFTS -> "Entwürfe"
            FolderRole.TRASH -> "Papierkorb"
            FolderRole.SPAM -> "Spam"
            FolderRole.ARCHIVE -> "Archiv"
            else -> f.displayName
        }

        fun formatAddress(name: String, address: String) =
            if (name.isBlank() || name == address) address else "$name <$address>"

        fun extractEmail(s: String): String =
            Regex("<([^>]+)>").find(s)?.groupValues?.get(1)?.trim() ?: s.trim()

        fun splitAddresses(s: String): List<String> =
            s.split(Regex(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")).map { it.trim() }.filter { it.isNotEmpty() }

        private fun prefixed(prefix: String, subject: String): String {
            val s = subject.trim()
            return if (s.startsWith(prefix, ignoreCase = true) ||
                (prefix == "Re:" && s.startsWith("AW:", ignoreCase = true)) ||
                (prefix == "Fwd:" && (s.startsWith("WG:", true) || s.startsWith("Fw:", true)))
            ) s else "$prefix $s"
        }

        private fun bodyAsText(m: MessageEntity): String =
            m.bodyText ?: m.bodyHtml?.let { de.famail.app.data.mail.MessageParser.htmlToText(it) } ?: m.preview

        private fun quote(m: MessageEntity): String {
            val date = SimpleDateFormat("dd.MM.yyyy 'um' HH:mm", Locale.GERMANY).format(m.dateMillis)
            val quoted = bodyAsText(m).lines().joinToString("\n") { "> $it" }
            return "Am $date schrieb ${formatAddress(m.fromName, m.fromAddress)}:\n$quoted"
        }

        private fun forwardBlock(m: MessageEntity): String {
            val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(m.dateMillis)
            return buildString {
                append("---------- Weitergeleitete Nachricht ----------\n")
                append("Von: ${formatAddress(m.fromName, m.fromAddress)}\n")
                append("Datum: $date\n")
                append("Betreff: ${m.subject}\n")
                append("An: ${m.toList}\n")
                if (m.ccList.isNotBlank()) append("Cc: ${m.ccList}\n")
                append("\n")
                append(bodyAsText(m))
            }
        }
    }
}
