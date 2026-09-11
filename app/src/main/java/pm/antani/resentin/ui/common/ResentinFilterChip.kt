package pm.antani.resentin.ui.common

import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** App-wide single-select chip — same signature as M3's [FilterChip], but the
 * selected state fills with `primary`/`onPrimary` instead of the default pale
 * `secondaryContainer`, which read as washed-out and near-indistinguishable from
 * the card surfaces on the light theme. Strong on both themes: indigo fill with
 * white label on light, lavender fill with dark label on dark. */
@Composable
fun ResentinFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}
