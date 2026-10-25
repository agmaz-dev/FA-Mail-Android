package de.famail.app.data.mail

import com.sun.mail.imap.IMAPFolder
import com.sun.mail.imap.IMAPStore
import de.famail.app.data.db.FolderRole
import java.io.File
import java.util.Properties
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.Session
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import kotlin.math.max

data class ServerConfig(
    val email: String,
    val displayName: String,
    val username: String,
    val password: String,
    val imapHost: String,
    val imapPort: Int,
    val imapSecurity: String,
    val smtpHost: String,
    val smtpPort: Int,
    val smtpSecurity: String
)

data class RemoteFolder(val fullName: String, val displayName: String, val role: String)

data class RemoteHeader(
    val uid: Long,
    val messageId: String?,
    val inReplyTo: String?,
    val references: String?,
    val fromName: String,
    val fromAddress: String,
    val to: String,
    val cc: String,
    val replyTo: String?,
    val subject: String,
    val dateMillis: Long,
    val seen: Boolean,
    val flagged: Boolean,
    val answered: Boolean,
    val hasAttachments: Boolean,
    val body: ParsedBody?
)

data class FolderSyncResult(
    val uidValidity: Long,
    val unreadCount: Int,
    val headers: List<RemoteHeader>,
    /** Kleinste UID im abgeglichenen Bereich – ältere lokale Nachrichten bleiben unangetastet */
    val lowestSyncedUid: Long
)

/**
 * Direkter IMAP-/SMTP-Zugriff über JavaMail. Alle Methoden blockieren und
 * dürfen nur auf einem IO-Thread aufgerufen werden.
 */
class MailClient(private val cfg: ServerConfig) {

    private val imapProtocol = if (cfg.imapSecurity == Security.SSL) "imaps" else "imap"
    private val smtpProtocol = if (cfg.smtpSecurity == Security.SSL) "smtps" else "smtp"

    private fun imapSession(): Session {
        val p = Properties()
        val pr = imapProtocol
        p.setProperty("mail.store.protocol", pr)
        p.setProperty("mail.$pr.host", cfg.imapHost)
        p.setProperty("mail.$pr.port", cfg.imapPort.toString())
        p.setProperty("mail.$pr.connectiontimeout", "20000")
        p.setProperty("mail.$pr.timeout", "40000")
        p.setProperty("mail.$pr.writetimeout", "40000")
        p.setProperty("mail.$pr.partialfetch", "false")
        p.setProperty("mail.$pr.peek", "true")
        p.setProperty("mail.$pr.ssl.checkserveridentity", "true")
        if (cfg.imapSecurity == Security.STARTTLS) {
            p.setProperty("mail.imap.starttls.enable", "true")
            p.setProperty("mail.imap.starttls.required", "true")
        }
        p.setProperty("mail.mime.address.strict", "false")
        p.setProperty("mail.mime.decodetext.strict", "false")
        return Session.getInstance(p)
    }

    private fun smtpSession(): Session {
        val p = Properties()
        val pr = smtpProtocol
        p.setProperty("mail.transport.protocol", pr)
        p.setProperty("mail.$pr.host", cfg.smtpHost)
        p.setProperty("mail.$pr.port", cfg.smtpPort.toString())
        p.setProperty("mail.$pr.auth", "true")
        p.setProperty("mail.$pr.connectiontimeout", "20000")
        p.setProperty("mail.$pr.timeout", "60000")
        p.setProperty("mail.$pr.writetimeout", "60000")
        p.setProperty("mail.$pr.ssl.checkserveridentity", "true")
        if (cfg.smtpSecurity == Security.STARTTLS) {
            p.setProperty("mail.smtp.starttls.enable", "true")
            p.setProperty("mail.smtp.starttls.required", "true")
        }
        p.setProperty("mail.mime.charset", "UTF-8")
        return Session.getInstance(p)
    }

    private fun <T> withStore(block: (IMAPStore) -> T): T {
        val store = imapSession().getStore(imapProtocol) as IMAPStore
        store.connect(cfg.imapHost, cfg.imapPort, cfg.username, cfg.password)
        try {
            return block(store)
        } finally {
            runCatching { store.close() }
        }
    }

    private fun <T> withFolder(fullName: String, mode: Int, block: (IMAPFolder, IMAPStore) -> T): T =
        withStore { store ->
            val folder = store.getFolder(fullName) as IMAPFolder
            folder.open(mode)
            try {
                block(folder, store)
            } finally {
                runCatching { if (folder.isOpen) folder.close(false) }
            }
        }

    /** Prüft IMAP- und SMTP-Anmeldung. Wirft eine Exception mit Fehlertext, wenn etwas nicht stimmt. */
    fun testConnection() {
        withStore { it.defaultFolder.name }
        val transport = smtpSession().getTransport(smtpProtocol)
        transport.connect(cfg.smtpHost, cfg.smtpPort, cfg.username, cfg.password)
        transport.close()
    }

    fun listFolders(): List<RemoteFolder> = withStore { store ->
        store.defaultFolder.list("*").mapNotNull { f ->
            val folder = f as IMAPFolder
            val attrs = runCatching { folder.attributes.map { it.lowercase() } }.getOrDefault(emptyList())
            if (attrs.contains("\\noselect") || attrs.contains("\\nonexistent")) return@mapNotNull null
            if ((folder.type and Folder.HOLDS_MESSAGES) == 0) return@mapNotNull null
            RemoteFolder(folder.fullName, folder.name, roleOf(folder.fullName, folder.name, attrs))
        }
    }

    private fun roleOf(fullName: String, name: String, attrs: List<String>): String {
        val n = name.lowercase()
        return when {
            fullName.equals("INBOX", ignoreCase = true) -> FolderRole.INBOX
            "\\sent" in attrs -> FolderRole.SENT
            "\\drafts" in attrs -> FolderRole.DRAFTS
            "\\trash" in attrs -> FolderRole.TRASH
            "\\junk" in attrs -> FolderRole.SPAM
            "\\archive" in attrs -> FolderRole.ARCHIVE
            "\\all" in attrs || "\\flagged" in attrs -> FolderRole.OTHER
            n in setOf("sent", "gesendet", "gesendete objekte", "sent items", "sent messages", "gesendete elemente") -> FolderRole.SENT
            n in setOf("drafts", "entwürfe", "entwurf", "draft") -> FolderRole.DRAFTS
            n in setOf("trash", "papierkorb", "gelöscht", "gelöschte objekte", "deleted items", "deleted messages", "bin") -> FolderRole.TRASH
            n in setOf("spam", "junk", "junk-e-mail", "junk e-mail", "spamverdacht", "bulk mail") -> FolderRole.SPAM
            n in setOf("archive", "archiv", "archives") -> FolderRole.ARCHIVE
            else -> FolderRole.OTHER
        }
    }

    fun syncFolder(
        fullName: String,
        knownUidValidity: Long,
        knownUids: Set<Long>,
        limit: Int = 250,
        bodyLimit: Int = 40
    ): FolderSyncResult = withFolder(fullName, Folder.READ_ONLY) { folder, _ ->
        val uidValidity = folder.uidValidity
        val count = folder.messageCount
        if (count <= 0) return@withFolder FolderSyncResult(uidValidity, 0, emptyList(), 0)

        val start = max(1, count - limit + 1)
        val messages = folder.getMessages(start, count)
        val fp = FetchProfile().apply {
            add(FetchProfile.Item.ENVELOPE)
            add(FetchProfile.Item.FLAGS)
            add(FetchProfile.Item.CONTENT_INFO)
            add(UIDFolder.FetchProfileItem.UID)
            add("References")
        }
        folder.fetch(messages, fp)

        val sameValidity = uidValidity == knownUidValidity
        var bodiesLeft = bodyLimit
        val headers = messages.reversed().mapNotNull { m ->
            runCatching {
                val uid = folder.getUID(m)
                val isNew = !sameValidity || uid !in knownUids
                val body = if (isNew && bodiesLeft > 0) {
                    bodiesLeft--
                    runCatching { MessageParser.parse(m) }.getOrNull()
                } else null
                toHeader(m, uid, body)
            }.getOrNull()
        }
        FolderSyncResult(
            uidValidity = uidValidity,
            unreadCount = runCatching { folder.unreadCount }.getOrDefault(0),
            headers = headers,
            lowestSyncedUid = headers.minOfOrNull { it.uid } ?: 0
        )
    }

    private fun toHeader(m: Message, uid: Long, body: ParsedBody?): RemoteHeader {
        val from = m.from?.firstOrNull() as? InternetAddress
        val mime = m as? MimeMessage
        return RemoteHeader(
            uid = uid,
            messageId = runCatching { mime?.messageID }.getOrNull(),
            inReplyTo = runCatching { m.getHeader("In-Reply-To")?.firstOrNull() }.getOrNull(),
            references = runCatching { m.getHeader("References")?.firstOrNull() }.getOrNull(),
            fromName = from?.personal?.takeIf { it.isNotBlank() } ?: from?.address ?: "Unbekannt",
            fromAddress = from?.address ?: "",
            to = joinAddresses(m.getRecipients(Message.RecipientType.TO)),
            cc = joinAddresses(m.getRecipients(Message.RecipientType.CC)),
            replyTo = runCatching { joinAddresses(m.replyTo) }.getOrNull()?.takeIf { it.isNotBlank() },
            subject = runCatching { m.subject }.getOrNull()?.takeIf { it.isNotBlank() } ?: "(Kein Betreff)",
            dateMillis = (m.sentDate ?: m.receivedDate)?.time ?: System.currentTimeMillis(),
            seen = m.isSet(Flags.Flag.SEEN),
            flagged = m.isSet(Flags.Flag.FLAGGED),
            answered = m.isSet(Flags.Flag.ANSWERED),
            hasAttachments = runCatching { m.isMimeType("multipart/mixed") }.getOrDefault(false) ||
                (body?.attachments?.isNotEmpty() == true),
            body = body
        )
    }

    private fun joinAddresses(list: Array<out javax.mail.Address>?): String =
        list?.joinToString(", ") { a ->
            val ia = a as? InternetAddress
            when {
                ia == null -> a.toString()
                ia.personal.isNullOrBlank() -> ia.address
                else -> "${ia.personal} <${ia.address}>"
            }
        } ?: ""

    fun fetchBody(fullName: String, uid: Long): ParsedBody? =
        withFolder(fullName, Folder.READ_ONLY) { folder, _ ->
            val m = folder.getMessageByUID(uid) ?: return@withFolder null
            MessageParser.parse(m)
        }

    fun downloadPart(fullName: String, uid: Long, path: String, dest: File) {
        withFolder(fullName, Folder.READ_ONLY) { folder, _ ->
            val m = folder.getMessageByUID(uid) ?: throw MessagingException("Nachricht nicht mehr auf dem Server")
            val part = MessageParser.findPart(m, path)
            dest.parentFile?.mkdirs()
            part.inputStream.use { input -> dest.outputStream().use { input.copyTo(it) } }
        }
    }

    fun setFlag(fullName: String, uid: Long, flag: Flags.Flag, value: Boolean) {
        withFolder(fullName, Folder.READ_WRITE) { folder, _ ->
            folder.getMessageByUID(uid)?.setFlag(flag, value)
        }
    }

    fun move(fromFullName: String, uid: Long, toFullName: String) {
        withFolder(fromFullName, Folder.READ_WRITE) { folder, store ->
            val m = folder.getMessageByUID(uid) ?: return@withFolder
            val target = store.getFolder(toFullName)
            try {
                folder.moveMessages(arrayOf(m), target)
            } catch (e: MessagingException) {
                // Server ohne MOVE-Erweiterung: kopieren, dann im Quellordner löschen
                folder.copyMessages(arrayOf(m), target)
                m.setFlag(Flags.Flag.DELETED, true)
                folder.expunge()
            }
        }
    }

    fun deletePermanently(fullName: String, uid: Long) {
        withFolder(fullName, Folder.READ_WRITE) { folder, _ ->
            val m = folder.getMessageByUID(uid) ?: return@withFolder
            m.setFlag(Flags.Flag.DELETED, true)
            folder.expunge()
        }
    }

    /**
     * Baut die Nachricht mit einer SMTP-Session und versendet sie. Kehrt erst zurück,
     * wenn der SMTP-Server die Nachricht angenommen hat; sonst wird eine Exception geworfen.
     */
    fun send(build: (Session) -> MimeMessage): MimeMessage {
        val session = smtpSession()
        val message = build(session)
        message.saveChanges()
        val transport = session.getTransport(smtpProtocol)
        transport.connect(cfg.smtpHost, cfg.smtpPort, cfg.username, cfg.password)
        try {
            transport.sendMessage(message, message.allRecipients)
        } finally {
            runCatching { transport.close() }
        }
        return message
    }

    fun append(fullName: String, message: MimeMessage) {
        withStore { store ->
            message.setFlag(Flags.Flag.SEEN, true)
            store.getFolder(fullName).appendMessages(arrayOf(message))
        }
    }

    companion object {
        /** Diese Anbieter legen gesendete Nachrichten selbst im Ordner "Gesendet" ab. */
        fun serverStoresSentCopy(smtpHost: String): Boolean {
            val h = smtpHost.lowercase()
            return h.contains("gmail") || h.contains("office365") || h.contains("outlook") || h.contains("yahoo")
        }
    }
}
