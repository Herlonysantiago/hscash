package hssolutions.hscash.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.max
import kotlin.math.roundToInt

enum class HelpFocus(
    val alignment: Alignment,
    val topPadding: Dp,
    val bottomPadding: Dp,
    val height: Dp,
    val explanationAtBottom: Boolean
) {
    TOP(Alignment.TopCenter, 12.dp, 0.dp, 155.dp, true),
    TOP_ROW(Alignment.TopCenter, 12.dp, 0.dp, 58.dp, true),
    TOP_OPTIONS(Alignment.TopCenter, 72.dp, 0.dp, 62.dp, true),
    CENTER(Alignment.Center, 0.dp, 0.dp, 210.dp, false),
    BOTTOM(Alignment.BottomCenter, 0.dp, 72.dp, 190.dp, false)
}

data class ScreenHelpStep(
    val title: String,
    val description: String,
    val focus: HelpFocus,
    val targetKey: String? = null
)

@Stable
class ScreenHelpTargets internal constructor() {
    internal val bounds = mutableStateMapOf<String, Rect>()
}

@Composable
fun rememberScreenHelpTargets(): ScreenHelpTargets = remember { ScreenHelpTargets() }

fun Modifier.screenHelpTarget(targets: ScreenHelpTargets, key: String): Modifier {
    return onGloballyPositioned { coordinates ->
        // Both the app content and the full-screen help dialog are laid out from
        // their Compose root. Window coordinates include the status-bar inset on
        // the activity but the dialog applies that inset again, shifting the
        // highlight down by one status bar. Root coordinates keep both spaces
        // aligned on devices with edge-to-edge/system-bar insets.
        if (coordinates.isAttached) targets.bounds[key] = coordinates.boundsInRoot()
    }
}

@Composable
fun ScreenHelp(
    screenKey: String,
    steps: List<ScreenHelpStep>,
    modifier: Modifier = Modifier,
    darkButton: Boolean = false,
    targets: ScreenHelpTargets? = null,
    onStepChanged: (Int) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var show by remember(screenKey) { mutableStateOf(!AppSettings.isScreenHelpSeen(context, screenKey)) }
    var stepIndex by remember(screenKey) { mutableIntStateOf(0) }

    LaunchedEffect(show, stepIndex) {
        if (show) onStepChanged(stepIndex)
    }

    if (!show) {
        Surface(
            modifier = modifier.size(42.dp),
            shape = CircleShape,
            color = if (darkButton) Color.Black.copy(alpha = 0.48f) else MaterialTheme.colorScheme.surfaceVariant
        ) {
            IconButton(onClick = { stepIndex = 0; show = true }) {
                Icon(
                    Icons.Default.HelpOutline,
                    contentDescription = "Ajuda desta tela",
                    tint = if (darkButton) Color.White else MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    if (show && steps.isNotEmpty()) {
        val step = steps[stepIndex]
        val targetBounds = step.targetKey?.let { targets?.bounds?.get(it) }
        val density = LocalDensity.current
        Dialog(
            onDismissRequest = {
                AppSettings.setScreenHelpSeen(context, screenKey)
                show = false
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.48f))) {
                val explanationAtBottom = targetBounds?.let { bounds ->
                    bounds.center.y < with(density) { maxHeight.toPx() / 2f }
                } ?: step.focus.explanationAtBottom
                if (targetBounds != null && targetBounds.width > 0f && targetBounds.height > 0f) {
                    val paddingPx = with(density) { 6.dp.toPx() }
                    val width = with(density) { (targetBounds.width + paddingPx * 2).toDp() }
                    val height = with(density) { (targetBounds.height + paddingPx * 2).toDp() }
                    Box(
                        Modifier
                            .offset {
                                IntOffset(
                                    max(0f, targetBounds.left - paddingPx).roundToInt(),
                                    max(0f, targetBounds.top - paddingPx).roundToInt()
                                )
                            }
                            .size(width, height)
                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                            .border(3.dp, Color(0xFF64B5F6), RoundedCornerShape(18.dp))
                    )
                } else {
                    Box(
                        Modifier
                            .align(step.focus.alignment)
                            .padding(horizontal = 14.dp)
                            .padding(top = step.focus.topPadding, bottom = step.focus.bottomPadding)
                            .fillMaxWidth()
                            .height(step.focus.height)
                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                            .border(3.dp, Color(0xFF64B5F6), RoundedCornerShape(18.dp))
                    )
                }

                Card(
                    modifier = Modifier
                        .align(if (explanationAtBottom) Alignment.BottomCenter else Alignment.TopCenter)
                        .padding(horizontal = 16.dp, vertical = 22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${stepIndex + 1} de ${steps.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = {
                                AppSettings.setScreenHelpSeen(context, screenKey)
                                show = false
                            }) { Text("Encerrar") }
                        }
                        Text(step.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(step.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Precisa de mais ajuda? herlonys97@gmail.com", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:herlonys97@gmail.com"))) }
                            }) { Icon(Icons.Default.Email, contentDescription = "Enviar e-mail", tint = Color(0xFF1565C0)) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (stepIndex > 0) TextButton(onClick = { stepIndex-- }) { Text("Voltar") }
                                Button(onClick = {
                                    if (stepIndex == steps.lastIndex) {
                                        AppSettings.setScreenHelpSeen(context, screenKey)
                                        show = false
                                    } else stepIndex++
                                }) { Text(if (stepIndex == steps.lastIndex) "Concluir" else "Próximo") }
                            }
                        }
                    }
                }
            }
        }
    }
}
