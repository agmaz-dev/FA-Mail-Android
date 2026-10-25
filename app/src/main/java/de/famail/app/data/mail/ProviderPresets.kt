package de.famail.app.data.mail

object Security {
    const val SSL = "SSL"
    const val STARTTLS = "STARTTLS"
}

data class ProviderPreset(
    val key: String,
    val name: String,
    val imapHost: String,
    val imapPort: Int,
    val imapSecurity: String,
    val smtpHost: String,
    val smtpPort: Int,
    val smtpSecurity: String,
    val hint: String
)

object ProviderPresets {
    val all = listOf(
        ProviderPreset(
            "gmail", "Gmail", "imap.gmail.com", 993, Security.SSL, "smtp.gmail.com", 465, Security.SSL,
            "Gmail verlangt ein App-Passwort (Google-Konto → Sicherheit → 2-Faktor aktivieren → App-Passwörter). " +
                "Die Anmeldung per Google-OAuth folgt, sobald eine eigene Client-ID eingetragen ist (siehe README)."
        ),
        ProviderPreset(
            "outlook", "Outlook / Microsoft 365", "outlook.office365.com", 993, Security.SSL,
            "smtp.office365.com", 587, Security.STARTTLS,
            "Microsoft erlaubt bei Outlook.com/Microsoft 365 meist nur noch OAuth-Anmeldung. " +
                "Passwort-Anmeldung funktioniert nur, wenn sie für das Konto ausdrücklich freigeschaltet ist. " +
                "OAuth-Anbindung: siehe README (Microsoft-Client-ID nötig)."
        ),
        ProviderPreset(
            "gmx", "GMX", "imap.gmx.net", 993, Security.SSL, "mail.gmx.net", 465, Security.SSL,
            "Vorher im GMX-Webmail unter Einstellungen → POP3/IMAP-Abruf den IMAP-Zugriff erlauben."
        ),
        ProviderPreset(
            "webde", "WEB.DE", "imap.web.de", 993, Security.SSL, "smtp.web.de", 587, Security.STARTTLS,
            "Vorher im WEB.DE-Webmail unter Einstellungen → POP3/IMAP-Abruf den IMAP-Zugriff erlauben."
        ),
        ProviderPreset(
            "yahoo", "Yahoo / ymail", "imap.mail.yahoo.com", 993, Security.SSL, "smtp.mail.yahoo.com", 465, Security.SSL,
            "Yahoo verlangt ein App-Passwort (Kontosicherheit → App-Passwort generieren)."
        ),
        ProviderPreset(
            "icloud", "iCloud", "imap.mail.me.com", 993, Security.SSL, "smtp.mail.me.com", 587, Security.STARTTLS,
            "iCloud verlangt ein app-spezifisches Passwort (appleid.apple.com)."
        ),
        ProviderPreset(
            "ionos", "IONOS (1&1)", "imap.ionos.de", 993, Security.SSL, "smtp.ionos.de", 465, Security.SSL,
            "Benutzername ist die vollständige E-Mail-Adresse."
        ),
        ProviderPreset(
            "tonline", "T-Online", "secureimap.t-online.de", 993, Security.SSL,
            "securesmtp.t-online.de", 465, Security.SSL,
            "T-Online verlangt ein eigenes E-Mail-Passwort aus dem Kundencenter."
        ),
        ProviderPreset(
            "custom", "Andere / Firmen-Adresse", "", 993, Security.SSL, "", 465, Security.SSL,
            "Server-Daten erhältst du von deinem Anbieter oder IT-Dienstleister."
        )
    )

    fun byKey(key: String) = all.firstOrNull { it.key == key } ?: all.last()

    /** Vorschlag anhand der Domain der E-Mail-Adresse */
    fun guess(email: String): ProviderPreset? {
        val domain = email.substringAfter('@', "").lowercase()
        return when {
            domain in setOf("gmail.com", "googlemail.com") -> byKey("gmail")
            domain in setOf("outlook.com", "hotmail.com", "hotmail.de", "live.com", "live.de", "outlook.de", "msn.com") -> byKey("outlook")
            domain.startsWith("gmx.") -> byKey("gmx")
            domain == "web.de" -> byKey("webde")
            domain in setOf("yahoo.com", "yahoo.de", "ymail.com", "rocketmail.com") -> byKey("yahoo")
            domain in setOf("icloud.com", "me.com", "mac.com") -> byKey("icloud")
            domain == "t-online.de" -> byKey("tonline")
            else -> null
        }
    }
}
