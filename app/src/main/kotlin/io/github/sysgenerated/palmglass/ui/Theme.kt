package io.github.sysgenerated.palmglass.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun PalmGlassTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = when {
        dark -> runCatching { dynamicDarkColorScheme(context) }.getOrElse { darkColorScheme() }
        else -> runCatching { dynamicLightColorScheme(context) }.getOrElse { lightColorScheme() }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
