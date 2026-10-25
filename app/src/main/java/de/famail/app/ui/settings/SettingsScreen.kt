package de.famail.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import de.famail.app.data.db.AccountEntity
import de.famail.app.data.settings.ThemeMode
import de.famail.app.ui.MainViewModel
import de.famail.app.ui.Screen
import de.famail.app.ui.components.GroupCard
import de.famail.app.ui.components.IosSwitch
import de.famail.app.ui.components.SectionHeader
import de.famail.app.ui.theme.MailTheme
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val c = MailTheme.colors
    val s by vm.settings.collectAsState()
    val accounts by vm.accounts.collectAsState()
    var editSignature by remember { mutableStateOf<AccountEntity?>(null) }
    var confirmRemove by remember { mutableStateOf<AccountEntity?>(null) }

    Box(Modifier.fillMaxSize().background(c.background), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
        ) {
            Row(Modifier.padding(start = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, null, tint = c.accent)
                    Spacer(Modifier.width(4.dp))
                    Text("Zurück", color = c.accent)
                }
            }
            Text(
                "Einstellungen",
                style = MaterialTheme.typography.headlineMedium,
                color = c.textPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )

            SectionHeader("Darstellung")
            GroupCard {
                listOf(ThemeMode.LIGHT to "Hell", ThemeMode.DARK to "Dunkel", ThemeMode.SYSTEM to "Automatisch").forEachIndexed { i, (mode, label) ->
                    if (i > 0) Divider()
                    CheckRow(label, s.themeMode == mode) { vm.setTheme(mode) }
                }
            }
            SectionHeader("Schriftgröße")
            GroupCard {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("${(s.fontScale * 100).roundToInt()} %", color = c.textPrimary)
                    Slider(
                        value = s.fontScale,
                        onValueChange = { vm.setFontScale((it * 20).roundToInt() / 20f) },
                        valueRange = 0.85f..1.3f
                    )
                }
            }

            SectionHeader("Bildschirmaufteilung (aufgeklappt)")
            GroupCard {
                SwitchRow("Letzte Position der Trennlinie merken", s.rememberSplit) { vm.setRememberSplit(it) }
                Divider()
                ActionRow("Aufteilung zurücksetzen (40 / 60)") { vm.resetSplit() }
            }
            Text(
                "Tipp: Trennlinie mit dem Finger ziehen (20–80 %). Doppeltippen setzt sie zurück.",
                style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )

            SectionHeader("E-Mail")
            GroupCard {
                SwitchRow("Externe Bilder automatisch laden", s.loadRemoteImages) { vm.setLoadRemoteImages(it) }
            }
            Text(
                "Aus: Tracking-Pixel und Fremdinhalte werden blockiert, bis du „Laden“ tippst.",
                style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )

            SectionHeader("Abruf im Hintergrund")
            GroupCard {
                listOf(15 to "Alle 15 Minuten", 30 to "Alle 30 Minuten", 60 to "Stündlich", 0 to "Nur manuell").forEachIndexed { i, (m, label) ->
                    if (i > 0) Divider()
                    CheckRow(label, s.syncIntervalMinutes == m) { vm.setSyncInterval(m) }
                }
            }

            SectionHeader("Benachrichtigungen")
            GroupCard {
                SwitchRow("Neue E-Mails melden", s.notificationsEnabled) { vm.setNotifications(it) }
                Divider()
                SwitchRow("Nur markierte (wichtige) E-Mails", s.notifyOnlyImportant) { vm.setNotifyOnlyImportant(it) }
                Divider()
                SwitchRow("Inhalt auf Sperrbildschirm verbergen", s.hideContentOnLockscreen) { vm.setHideLockscreen(it) }
            }

            SectionHeader("Konten")
            GroupCard {
                accounts.forEachIndexed { i, a ->
                    if (i > 0) Divider()
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(a.displayName, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                        Text(a.email, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        Row {
                            TextButton(onClick = { editSignature = a }) { Text("Signatur", color = c.accent) }
                            TextButton(onClick = { confirmRemove = a }) { Text("Entfernen", color = c.danger) }
                        }
                    }
                }
                if (accounts.isNotEmpty()) Divider()
                ActionRow("Konto hinzufügen") { vm.push(Screen.AddAccount) }
            }

            SectionHeader("Info")
            GroupCard {
                Column(Modifier.padding(16.dp)) {
                    Text("FA Mail 0.1 – Phase 1", color = c.textPrimary, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "E-Mail-Client mit IMAP/SMTP, Foldable-Layout und integriertem PDF-Reader. " +
                            "Formulare, Unterschriften, Sortierung und lokale KI folgen in den nächsten Phasen.",
                        color = c.textSecondary, style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    editSignature?.let { acc ->
        var text by remember(acc.id) { mutableStateOf(acc.signature) }
        AlertDialog(
            onDismissRequest = { editSignature = null },
            title = { Text("Signatur – ${acc.email}") },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 4, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = { vm.updateAccount(acc.copy(signature = text)); editSignature = null }) { Text("Sichern") }
            },
            dismissButton = { TextButton(onClick = { editSignature = null }) { Text("Abbrechen") } }
        )
    }

    confirmRemove?.let { acc ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Konto entfernen?") },
            text = { Text("${acc.email} und alle lokal gespeicherten Nachrichten und Entwürfe dieses Kontos werden vom Gerät gelöscht. Auf dem Server bleibt alles erhalten.") },
            confirmButton = {
                TextButton(onClick = { vm.removeAccount(acc); confirmRemove = null }) { Text("Entfernen", color = c.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Abbrechen") } }
        )
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(color = MailTheme.colors.separator, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onClick: () -> Unit) {
    val c = MailTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = c.textPrimary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (checked) Icon(Icons.Outlined.Check, null, tint = c.accent)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = MailTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = c.textPrimary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IosSwitch(checked, onChange)
    }
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    val c = MailTheme.colors
    Text(
        label,
        color = c.accent,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(0.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    )
}
