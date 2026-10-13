package dev.kidyoh.liquid.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kidyoh.liquid.ui.theme.Inter
import dev.kidyoh.liquid.wallpaper.Presets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val greetings = listOf("hello", "hola", "bonjour", "ciao", "hallo", "olá", "salam", "こんにちは", "你好", "namaste", "selam", "merhaba")

/** The iPhone-style "hello" welcome, ending with a button that makes us the Home app. */
@Composable
fun Onboarding(isDefault: Boolean, onSetDefault: () -> Unit, onSkip: () -> Unit) {
    var i by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1700)
            i = (i + 1) % greetings.size
        }
    }
    val t = rememberInfiniteTransition(label = "bg")
    val zoom by t.animateFloat(1f, 1.12f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse), label = "zoom")
    val shadow = Shadow(Color.Black.copy(alpha = 0.25f), Offset(0f, 2f), 12f)

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF1B2A6B))) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val bg by produceState<ImageBitmap?>(null, w, h) {
            value = withContext(Dispatchers.Default) { Presets.render(Presets.byId("aurora"), w / 2, h / 2, dark = false).asImageBitmap() }
        }
        bg?.let {
            Image(it, null, Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom }, contentScale = ContentScale.Crop)
        }
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            AnimatedContent(
                i,
                transitionSpec = { (fadeIn(tween(700)) + slideInVertically(tween(700)) { it / 3 }) togetherWith (fadeOut(tween(500)) + slideOutVertically(tween(500)) { -it / 3 }) },
                label = "hello",
            ) { idx ->
                Text(
                    greetings[idx],
                    style = TextStyle(fontFamily = Inter, fontSize = 76.sp, fontWeight = FontWeight.Medium, color = Color.White, letterSpacing = (-2).sp, shadow = shadow),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Welcome to Liquid Launcher",
                style = TextStyle(fontFamily = Inter, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, shadow = shadow),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "A Home Screen made of Liquid Glass. Make it your default Home app to replace your current launcher.",
                style = TextStyle(fontFamily = Inter, fontSize = 15.sp, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center, shadow = shadow),
            )
            Spacer(Modifier.height(26.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .background(Color.White, RoundedCornerShape(50))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        if (isDefault) onSkip() else onSetDefault()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (isDefault) "Continue" else "Set as Home Screen",
                    style = TextStyle(fontFamily = Inter, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.Black),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Customize first",
                style = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White),
                modifier = Modifier
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onSkip)
                    .padding(8.dp),
            )
        }
    }
}
