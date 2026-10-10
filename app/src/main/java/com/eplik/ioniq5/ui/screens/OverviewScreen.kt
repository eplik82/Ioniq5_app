package com.eplik.ioniq5.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eplik.ioniq5.R
import com.eplik.ioniq5.ui.components.PixelGauge

@Composable
fun OverviewScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(R.string.state_of_charge).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = stringResource(R.string.no_value), style = MaterialTheme.typography.displayLarge)
        PixelGauge(fraction = null, modifier = Modifier.fillMaxWidth(0.8f))
        Text(
            text = stringResource(R.string.connection_disconnected),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Ühendus tuleb 1. etapis
        OutlinedButton(onClick = {}, enabled = false) {
            Text(stringResource(R.string.connect))
        }
    }
}
