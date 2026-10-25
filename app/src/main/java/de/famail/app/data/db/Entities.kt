package de.famail.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Ein E-Mail-Konto. Das Passwort liegt nur verschlüsselt (Android Keystore) in [encryptedPassword]. */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val email: String,
    val displayName: String,
    val providerKey: String,
    val imapHost: String,
    val imapPort: Int,
    val imapSecurity: String,
    val smtpHost: String,
    val smtpPort: Int,
    val smtpSecurity: String,
    val username: String,
    val encryptedPassword: String,
    val signature: String = "",
    val colorIndex: Int = 0,
    val lastSync: Long = 0
)

object FolderRole {
    const val INBOX = "INBOX"
    const val SENT = "SENT"
    const val DRAFTS = "DRAFTS"
    const val ARCHIVE = "ARCHIVE"
    const val TRASH = "TRASH"
    const val SPAM = "SPAM"
    const val FLAGGED = "FLAGGED"
    const val OTHER = "OTHER"

    val order = listOf(INBOX, FLAGGED, DRAFTS, SENT, ARCHIVE, SPAM, TRASH, OTHER)
}

@Entity(
    tableName = "folders",
    indices = [Index(value = ["accountId", "fullName"], unique = true)]
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val fullName: String,
    val displayName: String,
    val role: String,
    val uidValidity: Long = 0,
    val unreadCount: Int = 0,
    val lastSync: Long = 0
)

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["folderId", "uid"], unique = true),
        Index(value = ["accountId"]),
        Index(value = ["dateMillis"])
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val folderId: Long,
    val uid: Long,
    val messageId: String?,
    val inReplyTo: String?,
    val referencesHeader: String?,
    val fromName: String,
    val fromAddress: String,
    val toList: String,
    val ccList: String,
    val replyTo: String?,
    val subject: String,
    val preview: String,
    val dateMillis: Long,
    val isRead: Boolean,
    val isFlagged: Boolean,
    val isAnswered: Boolean,
    val hasAttachments: Boolean,
    val bodyText: String? = null,
    val bodyHtml: String? = null,
    val bodyLoaded: Boolean = false,
    /** JSON-Array von [de.famail.app.data.mail.AttachmentInfo] */
    val attachmentsJson: String? = null
)

/** Lokal gespeicherter Entwurf (Verfassen, Antworten, Weiterleiten). */
@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val mode: String,
    val sourceMessageId: Long?,
    val toField: String,
    val ccField: String,
    val bccField: String,
    val subject: String,
    val body: String,
    /** Zeilengetrennte content://-URIs lokaler Anhänge */
    val attachmentUris: String,
    /** Einmal erzeugte Message-ID: verhindert doppelte Sendungen desselben Entwurfs */
    val outgoingMessageId: String,
    val updatedAt: Long
)
