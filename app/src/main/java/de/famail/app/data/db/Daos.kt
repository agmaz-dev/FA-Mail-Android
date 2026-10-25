package de.famail.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY id")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY id")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: Long): AccountEntity?

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Query("UPDATE accounts SET lastSync = :time WHERE id = :id")
    suspend fun setLastSync(id: Long, time: Long)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY accountId, displayName COLLATE NOCASE")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE accountId = :accountId")
    suspend fun forAccount(accountId: Long): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun get(id: Long): FolderEntity?

    @Query("SELECT * FROM folders WHERE accountId = :accountId AND role = :role LIMIT 1")
    suspend fun byRole(accountId: Long, role: String): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(folder: FolderEntity): Long

    @Update
    suspend fun update(folder: FolderEntity)

    @Delete
    suspend fun delete(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE accountId = :accountId")
    suspend fun deleteForAccount(accountId: Long)
}

@Dao
interface MessageDao {
    /** Alle Posteingänge aller Konten */
    @Query(
        """
        SELECT m.* FROM messages m JOIN folders f ON m.folderId = f.id
        WHERE f.role = 'INBOX' AND (:q = '' OR m.subject LIKE :q OR m.fromName LIKE :q
              OR m.fromAddress LIKE :q OR m.preview LIKE :q OR m.toList LIKE :q)
        ORDER BY m.dateMillis DESC
        """
    )
    fun observeAllInboxes(q: String): Flow<List<MessageEntity>>

    @Query(
        """
        SELECT * FROM messages
        WHERE folderId = :folderId AND (:q = '' OR subject LIKE :q OR fromName LIKE :q
              OR fromAddress LIKE :q OR preview LIKE :q OR toList LIKE :q)
        ORDER BY dateMillis DESC
        """
    )
    fun observeFolder(folderId: Long, q: String): Flow<List<MessageEntity>>

    @Query(
        """
        SELECT * FROM messages
        WHERE isFlagged = 1 AND (:q = '' OR subject LIKE :q OR fromName LIKE :q
              OR fromAddress LIKE :q OR preview LIKE :q)
        ORDER BY dateMillis DESC
        """
    )
    fun observeFlagged(q: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id")
    fun observe(id: Long): Flow<MessageEntity?>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun get(id: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE folderId = :folderId")
    suspend fun forFolder(folderId: Long): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    @Query("UPDATE messages SET isRead = :read, isFlagged = :flagged, isAnswered = :answered WHERE id = :id")
    suspend fun updateFlags(id: Long, read: Boolean, flagged: Boolean, answered: Boolean)

    @Query("UPDATE messages SET isRead = :read WHERE id = :id")
    suspend fun setRead(id: Long, read: Boolean)

    @Query("UPDATE messages SET isFlagged = :flagged WHERE id = :id")
    suspend fun setFlagged(id: Long, flagged: Boolean)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM messages WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<Long>)

    @Query("DELETE FROM messages WHERE folderId = :folderId")
    suspend fun deleteForFolder(folderId: Long)

    @Query("DELETE FROM messages WHERE accountId = :accountId")
    suspend fun deleteForAccount(accountId: Long)
}

@Dao
interface DraftDao {
    @Query("SELECT * FROM drafts ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DraftEntity>>

    @Query("SELECT * FROM drafts WHERE id = :id")
    suspend fun get(id: Long): DraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: DraftEntity): Long

    @Query("DELETE FROM drafts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM drafts WHERE accountId = :accountId")
    suspend fun deleteForAccount(accountId: Long)
}
