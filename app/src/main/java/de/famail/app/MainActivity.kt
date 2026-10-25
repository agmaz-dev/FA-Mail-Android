package de.famail.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import de.famail.app.data.sync.Notifications
import de.famail.app.ui.MailApp
import de.famail.app.ui.MainViewModel

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent { MailApp(vm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val id = intent?.getLongExtra(Notifications.EXTRA_MESSAGE_ID, -1L) ?: -1L
        if (id > 0) {
            vm.openMessage(id)
            intent?.removeExtra(Notifications.EXTRA_MESSAGE_ID)
        }
    }
}
