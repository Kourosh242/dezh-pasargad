package com.pasargad.dezh

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasargad.dezh.settings.DezhSettings
import com.pasargad.dezh.settings.ThemeMode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pasargad.dezh.di.AppContainer
import com.pasargad.dezh.navigation.DezhApp
import com.pasargad.dezh.ui.theme.DezhTheme

/**
 * Single entry point Activity. All UI is Jetpack Compose (no XML layouts,
 * no ViewBinding, no Fragment-based screens). Navigation follows the vault
 * lock lifecycle; this class stays minimal and holds no secrets.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container: AppContainer = (application as DezhApplication).container
        enableEdgeToEdge()
        setContent {
            val settings by container.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = DezhSettings())
            val themeMode = settings.themeMode
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val windowSizeClass = calculateWindowSizeClass(this)
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, fontScale = settings.fontScale),
            ) {
                DezhTheme(darkTheme = darkTheme, accentColor = settings.accentColor) {
                    DezhApp(
                        container = container,
                        windowWidthSizeClass = windowSizeClass.widthSizeClass,
                    )
                }
            }
        }
    }
}
