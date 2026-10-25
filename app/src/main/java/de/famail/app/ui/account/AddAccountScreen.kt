package de.famail.app.ui.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.famail.app.data.mail.ProviderPreset
import de.famail.app.data.mail.ProviderPresets
import de.famail.app.data.mail.Security
import de.famail.app.data.repo.NewAccountInput
import de.famail.app.ui.MainViewModel
import de.famail.app.ui.components.FootnoteText
import de.famail.app.ui.components.FormFieldRow
import de.famail.app.ui.components.GroupCard
import de.famail.app.ui.components.PrimaryButton
import de.famail.app.ui.components.RowDivider
import de.famail.app.ui.components.SectionHeader
import de.famail.app.ui.components.SegmentedControl
import de.famail.app.ui.theme.MailTheme

/** Farbe und Kürzel je Anbieter (eigene neutrale Darstellung, keine Markenlogos). */
private fun badgeFor(key: String): Pair<Color, String> = when (key) {
    "gmail" -> Color(0xFFEA4335) to "G"
    "outlook" -> Color(0xFF0A64C8) to "O"
    "gmx" -> Color(0xFF1C449B) to "GMX"
    "webde" -> Color(0xFFFFC800) to "W"
    "yahoo" -> Color(0xFF6001D2) to "Y"
    "icloud" -> Color(0xFF3B9CFF) to "i"
    "ionos" -> Color(0xFF003D8F) to "1&1"
    "tonline" -> Color(0xFFE20074) to "T"
    else -> Color(0xFF8E8E93) to "@"
}

@Composable
fun AddAccountScreen(vm: MainViewModel, isFirstAccount: Boolean, onCancel: () -> Unit) {
    val c = MailTheme.colors
    val state by vm.addState.collectAsState()
    var providerKey by rememberSaveable { mutableStateOf<String?>(null) }

    if (providerKey != null) BackHandler { providerKey = null; vm.clearAddError() }

    Box(Modifier.fillMaxSize().background(c.background), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
        ) {
            // Navigationszeile im iOS-Stil
            Row(
                Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val showBack = providerKey != null || !isFirstAccount
                if (showBack) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                if (providerKey != null) { providerKey = null; vm.clearAddError() } else onCancel()
                            }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.ArrowBackIosNew, null, tint = c.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (providerKey != null) "Anbieter" else "Abbrechen",
                            color = c.accent, style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            val key = providerKey
            if (key == null) {
                ProviderList(isFirstAccount) { providerKey = it; vm.clearAddError() }
            } else {
                AccountForm(vm, ProviderPresets.byKey(key), state.busy, state.error, state.loginRejected)
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun ProviderList(isFirstAccount: Boolean, onPick: (String) -> Unit) {
    val c = MailTheme.colors
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .padding(top = 12.dp)
                .size(76.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(c.accent),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Outlined.Mail, null, tint = Color.White, modifier = Modifier.size(44.dp)) }
        Text(
            if (isFirstAccount) "Willkommen bei FA Mail" else "Account hinzufügen",
            style = MaterialTheme.typography.headlineMedium,
            color = c.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 18.dp, start = 24.dp, end = 24.dp)
        )
        Text(
            "Wähle deinen E-Mail-Anbieter.",
            style = MaterialTheme.typography.bodyLarge,
            color = c.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, bottom = 22.dp)
        )
    }
    GroupCard {
        ProviderPresets.all.forEachIndexed { i, p ->
            if (i > 0) RowDivider(start = 64.dp)
            val (color, label) = badgeFor(p.key)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(p.key) }
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(color),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label, color = Color.White, fontWeight = FontWeight.Bold,
                        fontSize = if (label.length > 1) 11.sp else 17.sp
                    )
                }
                Spacer(Modifier.width(14.dp))
                Text(p.name, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ChevronRight, null, tint = c.textTertiary)
            }
        }
    }
}

@Composable
private fun AccountForm(vm: MainViewModel, preset: ProviderPreset, busy: Boolean, error: String?, loginRejected: Boolean) {
    val c = MailTheme.colors
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var imapHost by rememberSaveable(preset.key) { mutableStateOf(preset.imapHost) }
    var imapPort by rememberSaveable(preset.key) { mutableStateOf(preset.imapPort.toString()) }
    var imapSec by rememberSaveable(preset.key) { mutableStateOf(preset.imapSecurity) }
    var smtpHost by rememberSaveable(preset.key) { mutableStateOf(preset.smtpHost) }
    var smtpPort by rememberSaveable(preset.key) { mutableStateOf(preset.smtpPort.toString()) }
    var smtpSec by rememberSaveable(preset.key) { mutableStateOf(preset.smtpSecurity) }
    var advanced by rememberSaveable(preset.key) { mutableStateOf(preset.key == "custom") }

    val (color, label) = badgeFor(preset.key)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(15.dp)).background(color),
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (label.length > 1) 17.sp else 30.sp)
        }
        Text(
            preset.name,
            style = MaterialTheme.typography.headlineSmall,
            color = c.textPrimary,
            modifier = Modifier.padding(top = 12.dp, bottom = 18.dp)
        )
    }

    GroupCard {
        FormFieldRow("Name", name, { name = it }, placeholder = "Dein Name")
        RowDivider()
        FormFieldRow("E-Mail", email, { v ->
            email = v
            if (preset.key == "custom") {
                val domain = v.substringAfter('@', "")
                if (domain.contains('.') && (imapHost.isBlank() || imapHost.startsWith("imap."))) {
                    imapHost = "imap.$domain"; smtpHost = "smtp.$domain"
                }
            }
        }, placeholder = "name@beispiel.de", keyboardType = KeyboardType.Email)
        RowDivider()
        FormFieldRow(
            "Passwort", password,
            // App-Passwörter (Google, Yahoo, Apple) werden mit Leerzeichen angezeigt – diese entfernen
            { password = if (preset.key in setOf("gmail", "yahoo", "icloud")) it.filterNot(Char::isWhitespace) else it.trim() },
            placeholder = if (preset.key in setOf("gmail", "yahoo", "icloud")) "App-Passwort" else "Erforderlich",
            keyboardType = KeyboardType.Password, isPassword = true
        )
    }
    FootnoteText(preset.hint)

    SectionHeader("Server")
    GroupCard {
        Row(
            Modifier.fillMaxWidth().clickable { advanced = !advanced }.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Servereinstellungen", style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
            Text(if (advanced) "Ausblenden" else "Anzeigen", style = MaterialTheme.typography.bodyLarge, color = c.accent)
        }
        if (advanced) {
            RowDivider()
            FormFieldRow("Benutzer", username, { username = it }, placeholder = "wie E-Mail")
            RowDivider()
            FormFieldRow("IMAP", imapHost, { imapHost = it }, placeholder = "imap.server.de", keyboardType = KeyboardType.Uri)
            RowDivider()
            FormFieldRow("Port", imapPort, { imapPort = it.filter(Char::isDigit) }, keyboardType = KeyboardType.Number)
            SegmentedControl(
                listOf(Security.SSL to "SSL/TLS", Security.STARTTLS to "STARTTLS"), imapSec,
                { imapSec = it; imapPort = if (it == Security.SSL) "993" else "143" },
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            )
            RowDivider()
            FormFieldRow("SMTP", smtpHost, { smtpHost = it }, placeholder = "smtp.server.de", keyboardType = KeyboardType.Uri)
            RowDivider()
            FormFieldRow("Port", smtpPort, { smtpPort = it.filter(Char::isDigit) }, keyboardType = KeyboardType.Number)
            SegmentedControl(
                listOf(Security.SSL to "SSL/TLS", Security.STARTTLS to "STARTTLS"), smtpSec,
                { smtpSec = it; smtpPort = if (it == Security.SSL) "465" else "587" },
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            )
        }
    }

    if (error != null) {
        Spacer(Modifier.height(14.dp))
        GroupCard {
            Text(
                error,
                color = c.danger,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        }
        if (loginRejected) UnlockHelp(preset.key)
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Lock, null, tint = c.textSecondary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            "Das Passwort bleibt verschlüsselt auf diesem Gerät.",
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary
        )
    }

    val valid = email.contains('@') && password.isNotEmpty() && imapHost.isNotBlank() && smtpHost.isNotBlank() &&
        imapPort.toIntOrNull() != null && smtpPort.toIntOrNull() != null
    PrimaryButton(
        text = if (busy) "Verbindung wird geprüft …" else "Weiter",
        enabled = valid,
        busy = busy
    ) {
        vm.addAccount(
            NewAccountInput(
                email = email.trim(),
                displayName = name.trim(),
                providerKey = preset.key,
                username = username.trim().ifBlank { email.trim() },
                password = password,
                imapHost = imapHost.trim(),
                imapPort = imapPort.toInt(),
                imapSecurity = imapSec,
                smtpHost = smtpHost.trim(),
                smtpPort = smtpPort.toInt(),
                smtpSecurity = smtpSec
            )
        )
    }
}

/** Was der Anbieter verlangt, damit fremde Mail-Apps sich anmelden dürfen – mit Direkt-Link. */
private data class UnlockGuide(val title: String, val steps: List<String>, val buttonText: String, val url: String)

private fun unlockGuideFor(key: String): UnlockGuide = when (key) {
    "gmx" -> UnlockGuide(
        "GMX blockiert fremde Mail-Apps, bis du es einmal erlaubst:",
        listOf(
            "Unten auf „GMX öffnen“ tippen und dort anmelden",
            "Links unten auf das Zahnrad „Einstellungen“",
            "Unter „E-Mail“ auf „POP3/IMAP Abruf“",
            "Haken bei „POP3 und IMAP Zugriff erlauben“ setzen",
            "„Speichern“ – dann hier erneut auf „Weiter“"
        ),
        "GMX öffnen", "https://www.gmx.net/"
    )
    "webde" -> UnlockGuide(
        "WEB.DE blockiert fremde Mail-Apps, bis du es einmal erlaubst:",
        listOf(
            "Unten auf „WEB.DE öffnen“ tippen und dort anmelden",
            "Links unten auf das Zahnrad „Einstellungen“",
            "Unter „E-Mail“ auf „POP3/IMAP Abruf“",
            "Haken bei „POP3 und IMAP Zugriff erlauben“ setzen",
            "„Speichern“ – dann hier erneut auf „Weiter“"
        ),
        "WEB.DE öffnen", "https://web.de/"
    )
    "gmail" -> UnlockGuide(
        "Google lässt fremde Apps nur mit einem App-Passwort zu:",
        listOf(
            "Unten auf „App-Passwort erstellen“ tippen",
            "Name „FA Mail“ eingeben → „Erstellen“",
            "Die 16 Buchstaben kopieren",
            "Hier ins Passwortfeld einfügen → „Weiter“",
            "Fehlt die Seite: zuerst „Bestätigung in zwei Schritten“ einschalten"
        ),
        "App-Passwort erstellen", "https://myaccount.google.com/apppasswords"
    )
    "yahoo" -> UnlockGuide(
        "Yahoo lässt fremde Apps nur mit einem App-Passwort zu:",
        listOf(
            "Unten auf „App-Passwort erstellen“ tippen",
            "Name „FA Mail“ eingeben → „Passwort generieren“",
            "Die 16 Buchstaben kopieren",
            "Hier ins Passwortfeld einfügen → „Weiter“"
        ),
        "App-Passwort erstellen", "https://login.yahoo.com/account/security/app-passwords"
    )
    "icloud" -> UnlockGuide(
        "Apple lässt fremde Apps nur mit einem app-spezifischen Passwort zu:",
        listOf(
            "Unten auf „Apple-Account öffnen“ tippen",
            "„Anmelden und Sicherheit“ → „App-spezifische Passwörter“",
            "Passwort erstellen, kopieren und hier einfügen"
        ),
        "Apple-Account öffnen", "https://account.apple.com/"
    )
    "outlook" -> UnlockGuide(
        "Microsoft erlaubt bei Outlook/Hotmail keine Passwort-Anmeldung mehr.",
        listOf("Die Anmeldung „Mit Microsoft anmelden“ wird in einer der nächsten Versionen eingebaut."),
        "", ""
    )
    else -> UnlockGuide(
        "Der Server hat Benutzername oder Passwort abgelehnt:",
        listOf(
            "Passwort prüfen (Groß-/Kleinschreibung)",
            "Bei manchen Anbietern muss IMAP im Webmail erst freigeschaltet werden",
            "Manche Anbieter verlangen ein eigenes App-Passwort"
        ),
        "", ""
    )
}

@Composable
private fun UnlockHelp(providerKey: String) {
    val c = MailTheme.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    val guide = unlockGuideFor(providerKey)
    Spacer(Modifier.height(14.dp))
    SectionHeader("So klappt die Anmeldung")
    GroupCard {
        Column(Modifier.padding(16.dp)) {
            Text(guide.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
            Spacer(Modifier.height(10.dp))
            guide.steps.forEachIndexed { i, step ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.size(24.dp).clip(RoundedCornerShape(12.dp)).background(c.accent),
                        contentAlignment = Alignment.Center
                    ) { Text("${i + 1}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(10.dp))
                    Text(step, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
        if (guide.url.isNotBlank()) {
            RowDivider()
            Text(
                guide.buttonText + "  ›",
                color = c.accent,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(guide.url))
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                    .padding(16.dp)
            )
        }
    }
}
