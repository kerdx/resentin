package pm.antani.resentin.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pm.antani.resentin.R
import pm.antani.resentin.data.db.ChannelEntity
import pm.antani.resentin.data.db.NetworkWithChannels
import pm.antani.resentin.data.prefs.AppPreferences
import pm.antani.resentin.data.prefs.channelKey
import pm.antani.resentin.domain.repository.AuthRepository
import pm.antani.resentin.domain.repository.ChatRepository
import pm.antani.resentin.domain.repository.MembersRepository
import pm.antani.resentin.domain.repository.NetworksRepository
import pm.antani.resentin.domain.repository.UserSettingsRepository

class HomeViewModel(
    private val networksRepository: NetworksRepository,
    private val chatRepository: ChatRepository,
    private val membersRepository: MembersRepository,
    private val authRepository: AuthRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val appPreferences: AppPreferences,
    private val subject: String,
    val isVisitor: Boolean,
    private val context: Context,
) : ViewModel() {

    // Pinned chats first per network, then (optionally) most-unread first — stable
    // sorts keep the server order everywhere else.
    val networks: StateFlow<List<NetworkWithChannels>> = combine(
        networksRepository.networksWithChannels,
        appPreferences.pinnedChannels,
        appPreferences.unreadFirst,
    ) { list, pinned, unreadFirst ->
        list.map { nwc ->
            nwc.copy(
                channels = nwc.channels.sortedWith(
                    compareByDescending<ChannelEntity> { channel ->
                        channelKey(nwc.network.slug, channel.name) in pinned
                    }.thenByDescending { channel ->
                        if (unreadFirst) channel.unreadMessages else 0
                    },
                ),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val draftChannels: StateFlow<Set<String>> = appPreferences.chatDrafts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val pinnedChannels: StateFlow<Set<String>> = appPreferences.pinnedChannels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Server mute keys (muted_targets) — unexpired only, so the mute icon never
     * outlives a snooze the server already dropped. */
    val mutedChannels: StateFlow<Set<String>> = userSettingsRepository.notificationPrefs
        .map { prefs ->
            val nowSec = System.currentTimeMillis() / 1000
            prefs?.mutedTargets
                ?.filterValues { it.until == null || it.until > nowSec }
                ?.keys
                .orEmpty()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Emits (networkSlug, targetNick) once a "message privately" from the new-chat
     * dialog has actually opened the query window server-side — the screen navigates
     * to it only on success, mirroring [pm.antani.resentin.ui.common.UserCardController]. */
    private val _navigateToChat = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 1)
    val navigateToChat: SharedFlow<Pair<String, String>> = _navigateToChat.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            networksRepository.refresh()
                .onSuccess { _error.value = null }
                .onFailure { _error.value = it.message ?: context.getString(R.string.home_unknown_error) }
            _isRefreshing.value = false
        }
    }

    /** Long-press action: marks [channel] fully read without opening it. */
    fun markRead(networkSlug: String, channel: ChannelEntity) {
        viewModelScope.launch {
            chatRepository.markAllRead(networkSlug, channel.name)
                .onFailure { _error.value = it.message ?: context.getString(R.string.home_unknown_error) }
        }
    }

    /** Long-press action: leaves [channel] — a real IRC PART for a joined channel, or
     * just closing the local DM window for a query (PART-ing a nick makes no sense). */
    fun leaveChannel(networkSlug: String, channel: ChannelEntity) {
        viewModelScope.launch {
            val result = if (channel.source == "query") {
                runCatching {
                    val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                    membersRepository.closeQueryWindow(subject, networkId, channel.name)
                }
            } else {
                networksRepository.partChannel(networkSlug, channel.name)
            }
            result.onFailure { _error.value = it.message ?: context.getString(R.string.home_unknown_error) }
        }
    }

    /** "+" dialog action: JOINs [name] on [networkSlug]. */
    fun joinChannel(networkSlug: String, name: String) {
        viewModelScope.launch {
            networksRepository.joinChannel(networkSlug, name)
                .onFailure { _error.value = it.message ?: context.getString(R.string.home_unknown_error) }
        }
    }

    /** "+" dialog action: opens a DM with [nick] on [networkSlug], then signals
     * [navigateToChat] so the caller can push the chat screen. */
    fun startDirectMessage(networkSlug: String, nick: String) {
        viewModelScope.launch {
            runCatching {
                val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                membersRepository.openQueryWindow(subject, networkId, nick)
            }.onSuccess { _navigateToChat.tryEmit(networkSlug to nick) }
                .onFailure { _error.value = it.message ?: context.getString(R.string.home_unknown_error) }
        }
    }

    /** "Detach" (cicchetto parity) — sign-out for a visitor (whose own detach is
     * already a full teardown server-side) and one of the two choices offered to a
     * registered user. See AuthRepository.detach. */
    fun detach() {
        viewModelScope.launch { authRepository.detach() }
    }

    /** "Quit" (cicchetto parity, user-only in the UI) — parks every joined network
     * (server disconnects the upstream IRC connection) before detaching, composed
     * client-side per grappa-irc's own contract: `DELETE /auth/logout` alone never
     * tears a persistent identity's IRC session down, only an ephemeral visitor's. */
    fun quit() {
        viewModelScope.launch {
            runCatching {
                networks.value.forEach { nwc -> networksRepository.updateConnectionState(nwc.network.slug, connected = false) }
            }
            authRepository.detach()
        }
    }

    companion object {
        fun factory(
            networksRepository: NetworksRepository,
            chatRepository: ChatRepository,
            membersRepository: MembersRepository,
            authRepository: AuthRepository,
            userSettingsRepository: UserSettingsRepository,
            appPreferences: AppPreferences,
            subject: String,
            isVisitor: Boolean,
            context: Context,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    @Suppress("UNCHECKED_CAST")
                    return HomeViewModel(
                        networksRepository,
                        chatRepository,
                        membersRepository,
                        authRepository,
                        userSettingsRepository,
                        appPreferences,
                        subject,
                        isVisitor,
                        context.applicationContext,
                    ) as T
                }
            }
    }
}
