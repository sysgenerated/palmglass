package io.github.sysgenerated.palmglass.ui

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.provider.Settings as SysSettings
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sysgenerated.palmglass.BuildConfig
import io.github.sysgenerated.palmglass.core.Displays
import io.github.sysgenerated.palmglass.core.Prefs
import io.github.sysgenerated.palmglass.core.SessionState
import io.github.sysgenerated.palmglass.core.Settings
import io.github.sysgenerated.palmglass.ime.PalmGlassInputMethodService
import io.github.sysgenerated.palmglass.service.PalmGlassAccessibilityService
import io.github.sysgenerated.palmglass.touchpad.TouchpadActivity
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        setContent {
            PalmGlassTheme {
                Scaffold { padding ->
                    SetupScreen(prefs, Modifier.padding(padding))
                }
            }
        }
    }
}

private data class Checks(
    val service: Boolean,
    val ime: Boolean,
    val notifications: Boolean,
    val display: Boolean,
)

private fun runChecks(context: Context): Checks {
    val serviceComponent = ComponentName(context, PalmGlassAccessibilityService::class.java)
    val imeComponent = ComponentName(context, PalmGlassInputMethodService::class.java)

    // Settings.Secure keys for these lists are hidden from apps targeting Android 14+,
    // so ask the managers instead.
    val service = runCatching {
        context.getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.let { si -> ComponentName(si.packageName, si.name) } == serviceComponent }
    }.getOrDefault(false)
    val ime = runCatching {
        context.getSystemService(InputMethodManager::class.java)
            .enabledInputMethodList
            .any { it.component == imeComponent }
    }.getOrDefault(false)
    val notifications = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    val display = runCatching {
        Displays.findExternal(context.getSystemService(DisplayManager::class.java)) != null
    }.getOrDefault(false)
    return Checks(service = service, ime = ime, notifications = notifications, display = display)
}

@Composable
private fun SetupScreen(prefs: Prefs, modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by prefs.settings.collectAsStateWithLifecycle(Settings())
    val serviceRunning by SessionState.serviceRunning.collectAsStateWithLifecycle()
    var checks by remember { mutableStateOf(runChecks(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) checks = runChecks(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        checks = runChecks(context)
    }

    Column(
        modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("PalmGlass", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Turns the phone into a trackpad and keyboard for the glasses. " +
                "Complete the steps below once. When the glasses are plugged in, open the touchpad " +
                "from the notification or from the button below.",
            style = MaterialTheme.typography.bodyMedium,
        )

        StepCard(
            title = "1. Accessibility service",
            ok = checks.service && serviceRunning,
            detail = if (checks.service && !serviceRunning) "Enabled but not running yet" else
                "Draws the cursor on the glasses and sends taps and drags there.",
            buttonLabel = "Open accessibility settings",
        ) { context.startActivity(Intent(SysSettings.ACTION_ACCESSIBILITY_SETTINGS)) }

        StepCard(
            title = "2. PalmGlass keyboard",
            ok = checks.ime,
            detail = "Lets the phone type into fields on the glasses. Enable it here, then pick it with Choose keyboard when you start a session.",
            buttonLabel = "Open keyboard settings",
        ) { context.startActivity(Intent(SysSettings.ACTION_INPUT_METHOD_SETTINGS)) }

        StepCard(
            title = "3. Notifications",
            ok = checks.notifications,
            detail = "Shows the Glasses connected notice that opens the touchpad, and a Stop button during a session.",
            buttonLabel = "Allow notifications",
        ) { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }

        StepCard(
            title = "Glasses",
            ok = checks.display,
            detail = if (checks.display) "External display connected." else "Plug the glasses in with the USB-C cable.",
            buttonLabel = null,
        ) {}

        Spacer(Modifier.height(4.dp))
        Text("Settings", style = MaterialTheme.typography.titleMedium)

        ToggleRow("Open touchpad when glasses connect", settings.autoStart) {
            scope.launch { prefs.setAutoStart(it) }
        }
        ToggleRow("Dim phone screen while touchpad is open", settings.dimScreen) {
            scope.launch { prefs.setDimScreen(it) }
        }
        ToggleRow("Natural scrolling", settings.naturalScroll) {
            scope.launch { prefs.setNaturalScroll(it) }
        }
        ToggleRow("Hand keyboard back when touchpad is closed", settings.handBackKeyboard) {
            scope.launch { prefs.setHandBackKeyboard(it) }
        }

        Text("Cursor speed", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = settings.sensitivity,
            onValueChange = { scope.launch { prefs.setSensitivity(it) } },
            valueRange = 0.4f..3f,
        )

        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { context.startActivity(Intent(context, TouchpadActivity::class.java)) },
                enabled = serviceRunning,
            ) { Text("Open touchpad") }
            OutlinedButton(onClick = {
                context.getSystemService(InputMethodManager::class.java).showInputMethodPicker()
            }) { Text("Choose keyboard") }
        }

        Spacer(Modifier.height(8.dp))
        Text("Gestures", style = MaterialTheme.typography.titleMedium)
        Text(
            """
            One finger: move cursor. Tap: click. Tap then press and move: drag.
            Two fingers: scroll. Two-finger tap: long press (context menu).
            Three-finger tap: lock or unlock input (two buzzes locked, one unlocked). The cursor hides while locked.
            Three-finger swipe down: exit. Three-finger swipe up: app launcher. Three-finger swipe sideways: keyboard.
            On a panel, a three-finger swipe closes it. The cursor hides after 3 seconds without input.
            """.trimIndent(),
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun StepCard(title: String, ok: Boolean, detail: String, buttonLabel: String?, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (ok) "✓" else "○", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(detail, style = MaterialTheme.typography.bodySmall)
            if (buttonLabel != null && !ok) {
                OutlinedButton(onClick = onClick) { Text(buttonLabel) }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
