package pm.antani.resentin.ui.channelsettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import pm.antani.resentin.R
import pm.antani.resentin.domain.repository.ServerMute
import pm.antani.resentin.net.dto.BanlistEntryDto

private val LIST_MODE_FALLBACK = listOf("b", "e", "I", "q")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChannelSettingsScreen(
    viewModel: ChannelSettingsViewModel,
    title: String,
    onBack: () -> Unit,
    onParted: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val isPinned by viewModel.isPinned.collectAsState()
    val serverMute by viewModel.serverMute.collectAsState()
    val presencePin by viewModel.presencePin.collectAsState()

    LaunchedEffect(state.parted) {
        if (state.parted) onParted()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ChannelSettingsSection(
                    icon = Icons.Default.Tag,
                    title = stringResource(R.string.channel_settings_topic_label),
                ) {
                    OutlinedTextField(
                        value = state.topic,
                        onValueChange = viewModel::onTopicChange,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                    state.error?.let { error ->
                        Spacer(Modifier.height(8.dp))
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }
                    if (state.saved) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.channel_settings_topic_updated), color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = viewModel::saveTopic, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.channel_settings_update_topic))
                    }
                }
            }

            item {
                ChannelSettingsSection(
                    icon = Icons.Default.Settings,
                    title = stringResource(R.string.channel_settings_modes_title),
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SIMPLE_TOGGLE_MODES.forEach { letter ->
                            val checked = state.modes.modes.any { it.firstOrNull() == letter }
                            FilterChip(
                                selected = checked,
                                enabled = state.isPrivileged,
                                onClick = { viewModel.toggleSimpleMode(letter) },
                                label = { Text("+$letter") },
                            )
                        }
                    }
                    if (state.isPrivileged) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = state.rawModeInput,
                            onValueChange = viewModel::onRawModeInputChange,
                            placeholder = { Text(stringResource(R.string.channel_settings_raw_mode_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = viewModel::applyRawMode,
                            enabled = state.rawModeInput.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.channel_settings_apply_mode))
                        }
                    }
                }
            }
            item {
                ChannelSettingsSection(
                    icon = Icons.Default.Settings,
                    title = stringResource(R.string.settings_title),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.channel_settings_mute), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                muteStatusText(serverMute),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = serverMute.muted,
                            enabled = serverMute.loaded,
                            onCheckedChange = { muted ->
                                if (muted) viewModel.muteForever() else viewModel.unmute()
                            },
                        )
                    }
                    if (serverMute.muted) {
                        Spacer(Modifier.height(8.dp))
                        data class MuteOption(val label: String, val durationSeconds: Long? = null, val atEpochSeconds: Long? = null)
                        val tomorrowMidnight = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
                        val muteOptions = listOf(
                            MuteOption(stringResource(R.string.channel_settings_mute_forever)),
                            MuteOption("1h", durationSeconds = 3_600L),
                            MuteOption("8h", durationSeconds = 28_800L),
                            MuteOption(stringResource(R.string.channel_settings_mute_tomorrow), atEpochSeconds = tomorrowMidnight),
                        )
                        LazyRow {
                            items(muteOptions) { option ->
                                val selected = when {
                                    option.durationSeconds == null && option.atEpochSeconds == null -> serverMute.until == null
                                    option.durationSeconds != null -> {
                                        val remaining = (serverMute.until ?: 0) - System.currentTimeMillis() / 1000
                                        remaining in (option.durationSeconds - 120)..(option.durationSeconds + 120)
                                    }
                                    else -> serverMute.until == option.atEpochSeconds
                                }
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        when {
                                            option.durationSeconds != null -> viewModel.muteFor(option.durationSeconds)
                                            option.atEpochSeconds != null -> viewModel.muteUntil(option.atEpochSeconds)
                                            else -> viewModel.muteForever()
                                        }
                                    },
                                    label = { Text(option.label) },
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Column {
                        Text(stringResource(R.string.channel_settings_presence_title), style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.channel_settings_presence_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        val presenceOptions = listOf(
                            Triple(stringResource(R.string.channel_settings_presence_show), "show", presencePin == "show"),
                            Triple(stringResource(R.string.channel_settings_presence_default), null, presencePin == null),
                            Triple(stringResource(R.string.channel_settings_presence_hide), "hide", presencePin == "hide"),
                        )
                        LazyRow {
                            items(presenceOptions) { (label, pin, selected) ->
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.setPresencePin(pin) },
                                    label = { Text(label) },
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.channel_settings_device_title), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.channel_settings_pin),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = isPinned, onCheckedChange = { viewModel.togglePinned() })
                    }
                }
            }
            item {
                ChannelSettingsSection(
                    icon = Icons.Default.Tag,
                    title = stringResource(R.string.channel_settings_lists_title),
                ) {
                    val letters = state.listModeLetters.ifEmpty { LIST_MODE_FALLBACK }
                    LazyRow {
                        items(letters) { letter ->
                            FilterChip(
                                selected = state.activeListMode == letter,
                                onClick = { viewModel.selectListMode(letter) },
                                label = { Text(listModeLabel(letter)) },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (state.banlistLoading) {
                        CircularProgressIndicator(Modifier.height(24.dp))
                    } else if (state.banlistEntries.isEmpty()) {
                        Text(stringResource(R.string.channel_settings_list_empty), style = MaterialTheme.typography.bodySmall)
                    } else {
                        state.banlistEntries.forEachIndexed { index, entry ->
                            BanlistRow(
                                entry = entry,
                                canRemove = state.isPrivileged,
                                onRemove = { viewModel.removeListModeEntry(entry.mask) },
                            )
                            if (index < state.banlistEntries.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                    if (state.isPrivileged) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = state.newMaskInput,
                                onValueChange = viewModel::onNewMaskChange,
                                placeholder = { Text(stringResource(R.string.channel_settings_new_mask_hint)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = viewModel::addListModeEntry, enabled = state.newMaskInput.isNotBlank()) {
                                Text(stringResource(R.string.channel_settings_add_mask))
                            }
                        }
                    }
                }
            }

            item {
                ChannelSettingsSection(
                    icon = Icons.Default.Delete,
                    title = stringResource(R.string.channel_settings_part),
                ) {
                    OutlinedButton(
                        onClick = viewModel::part,
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.channel_settings_part), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelSettingsSection(
    icon: ImageVector,
    title: String,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            description?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}
@Composable
private fun BanlistRow(entry: BanlistEntryDto, canRemove: Boolean, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.mask, style = MaterialTheme.typography.bodyLarge)
            val meta = listOfNotNull(
                entry.setter?.let { stringResource(R.string.channel_settings_list_set_by, it) },
                entry.setTs?.let { formatEpochSeconds(it) },
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (canRemove) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_remove))
            }
        }
    }
}

private fun listModeLabel(letter: String): String = when (letter) {
    "b" -> "Ban (+b)"
    "e" -> "Exempt (+e)"
    "I" -> "Invite (+I)"
    "q" -> "Quiet (+q)"
    "z" -> "Restrict (+z)"
    else -> "+$letter"
}

/** Mute subtitle: permanent, "until <date>", or the plain desc when unmuted. */
@Composable
private fun muteStatusText(mute: ServerMute): String {
    if (!mute.muted) return stringResource(R.string.channel_settings_mute_desc)
    val until = mute.until ?: return stringResource(R.string.channel_settings_mute_forever)
    return stringResource(R.string.channel_settings_muted_until, formatEpochSeconds(until))
}

private val LIST_ENTRY_TIMESTAMP_FORMATTER =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())

private fun formatEpochSeconds(seconds: Long): String =
    runCatching { LIST_ENTRY_TIMESTAMP_FORMATTER.format(Instant.ofEpochSecond(seconds)) }.getOrDefault(seconds.toString())
