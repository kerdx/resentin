package pm.antani.resentin.ui.appsettings

import android.Manifest
import android.app.LocaleManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.LocaleList
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import org.unifiedpush.android.connector.UnifiedPush
import pm.antani.resentin.R
import pm.antani.resentin.data.prefs.ChatDisplayMode
import pm.antani.resentin.data.prefs.ReplyStyle
import pm.antani.resentin.net.dto.PushSubscriptionSummaryDto
import pm.antani.resentin.net.dto.VhostOptionDto
import pm.antani.resentin.ui.common.ResentinHeaderAction
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// #348 on grappa-irc — the auto-away ladder the control offers. Deliberately coarse:
// these are the answers to "how long before my friends see me as away", anything
// between the rungs is what the custom entry is for. Mirrors cic's SettingsDrawer.
private val AUTO_AWAY_PRESETS = listOf(
    60 to R.string.settings_auto_away_1min,
    300 to R.string.settings_auto_away_5min,
    600 to R.string.settings_auto_away_10min,
    1800 to R.string.settings_auto_away_30min,
    3600 to R.string.settings_auto_away_1h,
)
private val AUTO_AWAY_PRESET_SECONDS = AUTO_AWAY_PRESETS.map { it.first }.toSet()

// Text-size ladder for the slider below — labels need no translation (XS–XXL are
// universal), the scale applies app-wide through ResentinTheme.
private val FONT_SCALES = listOf(0.8f, 0.9f, 1f, 1.15f, 1.3f)
private val FONT_SCALE_LABELS = listOf("XS", "S", "M", "L", "XXL")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppSettingsScreen(viewModel: AppSettingsViewModel, onBack: () -> Unit, onAdminClick: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsState()
    val stayConnected by viewModel.stayConnected.collectAsState()
    val autoAwayDebounceSeconds by viewModel.autoAwayDebounceSeconds.collectAsState()
    val pushEnabled by viewModel.pushEnabled.collectAsState()
    val pushDecryptionFailureAt by viewModel.pushDecryptionFailureAt.collectAsState()
    val chatDisplayMode by viewModel.chatDisplayMode.collectAsState()
    val showSeconds by viewModel.showSeconds.collectAsState()
    val showHostmaskInEvents by viewModel.showHostmaskInEvents.collectAsState()
    val unreadFirst by viewModel.unreadFirst.collectAsState()
    val fontScale by viewModel.fontScale.collectAsState()
    val replyStyle by viewModel.replyStyle.collectAsState()
    val messageDbSizeBytes by viewModel.messageDbSizeBytes.collectAsState()
    var showClearMessagesConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.setStayConnected(true)
    }

    fun onStayConnectedChange(enabled: Boolean) {
        if (!enabled) {
            viewModel.setStayConnected(false)
            return
        }
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setStayConnected(true)
        }
    }

    val noDistributorMessage = stringResource(R.string.settings_push_no_distributor)
    val autoAwayInvalidInputMessage = stringResource(R.string.settings_auto_away_invalid_input)

    fun linkDistributorAndEnable() {
        // tryUseCurrentOrDefaultDistributor reuses the already-saved distributor without
        // needing an Activity; it only falls into the Activity-requiring deeplink path
        // (`context is Activity`, true here since LocalContext.current is the hosting
        // MainActivity) the first time, or after the saved distributor was uninstalled.
        UnifiedPush.tryUseCurrentOrDefaultDistributor(context) { success ->
            if (success) {
                viewModel.enablePushAfterDistributorLinked()
            } else {
                viewModel.reportPushLinkFailed(noDistributorMessage)
            }
        }
    }

    val pushPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) linkDistributorAndEnable() }

    fun onPushEnabledChange(enabled: Boolean) {
        if (!enabled) {
            viewModel.disablePush()
            return
        }
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pushPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            linkDistributorAndEnable()
        }
    }

    // On API 33+ the platform's own LocaleManager is the source of truth for the
    // per-app language (it's what drives the system Settings > Apps > Resentin >
    // Language picker too, via android:localeConfig in the manifest) — going through
    // AppCompatDelegate.setApplicationLocales() instead silently no-ops here: its
    // API 33+ bridge to LocaleManager apparently needs an AppCompatActivity to actually
    // persist the change, which MainActivity (a plain ComponentActivity, for Compose)
    // isn't. AppCompatDelegate is kept only as the API <33 fallback, where it's the
    // sole mechanism available (no platform LocaleManager to call directly).
    var currentLanguageTag by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getSystemService(LocaleManager::class.java)?.applicationLocales?.get(0)?.language
            } else {
                AppCompatDelegate.getApplicationLocales().get(0)?.language
            },
        )
    }

    fun setLanguage(languageTag: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (languageTag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(languageTag)
        } else {
            AppCompatDelegate.setApplicationLocales(
                if (languageTag == null) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(languageTag)
                },
            )
        }
        currentLanguageTag = languageTag
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    ResentinHeaderAction(
                        onClick = onBack,
                        icon = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ResentinSectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_stay_connected),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            )
                            Text(
                                stringResource(R.string.settings_stay_connected_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = stayConnected, onCheckedChange = ::onStayConnectedChange)
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.settings_colored_nicklist),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = state.displayPrefs.coloredNicklist,
                            onCheckedChange = { viewModel.toggleColoredNicklist() },
                        )
                    }
                }
            }
            item {
                ResentinSectionCard(title = stringResource(R.string.settings_chat_display)) {
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = chatDisplayMode == ChatDisplayMode.BUBBLES,
                        onClick = { viewModel.setChatDisplayMode(ChatDisplayMode.BUBBLES) },
                        label = { Text(stringResource(R.string.settings_display_bubbles)) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    FilterChip(
                        selected = chatDisplayMode == ChatDisplayMode.IRC_LINE,
                        onClick = { viewModel.setChatDisplayMode(ChatDisplayMode.IRC_LINE) },
                        label = { Text(stringResource(R.string.settings_display_irc_line)) },
                    )
                }
                Text(
                    stringResource(R.string.settings_display_irc_line_desc),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_show_seconds),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        )
                        Text(
                            stringResource(R.string.settings_show_seconds_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = showSeconds, onCheckedChange = viewModel::setShowSeconds)
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_show_hostmask),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        )
                        Text(
                            stringResource(R.string.settings_show_hostmask_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = showHostmaskInEvents, onCheckedChange = viewModel::setShowHostmaskInEvents)
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_unread_first),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        )
                        Text(
                            stringResource(R.string.settings_unread_first_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = unreadFirst, onCheckedChange = viewModel::setUnreadFirst)
                }
            }
            }
            item {
                ResentinSectionCard(title = stringResource(R.string.settings_font_size)) {
                Spacer(Modifier.height(8.dp))
                // Fixed five-stop slider (XS–XXL): discrete writes, live theme preview.
                val scaleIndex = FONT_SCALES.indices.minByOrNull { kotlin.math.abs(FONT_SCALES[it] - fontScale) } ?: 2
                Slider(
                    value = scaleIndex.toFloat(),
                    onValueChange = { viewModel.setFontScale(FONT_SCALES[it.roundToInt()]) },
                    valueRange = 0f..(FONT_SCALES.size - 1).toFloat(),
                    steps = FONT_SCALES.size - 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    FONT_SCALE_LABELS.forEach { label ->
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("AaBbCc 123", style = MaterialTheme.typography.displaySmall)
                Text(
                    stringResource(R.string.settings_font_size_preview),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            }
            item {
                ResentinSectionCard(title = stringResource(R.string.settings_reply_style_title)) {
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = replyStyle == ReplyStyle.NICK,
                        onClick = { viewModel.setReplyStyle(ReplyStyle.NICK) },
                        label = { Text(stringResource(R.string.settings_reply_style_nick)) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    FilterChip(
                        selected = replyStyle == ReplyStyle.QUOTE,
                        onClick = { viewModel.setReplyStyle(ReplyStyle.QUOTE) },
                        label = { Text(stringResource(R.string.settings_reply_style_quote)) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    FilterChip(
                        selected = replyStyle == ReplyStyle.CUSTOM,
                        onClick = { viewModel.setReplyStyle(ReplyStyle.CUSTOM) },
                        label = { Text(stringResource(R.string.settings_reply_style_custom)) },
                    )
                }
                if (replyStyle == ReplyStyle.CUSTOM) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.replyCustomTemplate,
                        onValueChange = viewModel::onReplyCustomTemplateChange,
                        placeholder = { Text(stringResource(R.string.settings_reply_custom_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.settings_reply_custom_placeholders_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = viewModel::saveReplyCustomTemplate,
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Text(stringResource(R.string.network_settings_save))
                        }
                        if (state.replyCustomTemplateSaved) {
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_reply_custom_saved), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            }
            item {
                ResentinSectionCard(title = stringResource(R.string.settings_language)) {
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = currentLanguageTag == null,
                        onClick = { setLanguage(null) },
                        label = { Text(stringResource(R.string.settings_language_system)) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    FilterChip(
                        selected = currentLanguageTag == "it",
                        onClick = { setLanguage("it") },
                        label = { Text(stringResource(R.string.settings_language_italian)) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    FilterChip(
                        selected = currentLanguageTag == "en",
                        onClick = { setLanguage("en") },
                        label = { Text(stringResource(R.string.settings_language_english)) },
                    )
                }
            }
            }
            item {
                ResentinSectionCard(title = stringResource(R.string.settings_auto_away_title)) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.settings_auto_away_label))
                Spacer(Modifier.height(8.dp))
                val autoAwayIsCustomValue = autoAwayDebounceSeconds != null &&
                    autoAwayDebounceSeconds != 0 &&
                    autoAwayDebounceSeconds !in AUTO_AWAY_PRESET_SECONDS
                val autoAwayShowCustom = state.autoAwayCustomMode || autoAwayIsCustomValue
                FlowRow {
                    FilterChip(
                        selected = !autoAwayShowCustom && autoAwayDebounceSeconds == null,
                        onClick = { viewModel.onAutoAwayPresetSelected(null) },
                        label = { Text(stringResource(R.string.settings_auto_away_site_default)) },
                        modifier = Modifier.padding(end = 8.dp, bottom = 8.dp),
                    )
                    FilterChip(
                        selected = !autoAwayShowCustom && autoAwayDebounceSeconds == 0,
                        onClick = { viewModel.onAutoAwayPresetSelected(0) },
                        label = { Text(stringResource(R.string.settings_auto_away_off)) },
                        modifier = Modifier.padding(end = 8.dp, bottom = 8.dp),
                    )
                    AUTO_AWAY_PRESETS.forEach { (seconds, labelRes) ->
                        FilterChip(
                            selected = !autoAwayShowCustom && autoAwayDebounceSeconds == seconds,
                            onClick = { viewModel.onAutoAwayPresetSelected(seconds) },
                            label = { Text(stringResource(labelRes)) },
                            modifier = Modifier.padding(end = 8.dp, bottom = 8.dp),
                        )
                    }
                    FilterChip(
                        selected = autoAwayShowCustom,
                        onClick = { viewModel.onAutoAwayCustomModeSelected() },
                        label = { Text(stringResource(R.string.settings_reply_style_custom)) },
                        modifier = Modifier.padding(end = 8.dp, bottom = 8.dp),
                    )
                }
                if (autoAwayShowCustom) {
                    OutlinedTextField(
                        value = if (state.autoAwayCustomMode) {
                            state.autoAwayCustomDraft
                        } else {
                            autoAwayDebounceSeconds?.toString().orEmpty()
                        },
                        onValueChange = viewModel::onAutoAwayCustomDraftChange,
                        label = { Text(stringResource(R.string.settings_auto_away_custom_seconds)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.saveAutoAwayCustomDraft(autoAwayInvalidInputMessage) },
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(stringResource(R.string.network_settings_save))
                    }
                }
                Text(
                    stringResource(R.string.settings_auto_away_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.autoAwaySavingError?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            }
            if (state.vhostOptions.isNotEmpty()) {
                item {
                    ResentinSectionCard(title = stringResource(R.string.settings_vhost_title)) {
                        Text(
                            stringResource(R.string.settings_vhost_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(state.vhostOptions, key = { it.address }) { option ->
                VhostRow(
                    option = option,
                    checked = option.address in state.vhostSelection,
                    onToggle = { viewModel.toggleVhostSelection(option.address) },
                )
            }
            item {
                state.vhostError?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                }
                ResentinSectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_push_enabled),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            )
                            Text(
                                stringResource(R.string.settings_push_enabled_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = pushEnabled, onCheckedChange = ::onPushEnabledChange)
                    }
                }
                state.pushError?.let { error ->
                    Spacer(Modifier.height(4.dp))
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                pushDecryptionFailureAt?.let { epochMillis ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.settings_push_decryption_failure, formatEpochMillis(epochMillis)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.pushSubscriptions.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.settings_push_devices),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            items(state.pushSubscriptions, key = { it.id }) { subscription ->
                PushSubscriptionRow(
                    subscription = subscription,
                    isThisDevice = subscription.id == state.ownPushSubscriptionId,
                    onRevoke = { viewModel.revokePushSubscription(subscription.id) },
                )
            }
            item {
                Text(
                    stringResource(R.string.settings_aliases),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            items(state.aliases.entries.toList(), key = { it.key }) { (name, expansion) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.bodyLarge)
                        Text(expansion, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { viewModel.removeAlias(name) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.cd_remove))
                    }
                }
            }
            item {
                ResentinSectionCard {
                    OutlinedTextField(
                        value = state.newAliasName,
                        onValueChange = viewModel::onNewAliasNameChange,
                        label = { Text(stringResource(R.string.settings_alias_name_label)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.newAliasExpansion,
                        onValueChange = viewModel::onNewAliasExpansionChange,
                        label = { Text(stringResource(R.string.settings_alias_expansion_label)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = viewModel::addAlias,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.settings_add_alias))
                    }
                    state.error?.let { error ->
                        Spacer(Modifier.height(8.dp))
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            item {
                ResentinSectionCard(title = stringResource(R.string.settings_storage_title)) {
                    OutlinedButton(
                        onClick = { showClearMessagesConfirm = true },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.settings_clear_messages, formatByteSize(messageDbSizeBytes)))
                    }
                    if (state.isAdmin) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onAdminClick,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.settings_admin_panel))
                        }
                    }
                }
            }
        }
    }

    if (showClearMessagesConfirm) {
        AlertDialog(
            onDismissRequest = { showClearMessagesConfirm = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            title = {
                Text(
                    stringResource(R.string.settings_clear_messages_confirm_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = { Text(stringResource(R.string.settings_clear_messages_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearMessageDatabase()
                        showClearMessagesConfirm = false
                    },
                ) {
                    Text(stringResource(R.string.settings_clear_messages_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearMessagesConfirm = false }) {
                    Text(stringResource(R.string.home_dialog_cancel))
                }
            },
        )
    }
}

@Composable
private fun ResentinSectionCard(
    title: String? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            title?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
            }
            content()
        }
    }
}

@Composable
private fun PushSubscriptionRow(
    subscription: PushSubscriptionSummaryDto,
    isThisDevice: Boolean,
    onRevoke: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                subscription.userAgent?.takeIf { it.isNotBlank() }
                    ?: stringResource(if (subscription.provider == "unifiedpush") R.string.settings_push_device_unknown else R.string.settings_push_device_browser),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                if (isThisDevice) {
                    stringResource(R.string.settings_push_this_device)
                } else {
                    stringResource(R.string.settings_push_last_used, formatIsoTimestamp(subscription.lastUsedAt))
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        IconButton(onClick = onRevoke) {
            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.settings_push_revoke))
        }
    }
}

@Composable
private fun VhostRow(option: VhostOptionDto, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(option.name, style = MaterialTheme.typography.bodyLarge)
            if (option.name != option.address) {
                Text(option.address, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private val SUBSCRIPTION_TIMESTAMP_FORMATTER =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())

private fun formatIsoTimestamp(iso: String?): String {
    if (iso == null) return "—"
    return runCatching { SUBSCRIPTION_TIMESTAMP_FORMATTER.format(Instant.parse(iso)) }.getOrDefault(iso)
}

private fun formatEpochMillis(epochMillis: Long): String =
    SUBSCRIPTION_TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(epochMillis))

private val BYTE_UNITS = listOf("B", "KB", "MB", "GB")

private fun formatByteSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < BYTE_UNITS.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return if (unitIndex == 0) {
        "$bytes ${BYTE_UNITS[0]}"
    } else {
        "%.1f %s".format(java.util.Locale.US, value, BYTE_UNITS[unitIndex])
    }
}
