package io.github.sysgenerated.palmglass.touchpad

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import io.github.sysgenerated.palmglass.ime.KeyboardBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val PanelBg = Color(0xFF14181C)
private val KeyBg = Color(0xFF262C33)
private val KeyFg = Color(0xFFD8DEE6)

@Composable
fun TouchpadScreen(
    touchpadView: TouchpadView,
    locked: Boolean,
    showKeyboard: Boolean,
    keyboardActive: Boolean,
    showLauncher: Boolean,
    onDismissLauncher: () -> Unit,
    onLauncherToKeyboard: () -> Unit,
    onDismissKeyboard: () -> Unit,
    onChooseKeyboard: () -> Unit,
    onLaunchApp: (LaunchableApp) -> Unit,
    onExit: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { touchpadView }, modifier = Modifier.fillMaxSize())

        if (locked) {
            Text(
                "locked",
                color = Color(0xFF3A3F45),
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (showLauncher) {
            LauncherPanel(
                modifier = Modifier.fillMaxSize(),
                onDismiss = onDismissLauncher,
                onSwitchToKeyboard = onLauncherToKeyboard,
                onChooseKeyboard = onChooseKeyboard,
                onLaunchApp = onLaunchApp,
                onExit = onExit,
            )
        } else if (showKeyboard) {
            KeyboardPanel(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                keyboardActive = keyboardActive,
                onChooseKeyboard = onChooseKeyboard,
                onDismiss = onDismissKeyboard,
            )
        }
    }
}

@Composable
private fun LauncherPanel(
    modifier: Modifier,
    onDismiss: () -> Unit,
    onSwitchToKeyboard: () -> Unit,
    onChooseKeyboard: () -> Unit,
    onLaunchApp: (LaunchableApp) -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val apps by produceState<List<LaunchableApp>>(emptyList()) {
        value = withContext(Dispatchers.IO) { AppLauncher.list(context) }
    }
    Column(
        modifier
            .background(PanelBg)
            .threeFingerSwipe { dx, dy ->
                // Up or down closes the launcher; sideways swaps it for the keyboard.
                if (kotlin.math.abs(dy) >= kotlin.math.abs(dx)) onDismiss() else onSwitchToKeyboard()
            }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Open on glasses", color = KeyFg, fontSize = 16.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onChooseKeyboard) { Text("Keyboard", color = KeyFg) }
            TextButton(onClick = onExit) { Text("Exit", color = KeyFg) }
        }
        Text(
            "✕  Close",
            color = KeyFg, fontSize = 15.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(KeyBg, RoundedCornerShape(10.dp))
                .clickable { onDismiss() }
                .padding(12.dp),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(apps, key = { it.component.flattenToString() }) { app ->
                val bitmap = remember(app) { app.icon.toBitmap(96, 96).asImageBitmap() }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .background(KeyBg, RoundedCornerShape(12.dp))
                        .clickable { onLaunchApp(app) }
                        .padding(8.dp),
                ) {
                    Image(bitmap, contentDescription = app.label, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        app.label, color = KeyFg, fontSize = 11.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private val ROWS_LETTERS = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
private val ROWS_SYMBOLS = listOf("1234567890", "@#\$%&-+()/", "*\"':;!?,.")

@Composable
private fun KeyboardPanel(
    modifier: Modifier,
    keyboardActive: Boolean,
    onChooseKeyboard: () -> Unit,
    onDismiss: () -> Unit,
) {
    var shift by remember { mutableStateOf(false) }
    var symbols by remember { mutableStateOf(false) }
    val rows = if (symbols) ROWS_SYMBOLS else ROWS_LETTERS

    Column(
        modifier
            .background(PanelBg)
            .threeFingerSwipe { _, _ -> onDismiss() }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(6.dp),
    ) {
        if (!keyboardActive) {
            Text(
                "PalmGlass keyboard is not selected. Tap to choose it.",
                color = Color(0xFFFFB74D), fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().clickable { onChooseKeyboard() }.padding(8.dp),
            )
        }
        rows.forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.Center) {
                if (rowIndex == 2 && !symbols) {
                    Key("⇧", weight = 1.5f, active = shift) { shift = !shift }
                }
                row.forEach { ch ->
                    val label = if (shift && !symbols) ch.uppercaseChar().toString() else ch.toString()
                    Key(label) {
                        KeyboardBridge.type(label)
                        if (shift) shift = false
                    }
                }
                if (rowIndex == 2) {
                    Key("⌫", weight = 1.5f) { KeyboardBridge.backspace() }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.Center) {
            Key(if (symbols) "abc" else "?123", weight = 1.6f) { symbols = !symbols }
            Key("space", weight = 5f) { KeyboardBridge.type(" ") }
            Key("enter", weight = 2f) { KeyboardBridge.enter() }
            Key("hide", weight = 1.2f) { onDismiss() }
        }
    }
}

@Composable
private fun RowScope.Key(
    label: String,
    weight: Float = 1f,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .padding(2.dp)
            .background(if (active) Color(0xFF3D5A80) else KeyBg, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
    ) {
        Text(label, color = KeyFg, fontSize = 16.sp)
    }
}

/**
 * Recognises a three-finger swipe on a panel that would otherwise swallow it. Observed in the
 * initial pass and consumed, so lists under the panel do not scroll while three fingers are down.
 */
@Composable
private fun Modifier.threeFingerSwipe(onSwipe: (dx: Float, dy: Float) -> Unit): Modifier {
    val thresholdPx = with(LocalDensity.current) { 48.dp.toPx() }
    return pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var armed = false
            var fired = false
            var start = Offset.Zero
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size >= 3) {
                    var sum = Offset.Zero
                    pressed.forEach { sum += it.position }
                    val centroid = sum / pressed.size.toFloat()
                    if (!armed) { armed = true; start = centroid }
                    else if (!fired) {
                        val d = centroid - start
                        if (d.getDistance() > thresholdPx) { fired = true; onSwipe(d.x, d.y) }
                    }
                }
                if (armed) event.changes.forEach { it.consume() }
                if (event.changes.none { it.pressed }) break
            }
        }
    }
}
