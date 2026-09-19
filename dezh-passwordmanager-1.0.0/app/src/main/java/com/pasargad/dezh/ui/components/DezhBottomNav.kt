package com.pasargad.dezh.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pasargad.dezh.R


/**
 * Bottom navigation with three tabs (vault / generator / settings) — the
 * research-backed standard for 3 top-level destinations. The middle dock is
 * emphasized as a filled circular key button (Instagram/X center-action
 * pattern): the generator is a creator action as much as a destination.
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
    NavigationBar(modifier = modifier, tonalElevation = 3.dp) {
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
