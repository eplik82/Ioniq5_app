package com.eplik.ioniq5.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eplik.ioniq5.alert.text
import com.eplik.ioniq5.core.alert.Alert
import com.eplik.ioniq5.core.alert.Severity
import com.eplik.ioniq5.settings.PressureUnit
import com.eplik.ioniq5.ui.theme.Warning as WarningColor

/** Aktiivsed hoiatused ekraani ülaosas; puudutus viib vastavale vaatele. */
@Composable
fun AlertBanner(alerts: List<Alert>, unit: PressureUnit, onClick: (Alert) -> Unit, modifier: Modifier = Modifier) {
    if (alerts.isEmpty()) return
    val context = LocalContext.current
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        alerts.forEach { alert ->
            val color = when (alert.severity) {
                Severity.CRITICAL -> MaterialTheme.colorScheme.error
                Severity.WARNING -> WarningColor
                Severity.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                    .clickable { onClick(alert) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    if (alert.severity == Severity.INFO) Icons.Filled.Info else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp),
                )
                Text(alert.text(context, unit), style = MaterialTheme.typography.bodyMedium, color = color)
            }
        }
    }
}
