package com.eplik.ioniq5.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eplik.ioniq5.core.poll.Reading
import java.util.Locale

/** Näidu vanuse piir, millest alates näit kuvatakse tuhmina. */
const val STALE_AFTER_MS = 15_000L

fun Reading?.isFresh(nowMs: Long): Boolean = this != null && nowMs - timeMs < STALE_AFTER_MS

fun formatValue(value: Double?, decimals: Int): String =
    value?.let { String.format(Locale.getDefault(), "%.${decimals}f", it) } ?: "—"

/** Üks näit: silt, väärtus ja ühik. Aegunud väärtus on tuhm. */
@Composable
fun ValueTile(
    label: String,
    value: String,
    unit: String,
    fresh: Boolean,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .alpha(if (fresh) 1f else 0.45f),
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 28.sp, style = MaterialTheme.typography.displayMedium, maxLines = 1)
            Spacer(Modifier.width(4.dp))
            Text(
                unit,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        detail?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
