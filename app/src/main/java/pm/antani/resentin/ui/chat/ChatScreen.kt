package pm.antani.resentin.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import pm.antani.resentin.R
import pm.antani.resentin.data.db.MemberEntity
import pm.antani.resentin.data.db.MessageEntity
import pm.antani.resentin.data.prefs.ChatDisplayMode
import pm.antani.resentin.irc.FormattedEvent
import pm.antani.resentin.irc.SystemEventFormatter
import pm.antani.resentin.irc.containsMention
import pm.antani.resentin.irc.highestSigil
import pm.antani.resentin.net.AppJson
import pm.antani.resentin.ui.common.MircText
import pm.antani.resentin.ui.common.UserCardSheet
import pm.antani.resentin.ui.common.colorForNick
import pm.antani.resentin.ui.common.mircAnnotatedString
import pm.antani.resentin.ui.common.sigilsOf
import pm.antani.resentin.ui.common.withClickableLinks

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
private val TIME_FORMATTER_WITH_SECONDS = DateTimeFormatter.ofPattern("HH:mm:ss")

private fun formatTime(epochMillis: Long, showSeconds: Boolean): String {
    val formatter = if (showSeconds) TIME_FORMATTER_WITH_SECONDS else TIME_FORMATTER
    return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(formatter)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    title: String,
    networkSlug: String,
    viewerUsername: String,
    isQuery: Boolean = false,
    onBack: () -> Unit,
    onMembersClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAppSettings: () -> Unit = {},
    onOpenQuery: (networkSlug: String, nick: String) -> Unit,
    onOpenChannel: (networkSlug: String, channelName: String) -> Unit,
) {
    val messages by viewModel.messages.collectAsState()
    val topic by viewModel.topic.collectAsState()
    val channelModes by viewModel.channelModes.collectAsState()
    val draft by viewModel.draft.collectAsState()
    // Local TextFieldValue (not just the String from the ViewModel) so an externally
    // triggered draft change — a swipe-to-reply prefill, or send() clearing it — can
    // explicitly place the cursor, instead of leaning on Compose's default "diff the new
    // text against whatever selection happened to be there" behavior, which does NOT
    // reliably land the cursor at the end (the reported bug: reply prefilled the text but
    // the caret stayed wherever it last was, not focused, not at the end).
    var draftFieldValue by remember { mutableStateOf(TextFieldValue(draft)) }
    val draftFocusRequester = remember { FocusRequester() }
    LaunchedEffect(draft) {
        if (draftFieldValue.text != draft) {
            draftFieldValue = TextFieldValue(text = draft, selection = TextRange(draft.length))
        }
    }
    LaunchedEffect(Unit) {
        viewModel.replyFocusRequests.collect { draftFocusRequester.requestFocus() }
    }
    val error by viewModel.error.collectAsState()
    val whois by viewModel.selectedWhois.collectAsState()
    val ownSigils by viewModel.ownSigils.collectAsState()
    val privilegeModes by viewModel.privilegeModes.collectAsState()
    val initialReadCursor by viewModel.initialReadCursor.collectAsState()
    val initialReadCursorReady by viewModel.initialReadCursorReady.collectAsState()
    val members by viewModel.members.collectAsState()
    val activeMention = remember(draftFieldValue) { mentionQueryAtCursor(draftFieldValue) }
    val mentionSuggestions = remember(activeMention, members) {
        activeMention?.let { findMentionSuggestions(it.query, members) }.orEmpty()
    }
    val slashSuggestions = remember(draftFieldValue.text) {
        suggestSlashCommands(draftFieldValue.text)
    }
    val availableChannels by viewModel.availableChannels.collectAsState()
    val availableNetworks by viewModel.availableNetworks.collectAsState()
    val slashArgumentSuggestions = remember(draftFieldValue.text, members, availableChannels, availableNetworks) {
        suggestSlashArguments(
            draftFieldValue.text,
            members.map { it.nick },
            availableChannels,
            availableNetworks,
        )
    }

    fun completeMention(nick: String) {
        val mention = activeMention ?: return
        val before = draftFieldValue.text.substring(0, mention.start)
        val after = draftFieldValue.text.substring(mention.end)
        val inserted = nick + if (after.isEmpty() || !after.first().isWhitespace()) " " else ""
        val newText = before + inserted + after
        val newCursor = before.length + inserted.length
        val newValue = TextFieldValue(newText, TextRange(newCursor))
        draftFieldValue = newValue
        viewModel.onDraftChange(newText)
        draftFocusRequester.requestFocus()
    }

    fun completeSlashCommand(command: SlashCommandSpec) {
        val completion = completeSlashCommandInput(draftFieldValue.text, command)
        val newValue = TextFieldValue(completion.text, TextRange(completion.cursor))
        draftFieldValue = newValue
        viewModel.onDraftChange(completion.text)
        draftFocusRequester.requestFocus()
    }
    fun completeSlashArgument(suggestion: SlashArgumentSuggestion) {
        val completion = completeSlashArgumentInput(draftFieldValue.text, suggestion)
        val newValue = TextFieldValue(completion.text, TextRange(completion.cursor))
        draftFieldValue = newValue
        viewModel.onDraftChange(completion.text)
        draftFocusRequester.requestFocus()
    }
    val displayMode by viewModel.chatDisplayMode.collectAsState()
    val showSeconds by viewModel.showSeconds.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val coloredNicklist by viewModel.coloredNicklist.collectAsState()
    val showHostmaskInEvents by viewModel.showHostmaskInEvents.collectAsState()
    val myNick by viewModel.myNick.collectAsState()
    val listState = rememberLazyListState()
    var hasScrolledInitially by remember { mutableStateOf(false) }
    var showTopicDialog by remember { mutableStateOf(false) }
    // The long-pressed row's plain text, threaded to UserCardSheet for its "Copia"/
    // "Copia parziale" actions — the sheet itself only knows the sender's nick (it opens
    // off a WHOIS reply, which arrives async), not which message triggered it.
    var longPressedMessageText by remember { mutableStateOf<String?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::uploadFile)
    }

    LaunchedEffect(Unit) {
        viewModel.navigateToQuery.collect { nick -> onOpenQuery(networkSlug, nick) }
    }

    LaunchedEffect(Unit) {
        viewModel.commandEffects.collect { effect ->
            when (effect) {
                is ChatCommandEffect.OpenChannel -> onOpenChannel(networkSlug, effect.channelName)
                ChatCommandEffect.CloseChat -> onBack()
                ChatCommandEffect.OpenChannelSettings -> onSettingsClick()
                ChatCommandEffect.OpenAppSettings -> onAppSettings()
            }
        }
    }

    // Position (within `messages`) of the first message past where the reader left off —
    // also where the "Hai letto fino a qui" divider renders. Derived from
    // initialReadCursor, which is frozen for this screen's whole lifetime (see
    // ChatViewModel), so the divider stays put even as newly-arrived messages get
    // marked read live: it marks where you left off, not a constantly-advancing cursor.
    // Null when there's nothing to mark (cursor still loading, never read anything, or
    // everything is already read).
    val dividerIndex = initialReadCursor?.let { cursor ->
        messages.indexOfFirst { it.id > cursor }.takeIf { it >= 0 }
    }

    // Land on the first unread message (per the server's read-cursor), not always the
    // bottom — only once, and only once we actually know where that is (see
    // ChatViewModel.initialReadCursorReady: null is ambiguous between "not loaded yet"
    // and "never read anything").
    LaunchedEffect(messages, initialReadCursorReady) {
        if (hasScrolledInitially || !initialReadCursorReady || messages.isEmpty()) return@LaunchedEffect
        listState.scrollToItem(dividerIndex ?: (messages.size - 1))
        hasScrolledInitially = true
    }

    // Whether the tail of the list is already on screen — read BEFORE a new message's
    // recomposition lands (LazyColumn hasn't re-laid-out to include it yet), so this is
    // "was the reader already following the bottom" at the moment the new item arrives,
    // not a stale flag that needs separate resetting. A 1-item tolerance covers the
    // divider's own row without needing to special-case it here.
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            lastVisible.index >= layoutInfo.totalItemsCount - 2
        }
    }

    // Opening the IME reduces the list viewport, but it does not change the
    // messages list, so the regular new-message auto-follow effect below is
    // not triggered. Capture whether the user was following the tail when the
    // composer received focus and restore that position after the IME resizes
    // the layout. If the user was reading history, keep their position.
    var shouldScrollToBottomOnIme by remember { mutableStateOf(false) }
    val imeInsets = WindowInsets.ime
    val density = LocalDensity.current
    LaunchedEffect(listState) {
        snapshotFlow {
            val lastIndex = messages.size - 1 + if (dividerIndex != null) 1 else 0
            Triple(
                imeInsets.getBottom(density),
                shouldScrollToBottomOnIme && hasScrolledInitially,
                lastIndex,
            )
        }.collectLatest { (imeBottom, shouldFollow, lastIndex) ->
            if (imeBottom <= 0 || !shouldFollow || lastIndex < 0) return@collectLatest
            // IME insets animate over multiple frames. Keep the tail aligned
            // after each inset change, once the current layout has measured.
            withFrameNanos { }
            listState.scrollToItem(lastIndex)
        }
    }

    // Only auto-follow to the tail when the reader was already there — landing on the
    // unread divider (potentially far above the bottom, e.g. after a big backfill) must
    // NOT get yanked down the instant one more message arrives; a still-unread backlog
    // stays exactly where the reader put it. loadOlder() prepends at the head, which
    // doesn't change `lastOrNull()?.id`, so this never fires for that case either.
    LaunchedEffect(messages.lastOrNull()?.id) {
        if (hasScrolledInitially && messages.isNotEmpty() && isAtBottom) {
            val lastIndex = messages.size - 1 + if (dividerIndex != null) 1 else 0
            listState.animateScrollToItem(lastIndex)
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index -> if (index == 0 && messages.isNotEmpty()) viewModel.loadOlder() }
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
                        Column(
                            modifier = Modifier.clickable(enabled = topic != null) { showTopicDialog = true },
                    ) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            networkSlug,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // "(+rnt) topic text" — modes prefix the topic line the way a
                        // classic IRC client's status bar does, shown even without a
                        // topic set so the channel's mode flags stay visible either way.
                        val subtitle = buildString {
                            if (channelModes != null) append("($channelModes) ")
                            if (topic != null) append(topic)
                        }.takeIf { it.isNotBlank() }
                        if (subtitle != null) {
                            MircText(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !isRefreshing) {
                        if (isRefreshing) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cd_refresh))
                        }
                    }
                    if (isQuery) {
                        // In a query, `title` IS the partner's nick (see AppRoot's
                        // ChatScreen call site) — the same nick onMessageLongPress
                        // already knows how to resolve into a WHOIS lookup.
                        IconButton(onClick = { viewModel.onMessageLongPress(title) }) {
                            Icon(Icons.Default.Person, contentDescription = stringResource(R.string.cd_user_info))
                        }
                    } else {
                        IconButton(onClick = onMembersClick) {
                            BadgedBox(
                                badge = {
                                    Badge {
                                        Text(members.size.toString())
                                    }
                                },
                            ) {
                                Icon(
                                    Icons.Default.Group,
                                    contentDescription = pluralStringResource(
                                        R.plurals.cd_members_count,
                                        members.size,
                                        members.size,
                                    ),
                                )
                            }
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_channel_settings))
                        }
                    }
                },
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                if (slashSuggestions.isNotEmpty()) {
                    SlashCommandSuggestions(
                        suggestions = slashSuggestions,
                        onSelect = ::completeSlashCommand,
                    )
                }
                if (slashArgumentSuggestions.isNotEmpty()) {
                    SlashArgumentSuggestions(
                        suggestions = slashArgumentSuggestions,
                        onSelect = ::completeSlashArgument,
                    )
                }
                if (mentionSuggestions.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 3.dp,
                    ) {
                        LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                            mentionSuggestions.forEach { member ->
                                item(key = "mention-${member.nick}") {
                                    DropdownMenuItem(
                                        text = { Text(member.nick) },
                                        onClick = { completeMention(member.nick) },
                                    )
                                }
                            }
                        }
                    }
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 2.dp,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                IconButton(
                    onClick = { filePicker.launch("*/*") },
                    enabled = !isUploading,
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.AttachFile, contentDescription = stringResource(R.string.cd_attach_file))
                    }
                }
                OutlinedTextField(
                    value = draftFieldValue,
                    onValueChange = { newValue ->
                        draftFieldValue = newValue
                        viewModel.onDraftChange(newValue.text)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(draftFocusRequester)
                        .onFocusChanged { focusState ->
                            shouldScrollToBottomOnIme = if (focusState.isFocused) {
                                hasScrolledInitially && isAtBottom
                            } else {
                                false
                            }
                        },
                    placeholder = { Text(stringResource(R.string.chat_message_placeholder)) },
                    shape = RoundedCornerShape(20.dp),
                )
                IconButton(
                    onClick = viewModel::send,
                    enabled = !isSending,
                    modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.cd_send), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                messages.forEachIndexed { index, message ->
                    if (index == dividerIndex) {
                        item(key = "unread-divider") { UnreadDivider() }
                    }
                    item(key = message.id) {
                        MessageRow(
                            message = message,
                            members = members,
                            displayMode = displayMode,
                            showSeconds = showSeconds,
                            coloredNicklist = coloredNicklist,
                            showHostmaskInEvents = showHostmaskInEvents,
                            isMention = isMentionRow(message, myNick, isQuery),
                            isQuery = isQuery,
                            isMine = isQuery && (myNick ?: viewerUsername).equals(message.sender, ignoreCase = true),
                            onReply = viewModel::reply,
                            onLongPress = { nick, text ->
                                longPressedMessageText = text
                                viewModel.onMessageLongPress(nick)
                            },
                        )
                    }
                }
            }
            error?.let { message ->
                ChatErrorSnackbar(
                    message = message,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                )
            }
            if (hasScrolledInitially && messages.isNotEmpty() && !isAtBottom) {
                val scope = rememberCoroutineScope()
                SmallFloatingActionButton(
                    onClick = {
                        val lastIndex = messages.size - 1 + if (dividerIndex != null) 1 else 0
                        scope.launch { listState.animateScrollToItem(lastIndex) }
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.cd_scroll_to_bottom))
                }
            }
        }
    }

    val whoisValue = whois
    if (whoisValue != null) {
        val ignored by viewModel.isIgnored(whoisValue.target).collectAsState(initial = false)
        val avatar by viewModel.avatarBitmap.collectAsState()
        UserCardSheet(
            whois = whoisValue,
            viewerUsername = viewerUsername,
            ownSigils = ownSigils,
            targetSigils = viewModel.sigilsFor(whoisValue.target),
            availableModes = privilegeModes,
            messageText = longPressedMessageText,
            onDismiss = {
                viewModel.dismissWhois()
                longPressedMessageText = null
            },
            onContactPrivately = viewModel::contactPrivately,
            onKick = viewModel::kickFromCard,
            onBan = viewModel::banFromCard,
            onSetMode = viewModel::setModeFromCard,
            showChannelActions = !isQuery,
            isIgnored = ignored,
            onIgnore = viewModel::ignore,
            onUnignore = viewModel::unignore,
            avatarBitmap = avatar,
        )
    }

    if (showTopicDialog && topic != null) {
        AlertDialog(
            onDismissRequest = { showTopicDialog = false },
            title = { Text(stringResource(R.string.chat_topic_dialog_title)) },
            text = { MircText(text = topic!!, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { showTopicDialog = false }) {
                    Text(stringResource(R.string.chat_dialog_close))
                }
            },
        )
    }
}

@Composable
internal fun SlashCommandSuggestions(
    suggestions: List<SlashCommandSpec>,
    onSelect: (SlashCommandSpec) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("slash-command-suggestions"),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
            suggestions.forEach { command ->
                item(key = "slash-command-${command.name}") {
                    DropdownMenuItem(
                        modifier = Modifier.testTag("slash-command-${command.name}"),
                        text = {
                            Column {
                                Text("/${command.name}", fontWeight = FontWeight.Medium)
                                Text(
                                    stringResource(command.syntaxRes),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                Text(
                                    stringResource(command.descriptionRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = { onSelect(command) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun SlashArgumentSuggestions(
    suggestions: List<SlashArgumentSuggestion>,
    onSelect: (SlashArgumentSuggestion) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("slash-argument-suggestions"),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
            suggestions.forEach { suggestion ->
                item(key = "slash-argument-${suggestion.value}") {
                    DropdownMenuItem(
                        modifier = Modifier.testTag("slash-argument-${suggestion.value}"),
                        text = { Text(suggestion.label) },
                        onClick = { onSelect(suggestion) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun ChatErrorSnackbar(message: String, modifier: Modifier = Modifier) {
    Snackbar(modifier = modifier.testTag("chat-error-snackbar")) {
        Text(message)
    }
}

private data class MentionQuery(
    val start: Int,
    val end: Int,
    val query: String,
)

private fun mentionQueryAtCursor(value: TextFieldValue): MentionQuery? {
    if (!value.selection.collapsed) return null
    val cursor = value.selection.end
    if (cursor !in 0..value.text.length) return null
    val atIndex = value.text.lastIndexOf('@', startIndex = cursor - 1)
    if (atIndex < 0) return null
    if (atIndex > 0 && !value.text[atIndex - 1].isWhitespace()) return null
    val query = value.text.substring(atIndex + 1, cursor)
    if (query.any(Char::isWhitespace)) return null
    return MentionQuery(start = atIndex, end = cursor, query = query)
}

private fun findMentionSuggestions(query: String, members: List<MemberEntity>): List<MemberEntity> =
    members
        .asSequence()
        .filter { query.isBlank() || it.nick.contains(query, ignoreCase = true) }
        .distinctBy { it.nick.lowercase() }
        .sortedWith(compareBy<MemberEntity>({ !it.nick.startsWith(query, ignoreCase = true) }, { it.nick.lowercase() }))
        .take(8)
        .toList()

@Composable
private fun UnreadDivider() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
        Text(
            text = stringResource(R.string.chat_unread_divider),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
    }
}

/** The nick's highest-priority role sigil (~&@%+), or "" if they hold none / aren't a
 * known member (e.g. a query partner, who never appears in a channel's member list). */
private fun nickPrefixFor(nick: String, members: List<MemberEntity>): String {
    val member = members.find { it.nick.equals(nick, ignoreCase = true) } ?: return ""
    return highestSigil(sigilsOf(member))?.toString().orEmpty()
}

/** A low-alpha tint of the theme's `tertiary` accent, laid OVER whatever background is
 * already there rather than replacing it — using the opaque `tertiaryContainer` role
 * instead (the first attempt) paired badly with the existing text colors on a dark
 * theme (nick colors, mIRC colors, plain body text all assume a dark background; some
 * dynamic-color palettes resolve `tertiaryContainer` to a *light* tone even in dark
 * mode, which then read as low-contrast-to-illegible against them). A translucent wash
 * over the correct background can't produce that mismatch, on either theme. */
@Composable
private fun mentionHighlight(isMention: Boolean): Modifier =
    if (isMention) Modifier.background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.22f)) else Modifier

/** Whether [message] is one that would have fired a notification — see
 * NotificationRouter.shouldNotify, which this deliberately mirrors (own messages never
 * count; a DM is skipped here rather than mirrored, since every message in an open DM
 * would "mention" you and highlighting all of them would just be noise, not a signal). */
private fun isMentionRow(message: MessageEntity, myNick: String?, isQuery: Boolean): Boolean {
    if (myNick == null || isQuery) return false
    if (message.sender.equals(myNick, ignoreCase = true)) return false
    return message.body?.let { containsMention(it, myNick) } ?: false
}

@Composable
private fun reasonSuffix(reason: String?): String =
    if (reason == null) "" else stringResource(R.string.paren_suffix, reason)

/** The sender nick prefixed with its `[ident@host]` mask when [showHostmask] is on and
 * the server actually captured one (join/part/quit only — see [FormattedEvent.System]'s
 * `userHost`, absent for kick/mode/nick_change). */
private fun actorLabel(sender: String, userHost: String?, showHostmask: Boolean): String =
    if (showHostmask && userHost != null) "$sender [$userHost]" else sender

/** Renders a structured [FormattedEvent.System] into its localized display line — the
 * templates themselves live in strings.xml so this varies by locale. */
@Composable
private fun systemEventText(event: FormattedEvent.System, showHostmask: Boolean): String = when (event) {
    is FormattedEvent.System.Join ->
        stringResource(R.string.event_join, actorLabel(event.sender, event.userHost, showHostmask))
    is FormattedEvent.System.Part ->
        stringResource(
            R.string.event_part,
            actorLabel(event.sender, event.userHost, showHostmask),
            reasonSuffix(event.reason),
        )
    is FormattedEvent.System.Quit ->
        stringResource(
            R.string.event_quit,
            actorLabel(event.sender, event.userHost, showHostmask),
            reasonSuffix(event.reason),
        )
    is FormattedEvent.System.Kick ->
        stringResource(R.string.event_kick, event.sender, event.target, reasonSuffix(event.reason))
    is FormattedEvent.System.Mode ->
        stringResource(R.string.event_mode, event.sender, event.modes, event.args?.let { " $it" }.orEmpty())
    is FormattedEvent.System.NickChange -> stringResource(R.string.event_nick_change, event.sender, event.newNick)
    is FormattedEvent.System.TopicChanged -> stringResource(R.string.event_topic_changed, event.sender)
}

@Composable
private fun MessageRow(
    message: MessageEntity,
    members: List<MemberEntity>,
    displayMode: ChatDisplayMode,
    showSeconds: Boolean,
    coloredNicklist: Boolean,
    showHostmaskInEvents: Boolean,
    isMention: Boolean,
    isQuery: Boolean,
    isMine: Boolean,
    onReply: (nick: String, body: String) -> Unit,
    onLongPress: (nick: String, text: String) -> Unit,
) {
    val meta = remember(message.metaJson) {
        runCatching { AppJson.parseToJsonElement(message.metaJson).jsonObject }
            .getOrDefault(JsonObject(emptyMap()))
    }
    val formatted = remember(message.kind, message.sender, message.body, meta) {
        SystemEventFormatter.format(message.kind, message.sender, message.body, meta)
    }
    val time = remember(message.serverTime, showSeconds) { formatTime(message.serverTime, showSeconds) }
    val prefix = remember(message.sender, members) { nickPrefixFor(message.sender, members) }

    when (formatted) {
        is FormattedEvent.System -> {
            val eventText = systemEventText(formatted, showHostmaskInEvents)
            MircText(
                text = "$eventText · $time",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .pointerInput(formatted.sender, eventText) {
                        detectTapGestures(onLongPress = { onLongPress(formatted.sender, eventText) })
                    },
            )
        }
        is FormattedEvent.Chat -> {
            SwipeToReply(
                onReply = { onReply(message.sender, formatted.text) },
                onLongPress = { onLongPress(message.sender, formatted.text) },
            ) {
                if (displayMode == ChatDisplayMode.IRC_LINE) {
                    IrcLineRow(message, formatted, prefix, time, coloredNicklist, isMention)
                } else {
                    BubbleRow(
                        message, formatted, prefix, time, coloredNicklist, isMention,
                        isPrivate = isQuery,
                        isMine = isMine,
                    )
                }
            }
        }
    }
}

/** Nick color (when [coloredNicklist] is on) applies to the bare nick only — the role
 * prefix sigil (`@`/`+`/...) and any "* "/"< >"/"(notice)" decoration around it stay
 * the surrounding text's own color, matching how real IRC clients color-code nicks. */
private fun buildNickLine(
    before: String,
    prefix: String,
    sender: String,
    after: String,
    body: String,
    coloredNicklist: Boolean,
) = buildAnnotatedString {
    append(before)
    append(prefix)
    if (coloredNicklist) {
        withStyle(SpanStyle(color = colorForNick(sender))) { append(sender) }
    } else {
        append(sender)
    }
    append(after)
    append(withClickableLinks(mircAnnotatedString(body)))
}

@Composable
private fun BubbleRow(
    message: MessageEntity,
    formatted: FormattedEvent.Chat,
    prefix: String,
    time: String,
    coloredNicklist: Boolean,
    isMention: Boolean,
    isPrivate: Boolean,
    isMine: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(mentionHighlight(isMention))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        // Query messages use the same left-aligned conversation flow as IRC chat.
        // The sender is still differentiated by the bubble tint below, not by
        // switching sides of the conversation.
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(18.dp),
            color = if (isPrivate && isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                if (formatted.isAction) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        val annotated = remember(prefix, message.sender, formatted.text, coloredNicklist) {
                            buildNickLine("* ", prefix, message.sender, " ", formatted.text, coloredNicklist)
                        }
                        Text(
                            text = annotated,
                            style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = time,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (formatted.isNotice) {
                                prefix + message.sender + " (notice)"
                            } else {
                                prefix + message.sender
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (coloredNicklist) colorForNick(message.sender) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = time,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MircText(text = formatted.text, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/** `[HH:mm] <nick> message`, classic IRC-client style. Role prefix (if any) sits right
 * inside the angle brackets — `<@nick>` — matching how real IRC clients render it. */
@Composable
private fun IrcLineRow(
    message: MessageEntity,
    formatted: FormattedEvent.Chat,
    prefix: String,
    time: String,
    coloredNicklist: Boolean,
    isMention: Boolean,
) {
    val annotated = remember(message.sender, formatted.text, formatted.isAction, formatted.isNotice, prefix, time, coloredNicklist) {
        when {
            formatted.isAction -> buildNickLine("[$time] * ", prefix, message.sender, " ", formatted.text, coloredNicklist)
            formatted.isNotice -> buildNickLine("[$time] -", prefix, message.sender, "- ", formatted.text, coloredNicklist)
            else -> buildNickLine("[$time] <", prefix, message.sender, "> ", formatted.text, coloredNicklist)
        }
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier
            .fillMaxWidth()
            .then(mentionHighlight(isMention))
            .padding(horizontal = 16.dp, vertical = 2.dp),
    )
}

private const val REPLY_SWIPE_THRESHOLD_DP = 64

/** Swipe-right-to-reply, WhatsApp/Telegram style: drag reveals a reply icon behind the
 * row and, past the threshold, prefills the draft with `nick: ` on release. IRC has no
 * real threaded replies, so this only ever affects the compose box, never the message.
 * A separate long-press gesture opens the sender's user card — the drag detector only
 * consumes events once the finger has actually moved, so the two coexist on one row. */
@Composable
private fun SwipeToReply(onReply: () -> Unit, onLongPress: () -> Unit, content: @Composable () -> Unit) {
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val thresholdPx = with(LocalDensity.current) { REPLY_SWIPE_THRESHOLD_DP.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (offsetX.value > thresholdPx) onReply()
                            offsetX.animateTo(0f, animationSpec = spring())
                        }
                    },
                    onDragCancel = { scope.launch { offsetX.animateTo(0f, animationSpec = spring()) } },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch { offsetX.snapTo((offsetX.value + dragAmount).coerceIn(0f, thresholdPx * 1.5f)) }
                    },
                )
            }
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) },
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = stringResource(R.string.cd_reply),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp)
                .graphicsLayer { alpha = (offsetX.value / thresholdPx).coerceIn(0f, 1f) },
        )
        Box(modifier = Modifier.offset { IntOffset(offsetX.value.roundToInt(), 0) }) {
            content()
        }
    }
}