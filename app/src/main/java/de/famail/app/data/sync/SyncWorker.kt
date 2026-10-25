package de.famail.app.data.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.famail.app.FaMailApp
import de.famail.app.MainActivity
import de.famail.app.R
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as FaMailApp
        return try {
            val fresh = app.container.repository.syncAll()
            val settings = app.container.settings.settings.first()
            if (settings.notificationsEnabled) {
                val toNotify = if (settings.notifyOnlyImportant) fresh.filter { it.second.isFlagged } else fresh
                toNotify.take(6).forEach { (account, msg) ->
                    Notifications.newMail(
                        applicationContext, msg.id, account.email, msg.fromName, msg.subject,
                        settings.hideContentOnLockscreen
                    )
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val NAME = "famail-sync"

        fun schedule(context: Context, intervalMinutes: Int) {
            val wm = WorkManager.getInstance(context)
            if (intervalMinutes <= 0) {
                wm.cancelUniqueWork(NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<SyncWorker>(intervalMinutes.toLong().coerceAtLeast(15), TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}

object Notifications {
    const val CHANNEL_NEW_MAIL = "new_mail"
    const val EXTRA_MESSAGE_ID = "open_message_id"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_NEW_MAIL,
                context.getString(R.string.channel_new_mail),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun newMail(
        context: Context,
        messageId: Long,
        accountEmail: String,
        from: String,
        subject: String,
        hideContent: Boolean
    ) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_MESSAGE_ID, messageId)
        }
        val pending = PendingIntent.getActivity(
            context, messageId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_NEW_MAIL)
            .setSmallIcon(android.R.drawable.sym_action_email)
            .setContentTitle("Neue E-Mail")
            .setContentText(accountEmail)
            .build()
        val n = NotificationCompat.Builder(context, CHANNEL_NEW_MAIL)
            .setSmallIcon(android.R.drawable.sym_action_email)
            .setContentTitle(from)
            .setContentText(subject)
            .setSubText(accountEmail)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setVisibility(if (hideContent) NotificationCompat.VISIBILITY_PRIVATE else NotificationCompat.VISIBILITY_PUBLIC)
            .setPublicVersion(publicVersion)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(messageId.toInt(), n) }
    }
}
