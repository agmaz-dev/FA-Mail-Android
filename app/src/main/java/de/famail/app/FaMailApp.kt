package de.famail.app

import android.app.Application
import de.famail.app.data.db.AppDatabase
import de.famail.app.data.repo.MailRepository
import de.famail.app.data.settings.SettingsStore
import de.famail.app.data.sync.Notifications
import de.famail.app.data.sync.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AppContainer(app: Application) {
    val database = AppDatabase.create(app)
    val settings = SettingsStore(app)
    val repository = MailRepository(app, database)
}

class FaMailApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Umlaute in Anhangnamen korrekt kodieren (RFC 2231)
        System.setProperty("mail.mime.encodefilename", "true")
        System.setProperty("mail.mime.decodefilename", "true")
        System.setProperty("mail.mime.encodeparameters", "true")

        container = AppContainer(this)
        Notifications.createChannel(this)

        appScope.launch {
            container.settings.settings
                .map { it.syncIntervalMinutes }
                .distinctUntilChanged()
                .collect { SyncWorker.schedule(this@FaMailApp, it) }
        }
    }
}
