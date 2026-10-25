package de.famail.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.famail.app.ui.theme.AvatarPalette
import de.famail.app.ui.theme.MailTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

/** Runder Avatar mit Initialen (eigene Darstellung, keine Fremdgrafiken). */
@Composable
fun InitialsAvatar(name: String, seed: String, size: Dp, modifier: Modifier = Modifier) {
    val color = AvatarPalette[abs(seed.lowercase().hashCode()) % AvatarPalette.size]
    val initials = initialsOf(name)
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initials,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.38f).sp
        )
    }
}

fun initialsOf(name: String): String {
    val cleaned = name.replace(Regex("[\"'<>()]"), " ").trim()
    val parts = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() && it.first().isLetterOrDigit() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts[0].take(1) + parts[parts.size - 1].take(1)).uppercase()
    }
}

/** Schwebende runde Glas-Schaltfläche wie in der rechten Aktionsleiste der Referenz. */
@Composable
fun GlassCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    tint: Color = MailTheme.colors.textPrimary,
    enabled: Boolean = true
) {
    val c = MailTheme.colors
    Box(
        modifier
            .size(size)
            .shadow(6.dp, CircleShape, clip = false, ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
            .clip(CircleShape)
            .background(c.glass)
            .border(0.5.dp, c.glassBorder, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = if (enabled) tint else c.textTertiary, modifier = Modifier.size(size * 0.46f))
    }
}

/** Glas-Pille mit mehreren Symbolen (z. B. Filter und Mehr-Menü oben in der Liste). */
@Composable
fun GlassPill(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = MailTheme.colors
    Row(
        modifier
            .shadow(4.dp, RoundedCornerShape(50), clip = false, ambientColor = Color(0x1A000000), spotColor = Color(0x1A000000))
            .clip(RoundedCornerShape(50))
            .background(c.glass)
            .border(0.5.dp, c.glassBorder, RoundedCornerShape(50))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) { content() }
}

@Composable
fun PillIcon(icon: ImageVector, contentDescription: String, onClick: () -> Unit, tint: Color = MailTheme.colors.textPrimary) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** Seitenleisten-Symbol (Rechteck mit linker Spalte), selbst gezeichnet. */
@Composable
fun SidebarGlyph(color: Color, modifier: Modifier = Modifier.size(22.dp)) {
    Canvas(modifier) {
        val stroke = 1.8.dp.toPx()
        val r = 4.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(stroke / 2, size.height * 0.15f),
            size = Size(size.width - stroke, size.height * 0.7f),
            cornerRadius = CornerRadius(r, r),
            style = Stroke(width = stroke)
        )
        val x = size.width * 0.38f
        drawLine(color, Offset(x, size.height * 0.15f), Offset(x, size.height * 0.85f), strokeWidth = stroke)
        val lx1 = size.width * 0.12f
        val lx2 = size.width * 0.28f
        listOf(0.32f, 0.44f, 0.56f).forEach { fy ->
            drawLine(color, Offset(lx1, size.height * fy), Offset(lx2, size.height * fy), strokeWidth = stroke * 0.8f)
        }
    }
}

/** Sidebar-Schaltfläche oben links (runder Glasknopf). */
@Composable
fun SidebarButton(onClick: () -> Unit) {
    val c = MailTheme.colors
    Box(
        Modifier
            .size(48.dp)
            .shadow(4.dp, CircleShape, clip = false, ambientColor = Color(0x1A000000), spotColor = Color(0x1A000000))
            .clip(CircleShape)
            .background(c.glass)
            .border(0.5.dp, c.glassBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { SidebarGlyph(c.textPrimary) }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        color = MailTheme.colors.textSecondary,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp)
    )
}

@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = MailTheme.colors
    Column(
        modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (c.isDark) Color(0xFF1C1C1E) else Color.White)
    ) { content() }
}

object DateLabels {
    private val time = SimpleDateFormat("HH:mm", Locale.GERMANY)
    private val weekday = SimpleDateFormat("EEEE", Locale.GERMANY)
    private val date = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
    private val full = SimpleDateFormat("EEEE, d. MMMM yyyy 'um' HH:mm", Locale.GERMANY)

    /** Listen-Datum im Apple-Stil: Uhrzeit heute, „Gestern“, Wochentag, sonst Datum. */
    fun list(millis: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = millis }
        val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
        val dayDiff = if (sameYear) now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR) else 99
        return when {
            dayDiff == 0 -> time.format(millis)
            dayDiff == 1 -> "Gestern"
            dayDiff in 2..6 -> weekday.format(millis)
            else -> date.format(millis)
        }
    }

    fun full(millis: Long): String = full.format(millis)
}

// ------------------------------------------------------------------ Apple-artige Bedienelemente

/** iOS-Schalter: grüne Spur, weißer Knopf mit Schatten. */
@Composable
fun IosSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = MailTheme.colors
    val track by androidx.compose.animation.animateColorAsState(
        if (checked) Color(0xFF34C759) else if (c.isDark) Color(0xFF39393D) else Color(0xFFE9E9EA),
        label = "track"
    )
    val offset by androidx.compose.animation.core.animateDpAsState(if (checked) 20.dp else 0.dp, label = "knob")
    Box(
        modifier
            .size(width = 51.dp, height = 31.dp)
            .clip(RoundedCornerShape(50))
            .background(track)
            .clickable { onChange(!checked) }
            .padding(2.dp)
    ) {
        Box(
            Modifier
                .padding(start = offset)
                .size(27.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/** iOS-Segmentschalter: graue Spur, ausgewähltes Segment als weiße Pille. */
@Composable
fun <T> SegmentedControl(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = MailTheme.colors
    Row(
        modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (c.isDark) Color(0xFF2C2C2E) else Color(0xFFE6E4EB))
            .padding(2.dp)
    ) {
        options.forEach { (value, label) ->
            val isSel = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .then(if (isSel) Modifier.shadow(2.dp, RoundedCornerShape(7.dp)) else Modifier)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (isSel) (if (c.isDark) Color(0xFF636366) else Color.White) else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                    color = c.textPrimary
                )
            }
        }
    }
}

/** Zeile in einer gruppierten Karte: Beschriftung links, Eingabefeld rechts (wie iOS-Formulare). */
@Composable
fun FormFieldRow(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
    isPassword: Boolean = false,
    labelWidth: Dp = 110.dp
) {
    val c = MailTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            color = c.textPrimary,
            modifier = Modifier.width(labelWidth)
        )
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge, color = c.textTertiary, maxLines = 1)
            }
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = androidx.compose.material3.MaterialTheme.typography.bodyLarge.copy(color = c.textPrimary),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
                visualTransformation = if (isPassword) androidx.compose.ui.text.input.PasswordVisualTransformation()
                else androidx.compose.ui.text.input.VisualTransformation.None,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = keyboardType,
                    autoCorrectEnabled = false
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun RowDivider(start: Dp = 16.dp) {
    androidx.compose.material3.HorizontalDivider(
        color = MailTheme.colors.separator,
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = start)
    )
}

@Composable
fun FootnoteText(text: String, color: Color = MailTheme.colors.textSecondary) {
    Text(
        text,
        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 6.dp, bottom = 4.dp)
    )
}

/** Großer blauer Apple-Knopf. */
@Composable
fun PrimaryButton(text: String, enabled: Boolean, busy: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = MailTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled || busy) c.accent else c.accent.copy(alpha = 0.35f))
            .clickable(enabled = enabled && !busy, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (busy) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            }
        } else {
            Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        }
    }
}
