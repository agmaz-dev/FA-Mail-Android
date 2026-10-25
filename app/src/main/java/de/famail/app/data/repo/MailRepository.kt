package de.famail.app.data.repo

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import de.famail.app.data.db.AccountEntity
import de.famail.app.data.db.AppDatabase
import de.famail.app.data.db.DraftEntity
import de.famail.app.data.db.FolderEntity
import de.famail.app.data.db.FolderRole
import de.famail.app.data.db.MessageEntity
import de.famail.app.data.mail.AttachmentInfo
import de.famail.app.data.mail.MailClient
import de.famail.app.data.mail.MessageParser
import de.famail.app.data.mail.ServerConfig
import de.famail.app.data.security.CredentialCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.mail.Flags
import javax.mail.Message
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

object ComposeMode {
    const val NEW = "NEW"
    const val REPLY = "REPLY"
    const val REPLY_ALL = "REPLY_ALL"
    const val FORWARD = "FORWARD"
}

data class NewAccountInput(
    val email: String,
    val displayName: String,
    val providerKey: String,
    val username: String,
    val password: String,
    val imapHost: String,
    val imapPort: Int,
    val imapSecurity: String,
    val smtpHost: String,
    val smtpPort: Int,
    val smtpSecurity: String
) {
    fun toConfig() = ServerConfig(
        email, displayName, username, password,
        imapHost, imapPort, imapSecurity, smtpHost, smtpPort, smtpSecurity
    )
}

/** Hält die Message-ID fest, damit ein erneuter Versuch dieselbe Nachricht-Kennung trägt. */
private class FixedIdMimeMessage(session: Session, private val fixedId: String) : MimeMessage(session) {
    override fun updateMessageID() {
        setHeader("Message-ID", fixedId)
    }
}

class MailRepository(private val context: Context, private val db: AppDatabase) {

    private val syncLocks = ConcurrentHashMap<Long, Mutex>()
    private fun lockFor(accountId: Long) = syncLocks.getOrPut(accountId) { Mutex() }

    val accounts = db.accounts().observeAll()
    val folders = db.folders().observeAll()

    fun observeAllInboxes(q: String) = db.messages().observeAllInboxes(likeOf(q))
    fun observeFolder(folderId: Long, q: String) = db.messages().observeFolder(folderId, likeOf(q))
    fun observeFlagged(q: String) = db.messages().observeFlagged(likeOf(q))
    fun observeMessage(id: Long) = db.messages().observe(id)

    private fun likeOf(q: String) = if (q.isBlank()) "" else "%${q.trim()}%"

    private fun clientFor(account: AccountEntity): MailClient = MailClient(
        ServerConfig(
            email = account.email,
            displayName = account.displayName,
            username = account.username,
            password = CredentialCipher.decrypt(account.encryptedPassword),
            imapHost = account.imapHost,
            imapPort = account.imapPort,
            imapSecurity = account.imapSecurity,
            smtpHost = account.smtpHost,
            smtpPort = account.smtpPort,
            smtpSecurity = account.smtpSecurity
        )
    )

    // ------------------------------------------------------------------ Konten

    suspend fun testAccount(input: NewAccountInput) = withContext(Dispatchers.IO) {
        MailClient(input.toConfig()).testConnection()
    }

    suspend fun addAccount(input: NewAccountInput): Long = withContext(Dispatchers.IO) {
        MailClient(input.toConfig()).testConnection()
        val count = db.accounts().getAll().size
        val id = db.accounts().insert(
            AccountEntity(
                email = input.email.trim(),
                displayName = input.displayName.trim().ifBlank { input.email.substringBefore('@') },
                providerKey = input.providerKey,
                imapHost = input.imapHost.trim(),
                imapPort = input.imapPort,
                imapSecurity = input.imapSecurity,
                smtpHost = input.smtpHost.trim(),
                smtpPort = input.smtpPort,
                smtpSecurity = input.smtpSecurity,
                username = input.username.trim(),
                encryptedPassword = CredentialCipher.encrypt(input.password),
                colorIndex = count
            )
        )
        val account = db.accounts().get(id)!!
        refreshFolders(account)
        db.folders().byRole(id, FolderRole.INBOX)?.let { syncFolder(account, it) }
        id
    }

    suspend fun updateAccount(account: AccountEntity) = withContext(Dispatchers.IO) {
        db.accounts().update(account)
    }

    suspend fun removeAccount(accountId: Long) = withContext(Dispatchers.IO) {
        db.withTransaction {
            db.messages().deleteForAccount(accountId)
            db.folders().deleteForAccount(accountId)
            db.drafts().deleteForAccount(accountId)
            db.accounts().delete(accountId)
        }
        File(context.cacheDir, "attachments").deleteRecursively()
    }

    // ------------------------------------------------------------------ Synchronisierung

    private suspend fun refreshFolders(account: AccountEntity) {
        val remote = clientFor(account).listFolders()
        val local = db.folders().forAccount(account.id)
        val localByName = local.associateBy { it.fullName }
        remote.forEach { rf ->
            val existing = localByName[rf.fullName]
            if (existing == null) {
                db.folders().insert(
                    FolderEntity(accountId = account.id, fullName = rf.fullName, displayName = rf.displayName, role = rf.role)
                )
            } else if (existing.role != rf.role || existing.displayName != rf.displayName) {
                db.folders().update(existing.copy(role = rf.role, displayName = rf.displayName))
            }
        }
        val remoteNames = remote.map { it.fullName }.toSet()
        local.filter { it.fullName !in remoteNames }.forEach { gone ->
            db.messages().deleteForFolder(gone.id)
            db.folders().delete(gone)
        }
    }

    /** Gleicht einen Ordner ab und liefert die neu eingefügten Nachrichten zurück. */
    private suspend fun syncFolder(account: AccountEntity, folder: FolderEntity): List<MessageEntity> {
        val local = db.messages().forFolder(folder.id)
        val result = clientFor(account).syncFolder(
            fullName = folder.fullName,
            knownUidValidity = folder.uidValidity,
            knownUids = local.map { it.uid }.toSet()
        )
        val inserted = mutableListOf<MessageEntity>()
        db.withTransaction {
            val current = if (result.uidValidity != folder.uidValidity) {
                db.messages().deleteForFolder(folder.id)
                emptyList<MessageEntity>()
            } else local
            val byUid = current.associateBy { it.uid }
            val remoteUids = HashSet<Long>()

            for (h in result.headers) {
                remoteUids += h.uid
                val existing = byUid[h.uid]
                if (existing == null) {
                    val body = h.body
                    val entity = MessageEntity(
                        accountId = account.id,
                        folderId = folder.id,
                        uid = h.uid,
                        messageId = h.messageId,
                        inReplyTo = h.inReplyTo,
                        referencesHeader = h.references,
                        fromName = h.fromName,
                        fromAddress = h.fromAddress,
                        toList = h.to,
                        ccList = h.cc,
                        replyTo = h.replyTo,
                        subject = h.subject,
                        preview = body?.preview() ?: "",
                        dateMillis = h.dateMillis,
                        isRead = h.seen,
                        isFlagged = h.flagged,
                        isAnswered = h.answered,
                        hasAttachments = h.hasAttachments,
                        bodyText = body?.text,
                        bodyHtml = body?.html,
                        bodyLoaded = body != null,
                        attachmentsJson = body?.let { MessageParser.attachmentsToJson(it.attachments) }
                    )
                    val id = db.messages().insert(entity)
                    if (id > 0) inserted += entity.copy(id = id)
                } else if (existing.isRead != h.seen || existing.isFlagged != h.flagged || existing.isAnswered != h.answered) {
                    db.messages().updateFlags(existing.id, h.seen, h.flagged, h.answered)
                }
            }

            // Auf dem Server gelöschte oder verschobene Nachrichten lokal entfernen
            val gone = current.filter { it.uid >= result.lowestSyncedUid && it.uid !in remoteUids }.map { it.id }
            if (gone.isNotEmpty()) db.messages().deleteIds(gone)

            db.folders().update(
                folder.copy(
                    uidValidity = result.uidValidity,
                    unreadCount = result.unreadCount,
                    lastSync = System.currentTimeMillis()
                )
            )
        }
        return inserted
    }

    /** Synchronisiert Ordnerliste und Posteingang aller Konten. Liefert neue ungelesene Posteingangs-Nachrichten. */
    suspend fun syncAll(): List<Pair<AccountEntity, MessageEntity>> = withContext(Dispatchers.IO) {
        val fresh = mutableListOf<Pair<AccountEntity, MessageEntity>>()
        val errors = mutableListOf<String>()
        for (account in db.accounts().getAll()) {
            try {
                lockFor(account.id).withLock {
                    refreshFolders(account)
                    val inbox = db.folders().byRole(account.id, FolderRole.INBOX)
                    if (inbox != null) {
                        val wasSynced = inbox.lastSync > 0
                        val newOnes = syncFolder(account, inbox)
                        if (wasSynced) newOnes.filter { !it.isRead }.forEach { fresh += account to it }
                    }
                    db.accounts().setLastSync(account.id, System.currentTimeMillis())
                }
            } catch (e: Exception) {
                errors += "${account.email}: ${e.friendlyMessage()}"
            }
        }
        if (errors.isNotEmpty() && fresh.isEmpty()) throw SyncException(errors.joinToString("\n"))
        fresh
    }

    suspend fun syncFolderById(folderId: Long) = withContext(Dispatchers.IO) {
        val folder = db.folders().get(folderId) ?: return@withContext
        val account = db.accounts().get(folder.accountId) ?: return@withContext
        lockFor(account.id).withLock { syncFolder(account, folder) }
    }

    // ------------------------------------------------------------------ Nachrichten

    suspend fun loadBody(messageId: Long) = withContext(Dispatchers.IO) {
        val msg = db.messages().get(messageId) ?: return@withContext
        if (msg.bodyLoaded) return@withContext
        val folder = db.folders().get(msg.folderId) ?: return@withContext
        val account = db.accounts().get(msg.accountId) ?: return@withContext
        val body = clientFor(account).fetchBody(folder.fullName, msg.uid) ?: return@withContext
        db.messages().update(
            msg.copy(
                bodyText = body.text,
                bodyHtml = body.html,
                bodyLoaded = true,
                preview = body.preview().ifBlank { msg.preview },
                hasAttachments = body.attachments.isNotEmpty(),
                attachmentsJson = MessageParser.attachmentsToJson(body.attachments)
            )
        )
    }

    suspend fun setRead(messageId: Long, read: Boolean) = withContext(Dispatchers.IO) {
        val msg = db.messages().get(messageId) ?: return@withContext
        if (msg.isRead == read) return@withContext
        db.messages().setRead(messageId, read)
        try {
            serverFlag(msg, Flags.Flag.SEEN, read)
        } catch (e: Exception) {
            db.messages().setRead(messageId, !read)
            throw e
        }
    }

    suspend fun setFlagged(messageId: Long, flagged: Boolean) = withContext(Dispatchers.IO) {
        val msg = db.messages().get(messageId) ?: return@withContext
        db.messages().setFlagged(messageId, flagged)
        try {
            serverFlag(msg, Flags.Flag.FLAGGED, flagged)
        } catch (e: Exception) {
            db.messages().setFlagged(messageId, !flagged)
            throw e
        }
    }

    private suspend fun serverFlag(msg: MessageEntity, flag: Flags.Flag, value: Boolean) {
        val folder = db.folders().get(msg.folderId) ?: return
        val account = db.accounts().get(msg.accountId) ?: return
        clientFor(account).setFlag(folder.fullName, msg.uid, flag, value)
    }

    suspend fun moveTo(messageId: Long, targetFolderId: Long) = withContext(Dispatchers.IO) {
        val msg = db.messages().get(messageId) ?: return@withContext
        val from = db.folders().get(msg.folderId) ?: return@withContext
        val to = db.folders().get(targetFolderId) ?: return@withContext
        require(from.accountId == to.accountId) { "Verschieben ist nur innerhalb eines Kontos möglich" }
        val account = db.accounts().get(msg.accountId) ?: return@withContext
        clientFor(account).move(from.fullName, msg.uid, to.fullName)
        db.messages().delete(messageId)
    }

    /** Verschiebt in den Papierkorb bzw. löscht endgültig, wenn die Nachricht schon dort liegt. */
    suspend fun delete(messageId: Long) = withContext(Dispatchers.IO) {
        val msg = db.messages().get(messageId) ?: return@withContext
        val folder = db.folders().get(msg.folderId) ?: return@withContext
        val account = db.accounts().get(msg.accountId) ?: return@withContext
        val trash = db.folders().byRole(account.id, FolderRole.TRASH)
        val client = clientFor(account)
        if (trash != null && trash.id != folder.id) {
            client.move(folder.fullName, msg.uid, trash.fullName)
        } else {
            client.deletePermanently(folder.fullName, msg.uid)
        }
        db.messages().delete(messageId)
    }

    /** Archiviert, wenn das Konto einen Archivordner hat. Gibt false zurück, wenn keiner existiert. */
    suspend fun archive(messageId: Long): Boolean = withContext(Dispatchers.IO) {
        val msg = db.messages().get(messageId) ?: return@withContext false
        val archive = db.folders().byRole(msg.accountId, FolderRole.ARCHIVE) ?: return@withContext false
        if (archive.id == msg.folderId) return@withContext true
        moveTo(messageId, archive.id)
        true
    }

    suspend fun downloadAttachment(messageId: Long, att: AttachmentInfo): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "attachments/$messageId")
        val dest = File(dir, safeFileName(att.fileName))
        if (dest.exists() && dest.length() > 0) return@withContext dest
        val msg = db.messages().get(messageId) ?: throw IllegalStateException("Nachricht nicht gefunden")
        val folder = db.folders().get(msg.folderId) ?: throw IllegalStateException("Ordner nicht gefunden")
        val account = db.accounts().get(msg.accountId) ?: throw IllegalStateException("Konto nicht gefunden")
        val tmp = File(dir, dest.name + ".part")
        clientFor(account).downloadPart(folder.fullName, msg.uid, att.path, tmp)
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
        dest
    }

    // ------------------------------------------------------------------ Entwürfe & Versand

    fun observeDrafts() = db.drafts().observeAll()

    suspend fun saveDraft(draft: DraftEntity): Long = withContext(Dispatchers.IO) {
        db.drafts().upsert(draft.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteDraft(id: Long) = withContext(Dispatchers.IO) { db.drafts().delete(id) }

    suspend fun getDraft(id: Long) = withContext(Dispatchers.IO) { db.drafts().get(id) }

    suspend fun getMessage(id: Long) = withContext(Dispatchers.IO) { db.messages().get(id) }

    suspend fun getAccount(id: Long) = withContext(Dispatchers.IO) { db.accounts().get(id) }

    fun newMessageId(account: AccountEntity): String {
        val domain = account.email.substringAfter('@', "famail.local")
        return "<${UUID.randomUUID()}@$domain>"
    }

    /**
     * Versendet einen Entwurf. Erst wenn der SMTP-Server die Nachricht angenommen hat,
     * wird der Entwurf gelöscht. Bei einem Fehler bleibt er samt Anhängen erhalten.
     */
    suspend fun send(draft: DraftEntity) = withContext(Dispatchers.IO) {
        val account = db.accounts().get(draft.accountId) ?: throw IllegalStateException("Absenderkonto fehlt")
        val to = normalizeAddresses(draft.toField)
        require(to.isNotBlank()) { "Bitte mindestens einen Empfänger angeben" }
        val toAddrs = InternetAddress.parse(to, false)
        val ccAddrs = normalizeAddresses(draft.ccField).takeIf { it.isNotBlank() }?.let { InternetAddress.parse(it, false) }
        val bccAddrs = normalizeAddresses(draft.bccField).takeIf { it.isNotBlank() }?.let { InternetAddress.parse(it, false) }

        val source = draft.sourceMessageId?.let { db.messages().get(it) }
        val outDir = File(context.cacheDir, "outgoing/${draft.id}").apply { mkdirs() }
        val files = mutableListOf<File>()

        draft.attachmentUris.lines().filter { it.isNotBlank() }.forEach { s ->
            files += copyUriToFile(Uri.parse(s), outDir)
        }
        if (draft.mode == ComposeMode.FORWARD && source != null) {
            MessageParser.attachmentsFromJson(source.attachmentsJson).forEach { att ->
                files += downloadAttachment(source.id, att)
            }
        }

        val client = clientFor(account)
        val sent = client.send { session ->
            FixedIdMimeMessage(session, draft.outgoingMessageId).apply {
                setFrom(InternetAddress(account.email, account.displayName, "UTF-8"))
                setRecipients(Message.RecipientType.TO, toAddrs)
                ccAddrs?.let { setRecipients(Message.RecipientType.CC, it) }
                bccAddrs?.let { setRecipients(Message.RecipientType.BCC, it) }
                setSubject(draft.subject, "UTF-8")
                sentDate = Date()
                setHeader("X-Mailer", "FA Mail")
                if (source?.messageId != null &&
                    (draft.mode == ComposeMode.REPLY || draft.mode == ComposeMode.REPLY_ALL)
                ) {
                    setHeader("In-Reply-To", source.messageId)
                    setHeader("References", listOfNotNull(source.referencesHeader, source.messageId).joinToString(" "))
                }
                if (files.isEmpty()) {
                    setText(draft.body, "UTF-8")
                } else {
                    val mp = MimeMultipart()
                    mp.addBodyPart(MimeBodyPart().apply { setText(draft.body, "UTF-8") })
                    files.forEach { f -> mp.addBodyPart(MimeBodyPart().apply { attachFile(f) }) }
                    setContent(mp)
                }
            }
        }

        // Ab hier gilt die Nachricht als versendet; Folgeschritte dürfen den Erfolg nicht mehr kippen.
        db.drafts().delete(draft.id)
        outDir.deleteRecursively()

        if (source != null && (draft.mode == ComposeMode.REPLY || draft.mode == ComposeMode.REPLY_ALL)) {
            runCatching {
                serverFlag(source, Flags.Flag.ANSWERED, true)
                db.messages().updateFlags(source.id, source.isRead, source.isFlagged, true)
            }
        }
        if (!MailClient.serverStoresSentCopy(account.smtpHost)) {
            db.folders().byRole(account.id, FolderRole.SENT)?.let { sentFolder ->
                runCatching { client.append(sentFolder.fullName, sent) }
            }
        }
    }

    private fun normalizeAddresses(s: String) =
        s.replace(';', ',').split(',').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")

    private fun copyUriToFile(uri: Uri, dir: File): File {
        val resolver = context.contentResolver
        var name = "Anhang"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { name = it }
        }
        val dest = File(dir, safeFileName(name))
        resolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
            ?: throw IllegalStateException("Anhang \"$name\" kann nicht gelesen werden")
        return dest
    }

    fun displayNameOf(uri: Uri): String {
        var name = uri.lastPathSegment ?: "Anhang"
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.let { name = it }
            }
        }
        return name
    }

    private fun safeFileName(name: String) =
        name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().ifBlank { "Anhang" }.take(120)
}

class SyncException(message: String) : Exception(message)

fun Throwable.friendlyMessage(): String {
    val msg = (message ?: "").lowercase()
    val cls = this::class.java.simpleName
    return when {
        cls.contains("AuthenticationFailed") || msg.contains("authentication") || msg.contains("auth") && msg.contains("fail") ->
            "Anmeldung abgelehnt – E-Mail, Benutzername oder (App-)Passwort prüfen."
        cls.contains("UnknownHost") || msg.contains("unknownhost") || msg.contains("unable to resolve") ->
            "Server nicht gefunden – Internetverbindung und Servername prüfen."
        cls.contains("SocketTimeout") || msg.contains("timed out") || msg.contains("timeout") ->
            "Zeitüberschreitung – der Server antwortet nicht."
        cls.contains("ConnectException") || msg.contains("couldn't connect") || msg.contains("connection refused") ->
            "Verbindung fehlgeschlagen – Port und Verschlüsselung prüfen."
        cls.contains("SSL") || msg.contains("ssl") || msg.contains("certificate") ->
            "Sichere Verbindung fehlgeschlagen – Verschlüsselung (SSL/STARTTLS) prüfen."
        cls.contains("AddressException") -> "Ungültige E-Mail-Adresse: ${message ?: ""}"
        cls.contains("SendFailed") -> "Versand abgelehnt: ${message ?: ""}"
        else -> message ?: cls
    }
}
