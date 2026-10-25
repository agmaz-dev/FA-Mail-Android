package de.famail.app.ui.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import de.famail.app.ui.components.GlassPill
import de.famail.app.ui.components.PillIcon
import de.famail.app.ui.theme.MailTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** Dünne Hülle um den Android-eigenen PdfRenderer (kostenlos, ohne Fremdbibliothek). */
private class PdfDocument(file: File) {
    private val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(pfd)
    private val lock = Mutex()
    val pageCount = renderer.pageCount
    /** Seitenverhältnis Breite/Höhe je Seite – vorab gelesen, damit die Liste nicht springt */
    val aspect: List<Float> = (0 until pageCount).map { i ->
        renderer.openPage(i).use { p -> p.width.toFloat() / p.height.toFloat() }
    }

    suspend fun render(index: Int, widthPx: Int): Bitmap = withContext(Dispatchers.IO) {
        lock.withLock {
            renderer.openPage(index).use { page ->
                val w = widthPx.coerceIn(200, 2400)
                val h = (w / aspect[index]).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        }
    }

    fun close() {
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }
}

@Composable
fun PdfReaderScreen(path: String, title: String, onClose: () -> Unit) {
    val c = MailTheme.colors
    val context = LocalContext.current
    val file = remember(path) { File(path) }
    val result = remember(path) { runCatching { PdfDocument(file) } }
    val doc = result.getOrNull()
    DisposableEffect(path) { onDispose { doc?.close() } }
    BackHandler { onClose() }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()
    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }

    Box(Modifier.fillMaxSize().background(if (c.isDark) Color(0xFF1C1C1E) else Color(0xFFE9E7EF))) {
        if (doc == null) {
            Text(
                "PDF kann nicht angezeigt werden (geschützt oder beschädigt).\n${result.exceptionOrNull()?.message ?: ""}",
                color = c.textSecondary,
                modifier = Modifier.align(Alignment.Center).padding(32.dp)
            )
        } else {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            if (scale > 1f) { scale = 1f; offsetX = 0f; offsetY = 0f } else { scale = 2.5f }
                        })
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.count { it.pressed }
                                if (pressed >= 2) {
                                    scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                                    val pan = event.calculatePan()
                                    val maxX = size.width * (scale - 1f) / 2f
                                    val maxY = size.height * (scale - 1f) / 2f
                                    offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                                    offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                                    event.changes.forEach { it.consume() }
                                } else if (scale > 1f && pressed == 1) {
                                    // Einfinger: horizontal verschieben, vertikal scrollt die Liste
                                    val dx = event.changes.first().positionChange().x
                                    val maxX = size.width * (scale - 1f) / 2f
                                    offsetX = (offsetX + dx).coerceIn(-maxX, maxX)
                                }
                                if (scale <= 1f) { offsetX = 0f; offsetY = 0f }
                            } while (event.changes.any { it.pressed })
                        }
                    }
            ) {
                val widthPx = constraints.maxWidth
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(top = 90.dp, bottom = 90.dp, start = 12.dp, end = 12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale, scaleY = scale,
                            translationX = offsetX, translationY = offsetY
                        )
                ) {
                    items(doc.pageCount) { index ->
                        val bitmap by produceState<Bitmap?>(null, index, widthPx) {
                            value = runCatching { doc.render(index, (widthPx * 1.5f).toInt()) }.getOrNull()
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .aspectRatio(doc.aspect[index])
                                .shadow(3.dp, RoundedCornerShape(4.dp))
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            val bmp = bitmap
                            if (bmp == null) {
                                CircularProgressIndicator(color = c.accent)
                            } else {
                                Image(
                                    bmp.asImageBitmap(),
                                    contentDescription = "Seite ${index + 1}",
                                    contentScale = ContentScale.FillBounds,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }

            GlassPill(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp)) {
                Text(
                    "Seite $currentPage von ${doc.pageCount}",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.textPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassPill { PillIcon(Icons.Outlined.Close, "Schließen", onClick = onClose) }
            Spacer(Modifier.width(10.dp))
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(c.glass)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(10.dp))
            if (doc != null) {
                GlassPill {
                    PillIcon(Icons.Outlined.Share, "Teilen", onClick = { sharePdf(context, file, title) })
                    PillIcon(Icons.Outlined.Print, "Drucken", onClick = { printPdf(context, file, title) })
                }
            }
        }
    }
}

private fun sharePdf(context: Context, file: File, title: String) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "PDF teilen").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun printPdf(context: Context, file: File, title: String) {
    val pm = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    pm.print(title, object : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?, newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal?, callback: PrintDocumentAdapter.LayoutResultCallback, extras: Bundle?
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }
            callback.onLayoutFinished(
                PrintDocumentInfo.Builder(file.name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(),
                true
            )
        }

        override fun onWrite(
            pages: Array<out PageRange>?, destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?, callback: PrintDocumentAdapter.WriteResultCallback
        ) {
            try {
                FileInputStream(file).use { input ->
                    FileOutputStream(destination.fileDescriptor).use { output -> input.copyTo(output) }
                }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            }
        }
    }, null)
}
