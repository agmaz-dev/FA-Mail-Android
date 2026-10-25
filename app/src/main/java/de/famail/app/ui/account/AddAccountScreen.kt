package de.famail.app.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.famail.app.data.mail.ProviderPreset
import de.famail.app.data.mail.ProviderPresets
import de.famail.app.data.mail.Security
import de.famail.app.data.repo.NewAccountInput
import de.famail.app.ui.MainViewModel
import de.famail.app.ui.theme.MailTheme

@Composable
fun AddAccountScreen(vm: MainViewModel, isFirstAccount: Boolean, onCancel: () -> Unit) {
    val c = MailTheme.colors
    val state by vm.addState.collectAsState()

    var providerKey by rememberSaveable { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(c.background), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (providerKey != null || !isFirstAccount) {
                    TextButton(onClick = {
                        if (providerKey != null) { providerKey = null; vm.clearAddError() } else onCancel()
                    }) {
                        Icon(Icons.Outlined.ArrowBack, null, tint = c.accent)
                        Spacer(Modifier.width(4.dp))
                        Text(if (providerKey != null) "Anbieter" else "Abbrechen", color = c.accent)
                    }
                }
            }
            Text(
                if (isFirstAccount) "Willkommen bei FA Mail" else "Konto hinzufügen",
                style = MaterialTheme.typography.headlineMedium,
                color = c.textPrimary,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                "Private und geschäftliche E-Mail-Konten in einer App.",
                style = MaterialTheme.typography.bodyLarge,
                color = c.textSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            val key = providerKey
            if (key == null) {
                ProviderPresets.all.forEach { p ->
                    ProviderCard(p) { providerKey = p.key; vm.clearAddError() }
                    Spacer(Modifier.height(10.dp))
                }
            } else {
                AccountForm(vm, ProviderPresets.byKey(key), state.busy, state.error)
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun ProviderCard(p: ProviderPreset, onClick: () -> Unit) {
    val c = MailTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (c.isDark) Color(0xFF1C1C1E) else Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(p.name, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
        Text("›", style = MaterialTheme.typography.titleLarge, color = c.textTertiary)
    }
}

@Composable
private fun AccountForm(vm: MainViewModel, preset: ProviderPreset, busy: Boolean, error: String?) {
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

    Text(preset.name, style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
    Spacer(Modifier.height(8.dp))
    Text(
        preset.hint,
        style = MaterialTheme.typography.bodyMedium,
        color = c.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (c.isDark) Color(0xFF1C1C1E) else Color(0xFFEFEDF4))
            .padding(12.dp)
    )
    Spacer(Modifier.height(16.dp))

    Field("Name (wird Empfängern angezeigt)", name) { name = it }
    Field("E-Mail-Adresse", email, KeyboardType.Email) { v ->
        email = v
        if (preset.key == "custom" && imapHost.isBlank()) {
            val domain = v.substringAfter('@', "")
            if (domain.contains('.')) { imapHost = "imap.$domain"; smtpHost = "smtp.$domain" }
        }
    }
    Field("Passwort / App-Passwort", password, KeyboardType.Password, isPassword = true) { password = it }

    TextButton(onClick = { advanced = !advanced }) {
        Text(if (advanced) "Servereinstellungen ausblenden" else "Servereinstellungen anzeigen", color = c.accent)
    }
    if (advanced) {
        Field("Benutzername (leer = E-Mail-Adresse)", username) { username = it }
        Text("Eingehend (IMAP)", style = MaterialTheme.typography.titleSmall, color = c.textPrimary, modifier = Modifier.padding(top = 8.dp))
        Field("IMAP-Server", imapHost, KeyboardType.Uri) { imapHost = it }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(120.dp)) { Field("Port", imapPort, KeyboardType.Number) { imapPort = it.filter(Char::isDigit) } }
            SecurityToggle(imapSec) { imapSec = it; imapPort = if (it == Security.SSL) "993" else "143" }
        }
        Text("Ausgehend (SMTP)", style = MaterialTheme.typography.titleSmall, color = c.textPrimary, modifier = Modifier.padding(top = 8.dp))
        Field("SMTP-Server", smtpHost, KeyboardType.Uri) { smtpHost = it }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(120.dp)) { Field("Port", smtpPort, KeyboardType.Number) { smtpPort = it.filter(Char::isDigit) } }
            SecurityToggle(smtpSec) { smtpSec = it; smtpPort = if (it == Security.SSL) "465" else "587" }
        }
    }

    if (error != null) {
        Text(
            error,
            color = c.danger,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 10.dp)
        )
    }

    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Lock, null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            "Das Passwort wird nur verschlüsselt auf diesem Gerät gespeichert (Android Keystore).",
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary
        )
    }

    val valid = email.contains('@') && password.isNotEmpty() && imapHost.isNotBlank() && smtpHost.isNotBlank() &&
        imapPort.toIntOrNull() != null && smtpPort.toIntOrNull() != null
    Box(
        Modifier
            .padding(top = 18.dp)
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (valid && !busy) c.accent else c.textTertiary)
            .clickable(enabled = valid && !busy) {
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
            },
        contentAlignment = Alignment.Center
    ) {
        if (busy) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Verbindung wird geprüft …", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Text("Anmelden", color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
}

@Composable
private fun SecurityToggle(value: String, onChange: (String) -> Unit) {
    val c = MailTheme.colors
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .border(0.5.dp, c.separator, RoundedCornerShape(10.dp))
    ) {
        listOf(Security.SSL to "SSL/TLS", Security.STARTTLS to "STARTTLS").forEach { (k, label) ->
            Text(
                label,
                color = if (value == k) Color.White else c.textPrimary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .background(if (value == k) c.accent else Color.Transparent)
                    .clickable { onChange(k) }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}
