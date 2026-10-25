package de.famail.app

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
import de.famail.app.data.mail.MailClient
import de.famail.app.data.mail.Security
import de.famail.app.data.mail.ServerConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import javax.mail.AuthenticationFailedException
import javax.mail.Message
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/**
 * Prüft den echten Anmelde-, Sende- und Abrufcode der App gegen einen
 * echten IMAP-/SMTP-Server mit SSL (GreenMail).
 */
class MailLoginTest {
    private lateinit var server: GreenMail
    private val email = "feri@test.local"
    private val password = "AppPasswort1234!"

    private val trustTestCert = mapOf(
        "mail.imaps.ssl.trust" to "*", "mail.imaps.ssl.checkserveridentity" to "false",
        "mail.smtps.ssl.trust" to "*", "mail.smtps.ssl.checkserveridentity" to "false"
    )

    private fun client(pw: String) = MailClient(
        ServerConfig(
            email = email, displayName = "Feri", username = email, password = pw,
            imapHost = "localhost", imapPort = ServerSetupTest.IMAPS.port, imapSecurity = Security.SSL,
            smtpHost = "localhost", smtpPort = ServerSetupTest.SMTPS.port, smtpSecurity = Security.SSL
        ),
        trustTestCert
    )

    @Before fun start() {
        server = GreenMail(ServerSetupTest.SMTPS_IMAPS)
        server.start()
        server.setUser(email, email, password)
    }

    @After fun stop() = server.stop()

    @Test fun anmeldungMitRichtigemPasswortKlappt() {
        client(password).testConnection()
    }

    @Test fun falschesPasswortWirdAbgelehnt() {
        try {
            client("falsch").testConnection()
            fail("Falsches Passwort wurde akzeptiert")
        } catch (e: AuthenticationFailedException) {
            // erwartet
        }
    }

    @Test fun sendenUndAbrufenKlappt() {
        val c = client(password)
        c.send { session ->
            MimeMessage(session).apply {
                setFrom(InternetAddress(email, "Feri", "UTF-8"))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(email))
                setSubject("Test Umlaute äöü", "UTF-8")
                setText("Hallo aus FA Mail", "UTF-8")
            }
        }
        assertTrue(server.waitForIncomingEmail(5000, 1))
        val folders = c.listFolders()
        assertTrue(folders.any { it.fullName.equals("INBOX", true) })
        val result = c.syncFolder("INBOX", 0, emptySet())
        assertEquals(1, result.headers.size)
        assertEquals("Test Umlaute äöü", result.headers[0].subject)
        assertEquals("Hallo aus FA Mail", result.headers[0].body?.text?.trim())
    }
}
