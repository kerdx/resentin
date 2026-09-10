package pm.antani.resentin.ui.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import pm.antani.resentin.R
import pm.antani.resentin.data.db.MessageEntity
import pm.antani.resentin.data.prefs.AppPreferences
import pm.antani.resentin.data.prefs.ChatDisplayMode
import pm.antani.resentin.domain.repository.AuthRepository
import pm.antani.resentin.domain.repository.ChatRepository
import pm.antani.resentin.domain.repository.IgnoresRepository
import pm.antani.resentin.domain.repository.MembersRepository
import pm.antani.resentin.domain.repository.NetworksRepository
import pm.antani.resentin.domain.repository.UserSettingsRepository
import pm.antani.resentin.domain.session.channelTopic
import pm.antani.resentin.domain.session.ConnectionManager
import pm.antani.resentin.domain.session.OpenChatTracker
import pm.antani.resentin.domain.session.PendingShareHolder
import pm.antani.resentin.irc.canonicalTarget
import pm.antani.resentin.irc.isMessageVisibleUnderPresenceFilter
import pm.antani.resentin.irc.isQueryTarget
import pm.antani.resentin.irc.presenceVisible
import pm.antani.resentin.ui.common.UserCardController

sealed interface ChatCommandEffect {
    data class OpenChannel(val channelName: String) : ChatCommandEffect
    data object CloseChat : ChatCommandEffect
    data object OpenChannelSettings : ChatCommandEffect
    data object OpenAppSettings : ChatCommandEffect
}
class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val networksRepository: NetworksRepository,
    private val membersRepository: MembersRepository,
    ignoresRepository: IgnoresRepository,
    private val authRepository: AuthRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val appPreferences: AppPreferences,
    private val connectionManager: ConnectionManager,
    private val openChatTracker: OpenChatTracker,
    private val pendingShareHolder: PendingShareHolder,
    private val appContext: Context,
    private val networkSlug: String,
    private val channelName: String,
    private val username: String,
    private val subject: String,
) : ViewModel() {

    val topic: StateFlow<String?> = networksRepository.observeChannel(networkSlug, channelName)
        .map { it?.topic?.takeIf { topic -> topic.isNotBlank() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val channelModes: StateFlow<String?> = networksRepository.observeChannel(networkSlug, channelName)
        .map { it?.modes }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // The viewer's own current nick on this network — same source NotificationRouter
    // reads to decide whether an incoming message deserves a notification, reused here
    // (via irc.containsMention) so a message highlighted as "mentions you" in chat can
    // never drift from one that actually fired a notification.
    val myNick: StateFlow<String?> = networksRepository.observeNetwork(networkSlug)
        .map { it?.nick }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val chatDisplayMode: StateFlow<ChatDisplayMode> = appPreferences.chatDisplayMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatDisplayMode.BUBBLES)

    val showSeconds: StateFlow<Boolean> = appPreferences.showSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val coloredNicklist: StateFlow<Boolean> = appPreferences.coloredNicklist
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val showHostmaskInEvents: StateFlow<Boolean> = appPreferences.showHostmaskInEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // Long-press-on-message user card: same whois/moderation logic as the member list,
    // scoped to this same (network, channel) — empty members/sigils for a query/$server,
    // which naturally hides the channel-only actions in the card.
    private val userCard =
        UserCardController(membersRepository, networksRepository, ignoresRepository, authRepository, networkSlug, channelName, username, subject, viewModelScope)
    val selectedWhois = userCard.selectedWhois
    val avatarBitmap = userCard.avatarBitmap
    val ownSigils = userCard.ownSigils
    val privilegeModes = userCard.privilegeModes
    val navigateToQuery = userCard.navigateToQuery
    // Exposed for the nick role-prefix (~&@%+) shown in both chat display modes.
    val members = userCard.members

    val availableChannels: StateFlow<List<String>> = networksRepository.networksWithChannels
        .map { networks -> networks.firstOrNull { it.network.slug.equals(networkSlug, ignoreCase = true) }?.channels.orEmpty().map { it.name }.filter { it.firstOrNull() in setOf('#', '&', '+', '!') }.distinct() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val availableNetworks: StateFlow<List<String>> = networksRepository.networksWithChannels
        .map { networks -> networks.map { it.network.slug }.distinct() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val channelPresenceVisible: StateFlow<Boolean> = combine(
        userSettingsRepository.presencePinFlowFor(networkSlug, channelName),
        members,
    ) { pin, currentMembers ->
        if (isQueryTarget(channelName)) true else presenceVisible(pin, currentMembers.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    // The server filters historical pages for a hidden presence pin. Apply the same
    // rule locally to live WS rows, which the server still delivers for membership
    // and window-state bookkeeping. Raw rows remain in Room.
    val messages: StateFlow<List<MessageEntity>> = combine(
        chatRepository.observeMessages(networkSlug, channelName),
        channelPresenceVisible,
        myNick,
    ) { rows, visible, ownNick ->
        rows.filter { row -> isMessageVisibleUnderPresenceFilter(row, visible, ownNick) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onMessageLongPress(nick: String) = userCard.onNickClick(nick)
    fun requestWhois(nick: String) = userCard.onNickClick(nick)
    fun dismissWhois() = userCard.dismissWhois()
    fun kickFromCard(nick: String) = userCard.kick(nick)
    fun banFromCard(nick: String) = userCard.ban(nick)
    fun contactPrivately(nick: String) = userCard.contactPrivately(nick)
    fun setModeFromCard(nick: String, letter: Char, grant: Boolean) = userCard.setMode(nick, letter, grant)
    fun sigilsFor(nick: String) = userCard.sigilsFor(nick)
    fun isIgnored(nick: String): Flow<Boolean> = userCard.isIgnored(nick)
    fun ignore(nick: String) = userCard.ignore(nick)
    fun unignore(nick: String) = userCard.unignore(nick)

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()
    private var draftChangedByUser = false
    private val draftWriteMutex = Mutex()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isLoadingOlder = MutableStateFlow(false)
    val isLoadingOlder: StateFlow<Boolean> = _isLoadingOlder.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Snapshot of "where the reader left off" BEFORE this session marks anything new as
    // read — the one-time scroll target. Null until known; ChatScreen should wait for a
    // non-null-or-otherwise-settled value before deciding where to land initially.
    private val _initialReadCursor = MutableStateFlow<Long?>(null)
    val initialReadCursor: StateFlow<Long?> = _initialReadCursor.asStateFlow()

    // Distinguishes "not known yet" from "known to be null" (never read) — the UI must
    // wait for this before deciding where to scroll, or it'll always land on the bottom.
    private val _initialReadCursorReady = MutableStateFlow(false)
    val initialReadCursorReady: StateFlow<Boolean> = _initialReadCursorReady.asStateFlow()

    private var lastMarkedRead = 0L

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    // One-shot signal for ChatScreen to grab keyboard focus (and move the cursor to the
    // end of the now-prefilled draft) right after a swipe-to-reply — reply() alone only
    // changes the draft's TEXT, which doesn't imply focus or cursor position on its own.
    private val _replyFocusRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val replyFocusRequests: SharedFlow<Unit> = _replyFocusRequests.asSharedFlow()

    private val _commandEffects = MutableSharedFlow<ChatCommandEffect>(extraBufferCapacity = 1)
    val commandEffects: SharedFlow<ChatCommandEffect> = _commandEffects.asSharedFlow()
    private val channelReady = CompletableDeferred<Unit>()

    init {
        openChatTracker.onChatOpened(networkSlug, channelName)
        // Arrived here via the Android share sheet (ShareTargetScreen staged the URIs
        // before navigating in) — upload each one now, exactly like tapping the attach
        // button once per file. Consumed once so re-entering this chat later doesn't
        // re-upload the same share.
        pendingShareHolder.consume().forEach { uploadFile(it) }
        viewModelScope.launch {
            userCard.error.collect { message -> message?.let { _error.value = it } }
        }
        viewModelScope.launch {
            val savedDraft = runCatching {
                appPreferences.getChatDraft(networkSlug, channelName)
            }.getOrDefault("")
            if (!draftChangedByUser) _draft.value = savedDraft
        }
        viewModelScope.launch {
            _initialReadCursor.value = networksRepository.getStoredReadCursor(networkSlug, channelName)
            runCatching {
                connectionManager.connect()
                connectionManager.joinChannel("grappa:user:$subject")
                connectionManager.joinChannel("grappa:user:$subject/network:$networkSlug")
                val channelTopic = channelTopic(subject, networkSlug, channelName)
                networksRepository.applyJoinResponse(channelTopic, connectionManager.joinChannel(channelTopic))
                channelReady.complete(Unit)
                // A join reply only happens once per process lifetime per topic (usually
                // AppContainer's app-wide join already consumed it before this screen
                // even opened, via the same applyJoinResponse path) — if we still have no
                // scroll target by then, fall back to whatever Room ended up with.
                if (_initialReadCursor.value == null) {
                    _initialReadCursor.value = networksRepository.getStoredReadCursor(networkSlug, channelName)
                }
            }.onFailure {
                if (!channelReady.isCompleted) channelReady.completeExceptionally(it)
                _error.value = it.message
            }
            // Only now, not before backfill() — while it's running, `messages` is a
            // partial, arbitrarily-ordered-by-arrival prefix of the true history (Room
            // emits on every individual upsert), so indexOfFirst{it.id > cursor} against
            // that partial snapshot can match a much-too-early row and then latch onto
            // it forever (ChatScreen sets hasScrolledInitially on its first pass once
            // this flips true). Waiting for backfill's suspend call to actually return
            // guarantees the full page is committed before that first pass runs.
            chatRepository.backfill(networkSlug, channelName).onFailure { _error.value = it.message }
            _initialReadCursorReady.value = true
        }
        // Marks the newest loaded message as read whenever it changes — the chat being
        // open (this ViewModel existing) is already the "the user is looking at this"
        // signal the rest of the app (OpenChatTracker) relies on.
        viewModelScope.launch {
            messages.collect { list -> list.maxByOrNull { it.id }?.let { markRead(it.id) } }
        }
    }

    private fun markRead(messageId: Long) {
        if (messageId <= lastMarkedRead) return
        lastMarkedRead = messageId
        viewModelScope.launch {
            chatRepository.markRead(networkSlug, channelName, messageId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        openChatTracker.onChatClosed(networkSlug, channelName)
    }

    fun onDraftChange(text: String) {
        setDraft(text)
    }

    private fun setDraft(text: String) {
        draftChangedByUser = true
        _draft.value = text
        viewModelScope.launch {
            draftWriteMutex.withLock {
                appPreferences.setChatDraft(networkSlug, channelName, text)
            }
        }
    }

    /** Swipe-to-reply: prefills the draft using the active reply-style template
     * (Settings) — plain `nick: ` by default, optionally a quoted preview of the
     * original message, or a fully custom template. IRC has no real threaded replies,
     * this is just the addressing/quoting convention most bouncers highlight on. */
    fun reply(nick: String, messageBody: String) {
        viewModelScope.launch {
            val style = appPreferences.replyStyle.first()
            val customTemplate = appPreferences.replyCustomTemplate.first()
            val prefix = buildReplyPrefix(style, customTemplate, nick, messageBody)
            if (!_draft.value.startsWith(prefix)) setDraft(prefix + _draft.value)
            _replyFocusRequests.tryEmit(Unit)
        }
    }

    fun send() {
        val rawText = _draft.value
        val text = rawText.trim()
        if (text.isBlank()) return
        if (!rawText.startsWith("/")) {
            sendMessage(text)
            return
        }
        if (_isSending.value) return
        _isSending.value = true
        viewModelScope.launch {
            try {
                val aliases = userSettingsRepository.getAliases().getOrDefault(emptyMap())
                val expanded = expandUserSlashAlias(rawText, aliases) ?: rawText
                when (val parsed = parseSlashCommand(expanded)) {
                    SlashCommandParseResult.NotACommand -> sendMessage(text)
                    SlashCommandParseResult.Incomplete -> showSlashError(R.string.chat_slash_command_incomplete)
                    is SlashCommandParseResult.Invalid -> showSlashError(
                        when (parsed.error) {
                            SlashCommandError.UnknownCommand -> R.string.chat_slash_command_unknown
                            SlashCommandError.MissingArgument -> R.string.chat_slash_command_missing_argument
                            SlashCommandError.InvalidArgument -> R.string.chat_slash_command_invalid_argument
                        },
                        "/${parsed.token}",
                    )
                    is SlashCommandParseResult.Parsed -> runCatching { executeSlashCommand(parsed.command) }
                        .onFailure { failure -> _error.value = failure.message ?: appContext.getString(R.string.chat_slash_command_unsupported, "/${parsed.command.name}") }
                }
            } finally {
                _isSending.value = false
            }
        }
    }

    private fun sendMessage(text: String) {
        // Set this synchronously before launching: a second tap can otherwise enqueue
        // another identical request while the first one awaits the network response.
        if (_isSending.value) return
        _isSending.value = true
        viewModelScope.launch {
            try {
                runCatching {
                    channelReady.await()
                    chatRepository.sendMessage(networkSlug, channelName, text).getOrThrow()
                }.onSuccess {
                    if (_draft.value.trim() == text) setDraft("")
                }.onFailure { _error.value = it.message }
            } finally {
                _isSending.value = false
            }
        }
    }

    private suspend fun executeSlashCommand(command: SlashCommand) {
        val args = command.arguments
        val argument = args.firstOrNull()
        when (command.name) {
            "me" -> {
                chatRepository.sendMessage(networkSlug, channelName, args.joinToString(" "), channelName).getOrThrow()
                setDraft("")
            }
            "join" -> {
                channelReady.await()
                networksRepository.joinChannel(networkSlug, requireNotNull(argument), args.getOrNull(1)).getOrThrow()
                setDraft("")
                _commandEffects.emit(ChatCommandEffect.OpenChannel(requireNotNull(argument)))
            }
            "part" -> {
                val target = argument?.takeIf(::isChannelName) ?: channelName
                val reason = if (argument != null && isChannelName(argument)) args.drop(1) else args
                networksRepository.partChannel(networkSlug, target, reason.joinToString(" ").ifBlank { null }).getOrThrow()
                setDraft("")
                if (canonicalTarget(target) == canonicalTarget(channelName)) _commandEffects.emit(ChatCommandEffect.CloseChat)
            }
            "cycle" -> {
                val target = argument?.takeIf(::isChannelName) ?: channelName
                val reason = if (argument != null && isChannelName(argument)) args.drop(1) else args
                networksRepository.partChannel(networkSlug, target, reason.joinToString(" ").ifBlank { null }).getOrThrow()
                networksRepository.joinChannel(networkSlug, target).getOrThrow()
                setDraft("")
                _commandEffects.emit(ChatCommandEffect.OpenChannel(target))
            }
            "topic" -> {
                check(!isQueryTarget(channelName)) { appContext.getString(R.string.chat_slash_channel_only) }
                networksRepository.updateTopic(networkSlug, channelName, if (argument == "-delete") "" else args.joinToString(" ")).getOrThrow()
                setDraft("")
            }
            "nick" -> {
                networksRepository.updateIdentity(networkSlug, requireNotNull(argument), null, null).getOrThrow()
                setDraft("")
            }
            "msg" -> {
                val target = requireNotNull(argument)
                val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                membersRepository.openQueryWindow(subject, networkId, target)
                chatRepository.sendMessage(networkSlug, target, args.drop(1).joinToString(" ")).getOrThrow()
                setDraft("")
                _commandEffects.emit(ChatCommandEffect.OpenChannel(target))
            }
            "query" -> {
                contactPrivately(requireNotNull(argument))
                setDraft("")
            }
            "whois" -> {
                requestWhois(requireNotNull(argument))
                setDraft("")
            }
            "names" -> {
                val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                membersRepository.requestNames(subject, networkId, requireNotNull(argument))
                setDraft("")
            }
            "op", "deop", "voice", "devoice" -> {
                val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                membersRepository.setNickModes(subject, networkId, channelName, command.name, args)
                setDraft("")
            }
            "kick" -> {
                val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                membersRepository.kick(subject, networkId, channelName, requireNotNull(argument), args.drop(1).joinToString(" ").ifBlank { null })
                setDraft("")
            }
            "ban" -> {
                val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                membersRepository.ban(subject, networkId, channelName, requireNotNull(argument))
                setDraft("")
            }
            "unban" -> {
                val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                membersRepository.unban(subject, networkId, channelName, requireNotNull(argument))
                setDraft("")
            }
            "banlist" -> {
                _commandEffects.emit(ChatCommandEffect.OpenChannelSettings)
                setDraft("")
            }
            "invite" -> {
                val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                val targetChannel = args.getOrNull(1)?.takeIf(::isChannelName) ?: channelName
                membersRepository.invite(subject, networkId, targetChannel, requireNotNull(argument))
                setDraft("")
            }
            "umode" -> {
                if (args.isEmpty()) {
                    showSlashError(R.string.chat_slash_command_unsupported, "/umode")
                } else {
                    val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                    membersRepository.setMode(subject, networkId, myNick.value ?: username, args.first(), args.drop(1))
                    setDraft("")
                }
            }
            "mode" -> {
                if (args.isEmpty()) {
                    _commandEffects.emit(ChatCommandEffect.OpenChannelSettings)
                } else {
                    val target = args.first().takeIf(::isChannelName) ?: channelName
                    val modeIndex = if (target == channelName) 0 else 1
                    val modes = args.getOrNull(modeIndex) ?: error(appContext.getString(R.string.chat_slash_command_missing_argument, "/mode"))
                    val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                    membersRepository.setMode(subject, networkId, target, modes, args.drop(modeIndex + 1))
                }
                setDraft("")
            }
            "notify" -> {
                networksRepository.addNotify(networkSlug, args)
                setDraft("")
            }
            "alias" -> {
                if (args.isEmpty()) {
                    _commandEffects.emit(ChatCommandEffect.OpenAppSettings)
                } else {
                    val name = requireNotNull(argument).lowercase()
                    check(slashCommandCatalog.none { it.name.equals(name, true) || it.aliases.any { alias -> alias.equals(name, true) } }) { appContext.getString(R.string.chat_slash_alias_builtin) }
                    val aliases = userSettingsRepository.getAliases().getOrThrow().toMutableMap()
                    aliases[name] = args.drop(1).joinToString(" ")
                    userSettingsRepository.updateAliases(aliases).getOrThrow()
                    setDraft("")
                }
            }
            "unalias" -> {
                val aliases = userSettingsRepository.getAliases().getOrThrow().toMutableMap()
                aliases.remove(requireNotNull(argument).lowercase())
                userSettingsRepository.updateAliases(aliases).getOrThrow()
                setDraft("")
            }
            "connect", "disconnect", "reconnect" -> {
                val (targetNetwork, reason) = resolveNetworkAndReason(args, command.name == "connect")
                when (command.name) {
                    "connect" -> networksRepository.updateConnectionState(targetNetwork, true, reason.ifBlank { null }).getOrThrow()
                    "disconnect" -> networksRepository.updateConnectionState(targetNetwork, false, reason.ifBlank { null }).getOrThrow()
                    else -> {
                        networksRepository.updateConnectionState(targetNetwork, false, reason.ifBlank { null }).getOrThrow()
                        networksRepository.updateConnectionState(targetNetwork, true, reason.ifBlank { null }).getOrThrow()
                    }
                }
                if (reason.isNotBlank()) Unit
                setDraft("")
            }
            "quit" -> {
                val reason = args.joinToString(" ").ifBlank { null }
                networksRepository.networksWithChannels.first().forEach { networksRepository.updateConnectionState(it.network.slug, false, reason).getOrThrow() }
                authRepository.detach()
                setDraft("")
            }
            else -> showSlashError(R.string.chat_slash_command_unsupported, "/${command.name}")
        }
    }

    private fun isChannelName(value: String): Boolean = value.firstOrNull() in setOf('#', '&', '+', '!')

    private fun resolveNetworkAndReason(args: List<String>, networkRequired: Boolean): Pair<String, String> {
        val candidate = args.firstOrNull()
        val isNetwork = candidate != null && availableNetworks.value.any { it.equals(candidate, ignoreCase = true) }
        if (networkRequired && !isNetwork) error(appContext.getString(R.string.chat_slash_command_invalid_argument, "/connect"))
        return if (isNetwork) candidate!! to args.drop(1).joinToString(" ") else networkSlug to args.joinToString(" ")
    }

    private fun showSlashError(messageRes: Int, argument: String? = null) {
        _error.value = if (argument == null) {
            appContext.getString(messageRes)
        } else {
            appContext.getString(messageRes, argument)
        }
    }

    fun uploadFile(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                channelReady.await()
                _isUploading.value = true
                val pending = readUploadFile(appContext, uri)
                    ?: error(appContext.getString(R.string.chat_upload_failed))
                chatRepository.uploadAndSend(networkSlug, channelName, pending.bytes, pending.fileName, pending.mimeType)
                    .getOrThrow()
            }.onFailure { _error.value = it.message }
            _isUploading.value = false
        }
    }

    /** Manual "reload the buffer" — the only recourse when a backfill silently fell
     * behind (a gap wider than [pm.antani.resentin.domain.repository.ChatRepository]'s
     * drain cap) and there's no other trigger to try again from. */
    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            chatRepository.backfill(networkSlug, channelName).onFailure { _error.value = it.message }
            _isRefreshing.value = false
        }
    }

    fun loadOlder() {
        if (_isLoadingOlder.value) return
        viewModelScope.launch {
            _isLoadingOlder.value = true
            chatRepository.loadOlder(networkSlug, channelName)
                .onFailure { _error.value = it.message }
            _isLoadingOlder.value = false
        }
    }

    companion object {
        fun factory(
            chatRepository: ChatRepository,
            networksRepository: NetworksRepository,
            membersRepository: MembersRepository,
            ignoresRepository: IgnoresRepository,
            authRepository: AuthRepository,
            userSettingsRepository: UserSettingsRepository,
            appPreferences: AppPreferences,
            connectionManager: ConnectionManager,
            openChatTracker: OpenChatTracker,
            pendingShareHolder: PendingShareHolder,
            appContext: Context,
            networkSlug: String,
            channelName: String,
            username: String,
            subject: String,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                @Suppress("UNCHECKED_CAST")
                return ChatViewModel(
                    chatRepository,
                    networksRepository,
                    membersRepository,
                    ignoresRepository,
                    authRepository,
                    userSettingsRepository,
                    appPreferences,
                    connectionManager,
                    openChatTracker,
                    pendingShareHolder,
                    appContext,
                    networkSlug,
                    channelName,
                    username,
                    subject,
                ) as T
            }
        }
    }
}
