package de.famail.app.ui.split

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import de.famail.app.ui.theme.MailTheme

/**
 * Zwei Bereiche mit frei verschiebbarer Trennlinie.
 * Ziehen: stufenlos zwischen 20 % und 80 %. Doppeltippen: zurück auf 40/60.
 * Die Inhalte bleiben während des Ziehens bestehen (kein Neuladen, kein Verlust der Scrollposition).
 */
@Composable
fun ResizableSplit(
    fraction: Float,
    hideLeft: Boolean,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onReset: () -> Unit,
    left: @Composable () -> Unit,
    right: @Composable () -> Unit
) {
    val c = MailTheme.colors
    val currentFraction by rememberUpdatedState(fraction)
    var dragging by remember { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val totalPx = constraints.maxWidth.toFloat()
        val density = LocalDensity.current
        val handleWidth = 18.dp
        val leftWidth = with(density) { (totalPx * fraction).toDp() } - handleWidth / 2

        Row(Modifier.fillMaxSize()) {
            if (!hideLeft) {
                Box(Modifier.width(leftWidth).fillMaxHeight()) { left() }

                Box(
                    Modifier
                        .width(handleWidth)
                        .fillMaxHeight()
                        .background(c.pane)
                        .pointerInput(totalPx) {
                            detectHorizontalDragGestures(
                                onDragStart = { dragging = true },
                                onDragEnd = { dragging = false; onDragEnd() },
                                onDragCancel = { dragging = false; onDragEnd() }
                            ) { change, dx ->
                                change.consume()
                                onDrag((currentFraction * totalPx + dx) / totalPx)
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = { onReset() })
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .width(if (dragging) 1.5.dp else 0.5.dp)
                            .fillMaxHeight()
                            .background(if (dragging) c.accent else c.separator)
                    )
                    Box(
                        Modifier
                            .size(width = 5.dp, height = 48.dp)
                            .clip(CircleShape)
                            .background(if (dragging) c.accent else c.textTertiary)
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) { right() }
        }
    }
}
