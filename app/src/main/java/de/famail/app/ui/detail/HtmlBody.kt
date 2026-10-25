package de.famail.app.ui.detail

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import android.text.TextUtils

private class ScrollHolder(var pending: Int)

/**
 * Sichere HTML-Anzeige: JavaScript aus, kein Datei-/Content-Zugriff,
 * externe Bilder (Tracking-Pixel) standardmäßig blockiert, Links öffnen nur nach Antippen extern.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlBody(
    html: String,
    allowRemote: Boolean,
    dark: Boolean,
    initialScroll: Int,
    onScroll: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnScroll by rememberUpdatedState(onScroll)
    val holder = remember { ScrollHolder(initialScroll) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(Color.TRANSPARENT)
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = false
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.textZoom = 100
                isVerticalScrollBarEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val uri = request.url
                        if (request.isForMainFrame && uri.scheme in setOf("http", "https", "mailto", "tel")) {
                            runCatching {
                                view.context.startActivity(
                                    Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                        return true
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        val y = holder.pending
                        if (y > 0) view.postDelayed({ view.scrollTo(0, y) }, 60)
                    }
                }
                setOnScrollChangeListener { _, _, y, _, _ -> currentOnScroll(y) }
            }
        },
        update = { wv ->
            val key = "${html.hashCode()}|$allowRemote|$dark"
            if (wv.tag != key) {
                wv.tag = key
                holder.pending = if (wv.scrollY > 0) wv.scrollY else holder.pending
                wv.settings.blockNetworkImage = !allowRemote
                wv.settings.blockNetworkLoads = !allowRemote
                wv.loadDataWithBaseURL(null, wrapHtml(html, dark), "text/html", "utf-8", null)
            }
        }
    )
}

fun hasRemoteContent(html: String): Boolean =
    Regex("(?i)(src|background)\\s*=\\s*[\"']?\\s*https?://").containsMatchIn(html) ||
        Regex("(?i)url\\(\\s*[\"']?https?://").containsMatchIn(html)

fun plainTextToHtml(text: String): String {
    val escaped = TextUtils.htmlEncode(text)
    val linked = Regex("(https?://[^\\s<>\"]+)").replace(escaped) { m -> "<a href=\"${m.value}\">${m.value}</a>" }
    // Zitatzeilen ("> ...") dezent darstellen
    val quoted = linked.lines().joinToString("\n") { line ->
        if (line.startsWith("&gt;")) "<span class=\"q\">$line</span>" else line
    }
    return "<pre>$quoted</pre>"
}

private fun wrapHtml(content: String, dark: Boolean): String {
    val text = if (dark) "#F2F2F7" else "#1C1C1E"
    val link = if (dark) "#0A84FF" else "#0A7AFF"
    val quote = if (dark) "#98989F" else "#6E6E73"
    val css = """
        html,body{margin:0;padding:0;background:transparent}
        body{font-family:-apple-system,'Roboto','Segoe UI',sans-serif;font-size:17px;line-height:1.5;
             color:$text;padding:4px 24px 160px 24px;word-wrap:break-word;overflow-wrap:break-word}
        img{max-width:100%;height:auto}
        table{max-width:100%!important}
        a{color:$link}
        blockquote{border-left:3px solid #C7C7CC;margin:8px 0;padding-left:12px;color:$quote}
        pre{white-space:pre-wrap;font-family:inherit;margin:0}
        .q{color:$quote}
    """.trimIndent()
    val head = "<meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><style>$css</style>"
    val lower = content.take(4000).lowercase()
    return when {
        lower.contains("<head") -> content.replaceFirst(Regex("(?i)<head[^>]*>"), "$0$head")
        lower.contains("<html") -> content.replaceFirst(Regex("(?i)<html[^>]*>"), "$0<head>$head</head>")
        else -> "<!doctype html><html><head>$head</head><body>$content</body></html>"
    }
}

fun openUri(context: android.content.Context, uri: Uri) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
