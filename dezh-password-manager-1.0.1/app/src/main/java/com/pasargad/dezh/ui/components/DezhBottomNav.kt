package com.pasargad.dezh.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import com.pasargad.dezh.R

/**
 * Bottom navigation with three tabs (vault / generator / settings) — the
 * research-backed standard for 3 top-level destinations. Uses the PLATFORM
 * NavigationBar as-is: its built-in M3 indicator hugs the active icon inside
 * the tab row. (A previous custom floating pill read as a stray surface
 * blob — removed; platform-native rendering wins.)
 * Children render start-first, so in RTL the vault sits on the right.
 */
@Composable
fun DezhBottomNav(
    current: DezhTab,
    onVault: () -> Unit,
    onGenerator: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        NavigationBarItem(
            selected = current == DezhTab.VAULT,
            onClick = onVault,
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_nav_vault),
                    contentDescription = null, // the label carries the meaning
                )
            },
            label = { Text(stringResource(R.string.nav_vault)) },
        )
        NavigationBarItem(
            selected = current == DezhTab.GENERATOR,
            onClick = onGenerator,
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_nav_key),
                    contentDescription = null, // the label carries the meaning
                    modifier = Modifier.size(26.dp),
                )
            },
            label = { Text(stringResource(R.string.nav_generator)) },
        )
        NavigationBarItem(
            selected = current == DezhTab.SETTINGS,
            onClick = onSettings,
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_nav_settings),
                    contentDescription = null, // the label carries the meaning
                )
            },
            label = { Text(stringResource(R.string.nav_settings)) },
        )
    }
}
