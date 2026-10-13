package dev.kidyoh.liquid.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kidyoh.liquid.icons.SquircleShape
import dev.kidyoh.liquid.ui.theme.Glyphs
import dev.kidyoh.liquid.ui.theme.IOS
import dev.kidyoh.liquid.ui.theme.Inter
import dev.kidyoh.liquid.ui.theme.LocalPalette

private fun noRipple() = MutableInteractionSource()

@Composable
fun LargeTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 20.dp, top = 8.dp, bottom = 14.dp),
        style = TextStyle(fontFamily = Inter, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = LocalPalette.current.label, letterSpacing = (-0.6).sp),
    )
}

/** iOS grouped section: optional caps header, a rounded card, optional footer. */
@Composable
fun Section(header: String? = null, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        header?.let {
            Text(
                it.uppercase(),
                modifier = Modifier.padding(start = 18.dp, bottom = 7.dp),
                style = TextStyle(fontFamily = Inter, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = p.secondaryLabel, letterSpacing = 0.2.sp),
            )
        }
        Column(Modifier.fillMaxWidth().background(p.card, RoundedCornerShape(26.dp)).padding(vertical = 2.dp), content = content)
        footer?.let {
            Text(
                it,
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 7.dp),
                style = TextStyle(fontFamily = Inter, fontSize = 12.5.sp, color = p.secondaryLabel),
            )
        }
    }
}

@Composable
fun RowDivider(inset: Dp = 60.dp) {
    Box(Modifier.fillMaxWidth().padding(start = inset).height(0.5.dp).background(LocalPalette.current.separator))
}

/** The coloured rounded-square icon at the start of a Settings row. */
@Composable
fun IconTile(glyph: ImageVector, color: Color) {
    Box(Modifier.size(30.dp).background(color, SquircleShape), contentAlignment = Alignment.Center) {
        Icon(glyph, null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun SettingRow(
    title: String,
    glyph: ImageVector? = null,
    tile: Color = IOS.Gray,
    value: String? = null,
    titleColor: Color? = null,
    chevron: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (onClick != null) Modifier.clickable(interactionSource = remember { noRipple() }, indication = null, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) {
            IconTile(glyph, tile)
            Spacer(Modifier.width(14.dp))
        }
        Text(title, style = TextStyle(fontFamily = Inter, fontSize = 17.sp, color = titleColor ?: p.label), modifier = Modifier.weight(1f))
        value?.let { Text(it, style = TextStyle(fontFamily = Inter, fontSize = 17.sp, color = p.secondaryLabel)) }
        trailing?.invoke()
        if (onClick != null && chevron && trailing == null) {
            Spacer(Modifier.width(6.dp))
            Icon(Glyphs.ChevronRight, null, tint = p.tertiaryLabel, modifier = Modifier.size(16.dp))
        }
    }
}

/** iOS switch. */
@Composable
fun IosSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    val track by animateColorAsState(if (checked) IOS.Green else p.fill, label = "track")
    val x by animateDpAsState(if (checked) 22.dp else 2.dp, spring(dampingRatio = 0.7f, stiffness = 700f), label = "knob")
    Box(
        Modifier
            .size(width = 51.dp, height = 31.dp)
            .background(track, CircleShape)
            .clickable(interactionSource = remember { noRipple() }, indication = null) { onChange(!checked) }
    ) {
        Box(
            Modifier
                .offset(x = x, y = 2.dp)
                .size(27.dp)
                .shadow(3.dp, CircleShape)
                .background(Color.White, CircleShape)
        )
    }
}

@Composable
fun ToggleRow(title: String, checked: Boolean, glyph: ImageVector? = null, tile: Color = IOS.Gray, onChange: (Boolean) -> Unit) {
    SettingRow(title, glyph, tile, chevron = false, trailing = { IosSwitch(checked, onChange) })
}

/** iOS segmented control. */
@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    val p = LocalPalette.current
    val index = options.indexOf(selected).coerceAtLeast(0)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(p.fill.copy(alpha = if (p.dark) 0.5f else 0.35f), RoundedCornerShape(50))
            .padding(2.dp)
    ) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * index, spring(dampingRatio = 0.8f, stiffness = 600f), label = "seg")
        Box(
            Modifier
                .offset(x = x)
                .width(w)
                .fillMaxHeight()
                .shadow(2.dp, RoundedCornerShape(50))
                .background(if (p.dark) Color(0xFF636366) else Color.White, RoundedCornerShape(50))
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEach { o ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(interactionSource = remember { noRipple() }, indication = null) { onSelect(o) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(o),
                        style = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = if (o == selected) FontWeight.SemiBold else FontWeight.Medium, color = p.label),
                    )
                }
            }
        }
    }
}

/** iOS slider with an elongated Liquid Glass style thumb. */
@Composable
fun IosSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier, steps: Int = 0) {
    val p = LocalPalette.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(steps) {
                fun set(x: Float) {
                    var v = (x / size.width).coerceIn(0f, 1f)
                    if (steps > 0) v = Math.round(v * steps) / steps.toFloat()
                    onChange(v)
                }
                detectTapGestures { set(it.x) }
            }
            .pointerInput(steps) {
                detectHorizontalDragGestures { change, _ ->
                    var v = (change.position.x / size.width).coerceIn(0f, 1f)
                    if (steps > 0) v = Math.round(v * steps) / steps.toFloat()
                    onChange(v)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val thumbW = 38.dp
        val travel = maxWidth - thumbW
        Box(Modifier.fillMaxWidth().height(5.dp).background(p.fill, CircleShape))
        Box(Modifier.width(thumbW / 2 + travel * value).height(5.dp).background(p.accent, CircleShape))
        Box(
            Modifier
                .offset(x = travel * value)
                .size(width = thumbW, height = 24.dp)
                .shadow(4.dp, CircleShape)
                .background(Color.White, CircleShape)
        )
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, color: Color = IOS.Blue, onClick: () -> Unit) {
    Box(
        modifier
            .height(50.dp)
            .background(color, RoundedCornerShape(50))
            .clickable(interactionSource = remember { noRipple() }, indication = null, onClick = onClick)
            .padding(horizontal = 26.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TextStyle(fontFamily = Inter, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
    }
}

