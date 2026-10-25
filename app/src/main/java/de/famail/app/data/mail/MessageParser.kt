package de.famail.app.data.mail

import androidx.core.text.HtmlCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.internet.ContentType
import javax.mail.internet.MimeUtility

data class AttachmentInfo(
    /** Pfad im MIME-Baum, z. B. "1" oder "0.2" – dient zum späteren Herunterladen */
    val path: String,
    val fileName: String,
    val mimeType: String,
    val size: Int
) {
    val isPdf: Boolean
        get() = mimeType.equals("application/pdf", true) || fileName.lowercase().endsWith(".pdf")
}

data class ParsedBody(
    val text: String?,
    val html: String?,
    val attachments: List<AttachmentInfo>
) {
    fun preview(): String = MessageParser.previewOf(text, html)
}

object MessageParser {

    fun parse(root: Part): ParsedBody {
        val acc = Accumulator()
        walk(root, "", acc)
        return ParsedBody(acc.text, acc.html, acc.attachments)
    }

    private class Accumulator {
        var text: String? = null
        var html: String? = null
        val attachments = mutableListOf<AttachmentInfo>()
    }

    private fun walk(part: Part, path: String, acc: Accumulator) {
        if (part.isMimeType("multipart/*")) {
            val mp = part.content as? Multipart ?: return
            for (i in 0 until mp.count) {
                val child = if (path.isEmpty()) "$i" else "$path.$i"
                walk(mp.getBodyPart(i), child, acc)
            }
            return
        }

        val fileName = decodedFileName(part)
        val disposition = runCatching { part.disposition }.getOrNull()
        val isText = part.isMimeType("text/plain") || part.isMimeType("text/html")
        val isAttachment = Part.ATTACHMENT.equals(disposition, ignoreCase = true) ||
            (fileName != null && !isText) ||
            part.isMimeType("message/rfc822") ||
            (fileName != null && Part.ATTACHMENT.equals(disposition, true))

        if (isAttachment) {
            acc.attachments += AttachmentInfo(
                path = path,
                fileName = fileName ?: if (part.isMimeType("message/rfc822")) "Nachricht.eml" else "Anhang",
                mimeType = baseType(part.contentType),
                size = runCatching { part.size }.getOrDefault(-1)
            )
            return
        }

        when {
            part.isMimeType("text/html") && acc.html == null -> acc.html = readText(part)
            part.isMimeType("text/plain") && acc.text == null -> acc.text = readText(part)
        }
    }

    /** Navigiert zum Teil mit dem gegebenen Pfad (siehe [AttachmentInfo.path]). */
    fun findPart(root: Part, path: String): Part {
        if (path.isEmpty()) return root
        var current: Part = root
        for (segment in path.split('.')) {
            val mp = current.content as Multipart
            current = mp.getBodyPart(segment.toInt())
        }
        return current
    }

    private fun decodedFileName(part: Part): String? {
        val raw = runCatching { part.fileName }.getOrNull() ?: return null
        return runCatching { MimeUtility.decodeText(raw) }.getOrDefault(raw)
    }

    private fun baseType(contentType: String?): String =
        runCatching { ContentType(contentType).baseType.lowercase() }.getOrDefault("application/octet-stream")

    private fun readText(part: Part): String? {
        return try {
            when (val c = part.content) {
                is String -> c
                is InputStream -> c.readBytes().toString(Charsets.UTF_8)
                else -> c?.toString()
            }
        } catch (e: Exception) {
            // Unbekannter Zeichensatz: Rohdaten als UTF-8 lesen statt die Nachricht zu verlieren
            runCatching { part.inputStream.readBytes().toString(Charsets.UTF_8) }.getOrNull()
        }
    }

    private val styleOrScript = Regex("(?is)<(style|script|head)[^>]*>.*?</\\1>")
    private val whitespace = Regex("\\s+")

    /** Wandelt HTML in lesbaren Text um (für Zitate in Antworten und Weiterleitungen). */
    fun htmlToText(html: String): String {
        val cleaned = html.take(200_000).replace(styleOrScript, " ")
        return HtmlCompat.fromHtml(cleaned, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
            .replace('\uFFFC', ' ')
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    fun previewOf(text: String?, html: String?): String {
        val plain = when {
            !text.isNullOrBlank() -> text
            !html.isNullOrBlank() -> {
                val cleaned = html.take(30_000).replace(styleOrScript, " ")
                HtmlCompat.fromHtml(cleaned, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
            }
            else -> ""
        }
        return plain.replace('￼', ' ').replace(whitespace, " ").trim().take(220)
    }

    fun attachmentsToJson(list: List<AttachmentInfo>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("path", it.path)
                    .put("name", it.fileName)
                    .put("mime", it.mimeType)
                    .put("size", it.size)
            )
        }
        return arr.toString()
    }

    fun attachmentsFromJson(json: String?): List<AttachmentInfo> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AttachmentInfo(o.getString("path"), o.getString("name"), o.getString("mime"), o.optInt("size", -1))
            }
        }.getOrDefault(emptyList())
    }
}
