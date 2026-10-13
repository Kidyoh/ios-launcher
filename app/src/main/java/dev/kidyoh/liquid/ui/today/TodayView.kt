package dev.kidyoh.liquid.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kidyoh.liquid.LiquidApp
import dev.kidyoh.liquid.data.WidgetKind
import dev.kidyoh.liquid.data.WidgetSize
import dev.kidyoh.liquid.ui.glass.Glass
import dev.kidyoh.liquid.ui.home.GridMetrics
import dev.kidyoh.liquid.ui.home.HomeController
import dev.kidyoh.liquid.ui.home.LocalHome
import dev.kidyoh.liquid.ui.home.rememberLauncher
import dev.kidyoh.liquid.ui.search.AppRowGrid
import dev.kidyoh.liquid.ui.search.SectionTitle
import dev.kidyoh.liquid.ui.search.suggestions
import dev.kidyoh.liquid.ui.theme.Glyphs
import dev.kidyoh.liquid.ui.theme.Inter
import dev.kidyoh.liquid.ui.widgets.WidgetCard
import dev.kidyoh.liquid.ui.widgets.rememberNow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.ui.platform.LocalConfiguration

private val shadow = Shadow(Color.Black.copy(alpha = 0.3f), Offset(0f, 1f), 6f)

/** Swipe right from the first page: a stack of widgets, like iOS Today View. */
@Composable
fun TodayView(controller: HomeController, metrics: GridMetrics, topInset: Dp, sidePad: Dp) {
    val env = LocalHome.current
    val all by LiquidApp.instance.apps.apps.collectAsState()
    val launch = rememberLauncher()
    val locale = LocalConfiguration.current.locales[0]
    val now by rememberNow(60_000L)
    val today = remember(now / 60_000) { LocalDate.now() }
    val icon = env.icon
    val small = metrics.cellW + icon
    val smallH = metrics.cellH + icon
    val medium = metrics.cellW * 3 + icon
    val inset = sidePad + (metrics.cellW - icon) / 2

    LazyColumn(contentPadding = PaddingValues(top = topInset + 18.dp, bottom = 60.dp, start = inset, end = inset)) {
        item {
            Text(
                today.format(DateTimeFormatter.ofPattern("EEEE", locale)),
                style = TextStyle(fontFamily = Inter, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.8f), shadow = shadow),
            )
            Text(
                today.format(DateTimeFormatter.ofPattern("MMMM d", locale)),
                style = TextStyle(fontFamily = Inter, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = (-0.8).sp, shadow = shadow),
            )
            Spacer(Modifier.height(16.dp))
            Glass(
                Modifier.fillMaxWidth().height(44.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { controller.spotlight = true },
                corner = Dp.Infinity,
            ) {
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Glyphs.Search, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Search", style = TextStyle(fontFamily = Inter, fontSize = 16.sp, color = Color.White.copy(alpha = 0.85f)))
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                WidgetCard(WidgetKind.AnalogClock, WidgetSize.Small, small, smallH)
                WidgetCard(WidgetKind.Battery, WidgetSize.Small, small, smallH)
            }
            Spacer(Modifier.height(18.dp))
            WidgetCard(WidgetKind.Month, WidgetSize.Medium, medium, smallH)
            Spacer(Modifier.height(18.dp))
            WidgetCard(WidgetKind.WorldClock, WidgetSize.Medium, medium, smallH)
        }
        item {
            Column {
                SectionTitle("Siri Suggestions")
                AppRowGrid(suggestions(all, 8)) { a, b -> launch(a, b) }
            }
        }
    }
}
