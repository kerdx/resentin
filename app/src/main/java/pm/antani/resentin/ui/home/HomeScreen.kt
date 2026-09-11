package pm.antani.resentin.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pm.antani.resentin.R
import pm.antani.resentin.data.db.ChannelEntity
import pm.antani.resentin.data.db.NetworkEntity
import pm.antani.resentin.data.prefs.channelKey
import pm.antani.resentin.domain.repository.serverChannelKey
import pm.antani.resentin.ui.common.MircText
import pm.antani.resentin.ui.common.LocalDensityScale
import pm.antani.resentin.ui.common.rememberAvatarBitmap
import pm.antani.resentin.ui.common.ResentinDropdownMenu
import pm.antani.resentin.ui.common.ResentinDropdownMenuItem
import pm.antani.resentin.ui.common.ResentinHeaderAction
import pm.antani.resentin.ui.common.ResentinEmptyState
import pm.antani.resentin.ui.common.ResentinLoadingState

private data class ChannelActionsTarget(val networkSlug: String, val channel: ChannelEntity)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    host: String,
    onChannelClick: (networkSlug: String, channelName: String) -> Unit,
    onNetworkSettingsClick: (networkSlug: String) -> Unit,
    onAppSettingsClick: () -> Unit,
    onBrowseDirectory: (networkSlug: String) -> Unit = {},
) {
    // "$server" (MOTD + service notices) is reached by tapping the network header
    // itself rather than listed as a channel row — it's network-level, not a channel.
    val onServerClick: (String) -> Unit = { networkSlug -> onChannelClick(networkSlug, "\$server") }

    val networks by viewModel.networks.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val error by viewModel.error.collectAsState()
    val draftChannels by viewModel.draftChannels.collectAsState()
    val pinnedChannels by viewModel.pinnedChannels.collectAsState()
    val mutedChannels by viewModel.mutedChannels.collectAsState()
    // NavHost removes Home from the composition while a chat is open. Keep the same
    // scroll position when it comes back instead of rebuilding from the top.
    val homeListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    // Pin is local-only (slash key), mute is the server muted_targets map (space key)
    // — resolved here so ChannelRow stays a dumb renderer.
    val pinMutedOf: (networkSlug: String, channel: ChannelEntity) -> Pair<Boolean, Boolean> =
        { networkSlug, channel ->
            (channelKey(networkSlug, channel.name) in pinnedChannels) to
                (serverChannelKey(networkSlug, channel.name) in mutedChannels)
        }

    var actionsTarget by remember { mutableStateOf<ChannelActionsTarget?>(null) }
    var leaveConfirmTarget by remember { mutableStateOf<ChannelActionsTarget?>(null) }
    var newChatNetwork by remember { mutableStateOf<String?>(null) }
    // A visitor's own "detach" is already a full teardown server-side (see
    // AuthRepository.detach), so the sign-out icon skips straight to it; a
    // registered user gets the cicchetto-parity detach/quit choice.
    var showSignOutChoice by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.navigateToChat.collect { (networkSlug, nick) -> onChannelClick(networkSlug, nick) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF4E342E),
                        ) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(
                                    id = pm.antani.resentin.R.drawable.ic_launcher_foreground,
                                ),
                                contentDescription = null,
                                modifier = Modifier.padding(5.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Resentin",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                ),
                            ) {
                                Text(
                                    text = host,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                },
                actions = {
                    ResentinHeaderAction(
                        onClick = viewModel::refresh,
                        icon = Icons.Outlined.Refresh,
                        contentDescription = stringResource(R.string.cd_refresh),
                        enabled = !isRefreshing,
                        loading = isRefreshing,
                    )
                    ResentinHeaderAction(
                        onClick = onAppSettingsClick,
                        icon = Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.cd_app_settings),
                    )
                    ResentinHeaderAction(
                        onClick = { if (viewModel.isVisitor) viewModel.detach() else showSignOutChoice = true },
                        icon = Icons.AutoMirrored.Outlined.ExitToApp,
                        contentDescription = stringResource(R.string.cd_sign_out),
                    )
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                isRefreshing && networks.isEmpty() -> {
                    ResentinLoadingState(
                        title = stringResource(R.string.home_connection_loading_title),
                        description = stringResource(R.string.home_connection_loading_description),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                networks.isEmpty() && error != null -> {
                    ResentinEmptyState(
                        icon = Icons.Outlined.WifiOff,
                        title = stringResource(R.string.home_connection_error_title),
                        description = stringResource(R.string.home_connection_error_description),
                        actionLabel = stringResource(R.string.home_connection_retry),
                        onAction = viewModel::refresh,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                networks.isEmpty() -> {
                    ResentinEmptyState(
                        icon = Icons.Outlined.Public,
                        title = stringResource(R.string.home_no_networks_title),
                        description = stringResource(R.string.home_no_networks_description),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                else -> {
                    LazyColumn(
                        state = homeListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp * LocalDensityScale.current),
                    ) {
                        item(key = "home-summary") {
                            HomeSectionHeader(networkCount = networks.size)
                        }
                        items(
                            networks,
                            key = { it.network.slug },
                        ) { networkWithChannels ->
                            NetworkGroupCard(
                                network = networkWithChannels.network,
                                channels = networkWithChannels.channels
                                    .filter { it.source != "server" }
                                    .sortedBy { it.source == "query" },
                                pinMutedOf = pinMutedOf,
                                draftChannels = draftChannels,
                                fetchAvatarBytes = viewModel::fetchAvatarBytes,
                                onServerClick = { onServerClick(networkWithChannels.network.slug) },
                                onNetworkSettingsClick = { onNetworkSettingsClick(networkWithChannels.network.slug) },
                                onAddClick = { newChatNetwork = networkWithChannels.network.slug },
                                onBrowseDirectory = { onBrowseDirectory(networkWithChannels.network.slug) },
                                onChannelClick = { channel ->
                                    onChannelClick(networkWithChannels.network.slug, channel.name)
                                },
                                onChannelLongClick = { channel ->
                                    actionsTarget = ChannelActionsTarget(networkWithChannels.network.slug, channel)
                                },
                            )
                        }
                    }
                }
            }
            if (error != null && networks.isNotEmpty()) {
                val message = error!!
                Snackbar(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                    Text(message)
                }
            }
        }
    }

    actionsTarget?.let { target ->
        ChannelActionsSheet(
            target = target,
            onDismiss = { actionsTarget = null },
            onMarkRead = {
                viewModel.markRead(target.networkSlug, target.channel)
                actionsTarget = null
            },
            onLeave = {
                actionsTarget = null
                leaveConfirmTarget = target
            },
        )
    }

    leaveConfirmTarget?.let { target ->
        val isQuery = target.channel.source == "query"
        AlertDialog(
            onDismissRequest = { leaveConfirmTarget = null },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            title = {
                Text(
                    stringResource(if (isQuery) R.string.home_action_close_query else R.string.home_action_leave_channel),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Text(
                    stringResource(
                        if (isQuery) R.string.home_close_query_confirm else R.string.home_leave_channel_confirm,
                        target.channel.name,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.leaveChannel(target.networkSlug, target.channel)
                        leaveConfirmTarget = null
                    },
                ) {
                    Text(
                        stringResource(if (isQuery) R.string.home_action_close_query else R.string.home_action_leave_channel),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { leaveConfirmTarget = null }) {
                    Text(stringResource(R.string.home_dialog_cancel))
                }
            },
        )
    }

    if (showSignOutChoice) {
        AlertDialog(
            onDismissRequest = { showSignOutChoice = false },
            title = { Text(stringResource(R.string.home_sign_out_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.home_sign_out_detach_hint), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            showSignOutChoice = false
                            viewModel.detach()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.home_sign_out_detach))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.home_sign_out_quit_hint), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            showSignOutChoice = false
                            viewModel.quit()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.home_sign_out_quit))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSignOutChoice = false }) {
                    Text(stringResource(R.string.home_dialog_cancel))
                }
            },
        )
    }

    newChatNetwork?.let { networkSlug ->
        NewChatDialog(
            onDismiss = { newChatNetwork = null },
            onJoin = { name ->
                viewModel.joinChannel(networkSlug, name)
                newChatNetwork = null
            },
            onMessage = { nick ->
                viewModel.startDirectMessage(networkSlug, nick)
                newChatNetwork = null
            },
            onBrowseDirectory = {
                newChatNetwork = null
                onBrowseDirectory(networkSlug)
            },
        )
    }
}

@Composable
private fun NewChatDialog(
    onDismiss: () -> Unit,
    onJoin: (String) -> Unit,
    onMessage: (String) -> Unit,
    onBrowseDirectory: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        title = {
            Text(
                stringResource(R.string.home_new_chat_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(R.string.home_new_chat_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.OutlinedButton(
                    onClick = onBrowseDirectory,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Outlined.Public,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.home_new_chat_browse))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onJoin(text.trim()) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.home_new_chat_join))
            }
        },
        dismissButton = {
            TextButton(onClick = { onMessage(text.trim()) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.home_new_chat_dm))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelActionsSheet(
    target: ChannelActionsTarget,
    onDismiss: () -> Unit,
    onMarkRead: () -> Unit,
    onLeave: () -> Unit,
) {
    val isQuery = target.channel.source == "query"
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        ) {
            Text(
                text = target.channel.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            val subtitle = if (isQuery) {
                stringResource(R.string.channel_private_conversation)
            } else {
                target.channel.topic?.takeIf { it.isNotBlank() }
            }
            subtitle?.let {
                MircText(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            if (target.channel.unreadMessages > 0) {
                SheetActionRow(
                    icon = Icons.Outlined.Done,
                    iconContainer = MaterialTheme.colorScheme.primaryContainer,
                    iconContent = MaterialTheme.colorScheme.onPrimaryContainer,
                    text = stringResource(R.string.home_action_mark_read),
                    textColor = MaterialTheme.colorScheme.onSurface,
                    onClick = onMarkRead,
                )
            }
            SheetActionRow(
                icon = Icons.Outlined.Delete,
                iconContainer = MaterialTheme.colorScheme.errorContainer,
                iconContent = MaterialTheme.colorScheme.onErrorContainer,
                text = stringResource(
                    if (isQuery) R.string.home_action_close_query else R.string.home_action_leave_channel,
                ),
                textColor = MaterialTheme.colorScheme.error,
                onClick = onLeave,
            )
        }
    }
}

@Composable
private fun SheetActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconContainer: Color,
    iconContent: Color,
    text: String,
    textColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(14.dp),
            color = iconContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = iconContent,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = textColor,
        )
    }
}

@Composable
private fun HomeSectionHeader(networkCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.home_networks_title).uppercase(),
            style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 0.8.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text(
            pluralStringResource(R.plurals.home_network_count, networkCount, networkCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NetworkGroupCard(
    network: NetworkEntity,
    channels: List<ChannelEntity>,
    pinMutedOf: (networkSlug: String, channel: ChannelEntity) -> Pair<Boolean, Boolean>,
    draftChannels: Set<String>,
    fetchAvatarBytes: suspend (String) -> ByteArray?,
    onServerClick: () -> Unit,
    onNetworkSettingsClick: () -> Unit,
    onAddClick: () -> Unit,
    onBrowseDirectory: () -> Unit,
    onChannelClick: (ChannelEntity) -> Unit,
    onChannelLongClick: (ChannelEntity) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            NetworkHeader(
                network = network,
                channelCount = channels.size,
                fetchAvatarBytes = fetchAvatarBytes,
                onClick = onServerClick,
                onSettingsClick = onNetworkSettingsClick,
                onAddClick = onAddClick,
                onBrowseDirectory = onBrowseDirectory,
            )
            if (channels.isEmpty()) {
                ResentinEmptyState(
                    icon = Icons.Outlined.Tag,
                    title = stringResource(R.string.home_network_empty_title),
                    description = stringResource(R.string.home_network_empty_description),
                    actionLabel = stringResource(R.string.home_new_chat_browse),
                    onAction = onBrowseDirectory,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                channels.forEachIndexed { index, channel ->
                if (index == 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
                val (pinned, muted) = pinMutedOf(network.slug, channel)
                val hasDraft = channelKey(network.slug, channel.name) in draftChannels
                ChannelRow(
                    channel = channel,
                    pinned = pinned,
                    muted = muted,
                    hasDraft = hasDraft,
                    fetchAvatarBytes = fetchAvatarBytes,
                    onClick = { onChannelClick(channel) },
                    onLongClick = { onChannelLongClick(channel) },
                )
                if (index < channels.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 62.dp, end = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                }
                }
            }
        }
    }
}

@Composable
private fun networkAvatarColor(): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) {
        scheme.primaryContainer to scheme.onPrimaryContainer
    }
}

@Composable
private fun NetworkHeader(
    network: NetworkEntity,
    channelCount: Int,
    fetchAvatarBytes: suspend (String) -> ByteArray?,
    onClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAddClick: () -> Unit,
    onBrowseDirectory: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val densityScale = LocalDensityScale.current
    val (avatarContainer, avatarContent) = networkAvatarColor()
    val avatarBitmap = rememberAvatarBitmap(network.avatarUrl, fetchAvatarBytes)
    val stateLabel = if (network.connectionState == "connected") {
        stringResource(R.string.network_settings_connected)
    } else {
        network.connectionState
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp * densityScale, bottom = 8.dp * densityScale),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(16.dp),
            color = avatarContainer,
        ) {
            if (avatarBitmap != null) {
                Image(
                    bitmap = avatarBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = network.slug.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = avatarContent,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = network.slug,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ConnectionStateDot(network.connectionState)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${network.nick} • $stateLabel • $channelCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.cd_network_settings))
            }
            ResentinDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                ResentinDropdownMenuItem(
                    text = stringResource(R.string.cd_new_chat),
                    icon = Icons.Outlined.Add,
                    onClick = {
                        showMenu = false
                        onAddClick()
                    },
                )
                ResentinDropdownMenuItem(
                    text = stringResource(R.string.home_new_chat_browse),
                    icon = Icons.Outlined.Public,
                    onClick = {
                        showMenu = false
                        onBrowseDirectory()
                    },
                )
                ResentinDropdownMenuItem(
                    text = stringResource(R.string.cd_network_settings),
                    icon = Icons.Outlined.Settings,
                    onClick = {
                        showMenu = false
                        onSettingsClick()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelRow(
    channel: ChannelEntity,
    pinned: Boolean,
    muted: Boolean,
    hasDraft: Boolean,
    fetchAvatarBytes: suspend (String) -> ByteArray?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val hasUnread = channel.unreadMessages > 0
    val isQuery = channel.source == "query"
    val avatarBitmap = rememberAvatarBitmap(channel.avatarUrl.takeIf { isQuery }, fetchAvatarBytes)
    val topic = channel.topic?.takeIf { it.isNotBlank() }
    // Resentin: nasconde il segnaposto rumoroso "nessun topic" — titolo su una
    // sola riga centrato verticalmente quando non c'è altro da mostrare.
    val showSubtitle = hasDraft || topic != null || isQuery

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 12.dp, end = 12.dp, top = 9.dp * LocalDensityScale.current, bottom = 9.dp * LocalDensityScale.current),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(14.dp),
            color = if (isQuery) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                if (isQuery && avatarBitmap != null) {
                    Image(
                        bitmap = avatarBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (isQuery) {
                    Text(
                        text = channel.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Tag,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showSubtitle) {
                if (hasDraft) {
                    Text(
                        text = stringResource(R.string.cd_chat_draft),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    MircText(
                        text = if (isQuery) {
                            stringResource(R.string.channel_private_conversation)
                        } else {
                            topic.orEmpty()
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        enableLinks = false,
                    )
                }
            }
        }
        if (hasUnread) {
            Spacer(Modifier.width(8.dp))
            UnreadBadge(count = channel.unreadMessages)
        }
        if (hasDraft || pinned || muted) {
            Spacer(Modifier.width(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasDraft) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.cd_chat_draft),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    )
                }
                if (pinned) {
                    if (hasDraft) Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Outlined.PushPin,
                        contentDescription = stringResource(R.string.channel_settings_pin),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                if (muted) {
                    if (hasDraft || pinned) Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Outlined.NotificationsOff,
                        contentDescription = stringResource(R.string.channel_settings_mute),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

@Composable
private fun UnreadBadge(count: Int) {
    Box(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun ConnectionStateDot(connectionState: String) {
    val color = when (connectionState) {
        "connected" -> Color(0xFF4CAF50)
        "failed" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(color, CircleShape),
    )
}
