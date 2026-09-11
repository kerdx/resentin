package pm.antani.resentin.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.StringRes
import kotlinx.coroutines.CoroutineScope
import pm.antani.resentin.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import pm.antani.resentin.data.db.MemberEntity
import pm.antani.resentin.domain.repository.AuthRepository
import pm.antani.resentin.domain.repository.IgnoresRepository
import pm.antani.resentin.domain.repository.MembersRepository
import pm.antani.resentin.domain.repository.NetworksRepository
import pm.antani.resentin.domain.repository.coveringMasks
import pm.antani.resentin.net.AppJson
import pm.antani.resentin.net.dto.WhoisBundleDto

/** Standard IRC channel-privilege sigil hierarchy, highest first: ~ owner, & admin
 * (protect), @ op, % half-op, + voice. Only shown for letters the network's own
 * ISUPPORT PREFIX actually advertises — not every ircd has owner/admin. */
@Suppress("unused")
data class PrivilegeMode(val letter: Char, @StringRes val labelRes: Int)

val PRIVILEGE_MODES: List<PrivilegeMode> = listOf(
    PrivilegeMode('q', R.string.irc_role_owner),
    PrivilegeMode('a', R.string.irc_role_protect),
    PrivilegeMode('o', R.string.irc_role_operator),
    PrivilegeMode('h', R.string.irc_role_halfop),
    PrivilegeMode('v', R.string.irc_role_voice),
)

private val PRIVILEGE_SIGILS = mapOf('q' to '~', 'a' to '&', 'o' to '@', 'h' to '%', 'v' to '+')

fun sigilOfMode(letter: Char): Char? = PRIVILEGE_SIGILS[letter]

fun sigilsOf(member: MemberEntity): String = runCatching {
    AppJson.decodeFromString(ListSerializer(String.serializer()), member.modesJson).joinToString("")
}.getOrDefault("")

/** Whether [sigils] (as returned by [sigilsOf]) grants channel-privileged actions —
 * gates the op/deop/halfop/etc. toggle buttons in the user card. */
fun isPrivileged(sigils: String): Boolean = sigils.any { it in setOf('~', '&', '@') }

/**
 * Shared whois/kick/ban/privilege-toggle/contact-privately logic behind the user card.
 * Used identically from the member list and from long-pressing a chat message — both
 * are already scoped to a single (network, channel), so this centralizes the behavior
 * instead of duplicating it across MembersViewModel and ChatViewModel.
 */
class UserCardController(
    private val membersRepository: MembersRepository,
    private val networksRepository: NetworksRepository,
    private val ignoresRepository: IgnoresRepository,
    private val authRepository: AuthRepository,
    private val networkSlug: String,
    private val channelName: String,
    private val username: String,
    private val subject: String,
    private val scope: CoroutineScope,
) {
    val members: StateFlow<List<MemberEntity>> = membersRepository.observeMembers(networkSlug, channelName)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val ownSigils: StateFlow<String> = members
        .map { list -> list.find { it.nick.equals(username, ignoreCase = true) }?.let(::sigilsOf) ?: "" }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), "")

    /** Mode letter -> sigil this network's ircd actually supports, e.g. `{"o":"@","v":"+"}`. */
    val privilegeModes: StateFlow<Map<String, String>> = membersRepository.observePrefixModes(networkSlug)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _selectedWhois = MutableStateFlow<WhoisBundleDto?>(null)
    val selectedWhois: StateFlow<WhoisBundleDto?> = _selectedWhois.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _navigateToQuery = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navigateToQuery: SharedFlow<String> = _navigateToQuery.asSharedFlow()

    /** Server /ignore masks for this network — refreshed whenever a card opens, so
     * the ignore toggle below never renders a stale membership. */
    val ignoredMasks: StateFlow<List<String>> = ignoresRepository.ignores
        .map { it[networkSlug].orEmpty() }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Reactive "is this nick covered" for the card's ignore toggle. */
    fun isIgnored(nick: String): Flow<Boolean> =
        ignoredMasks.map { coveringMasks(it, nick).isNotEmpty() }

    /** Avatar URL for the open card: the bundle's seed, patched live by avatar
     * events. Null with no card open (dismiss clears it via selectedWhois). */
    private val _avatarUrl = MutableStateFlow<String?>(null)
    val avatarUrl: StateFlow<String?> = _avatarUrl.asStateFlow()

    private val _avatarBitmap = MutableStateFlow<Bitmap?>(null)
    val avatarBitmap: StateFlow<Bitmap?> = _avatarBitmap.asStateFlow()

    // WHOIS replies are broadcast to every screen-level controller because the
    // repository owns one WebSocket event stream. Keep the requested nick here so a
    // reply opened from the members screen cannot also resurrect the chat's card.
    private val _pendingWhoisTarget = MutableStateFlow<String?>(null)

    private suspend fun fetchAvatar(url: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            authRepository.fetchBytes(url)?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }.getOrNull()
    }

    /** Self-WHOIS never carries an avatar: `Grappa.Avatars`/the CTCP AVATAR dance is a
     * PEER-only mechanism (you never send yourself a CTCP query), so the server's
     * `whois_bundle.avatar_url` for your own nick is always null. Fall back to the
     * network's own avatar (already fetched for the network-settings profile editor)
     * when the WHOIS target matches the current live nick. */
    private suspend fun ownAvatarUrlIfSelf(target: String): String? {
        val network = networksRepository.observeNetwork(networkSlug).first() ?: return null
        return network.avatarUrl.takeIf { target.equals(network.nick, ignoreCase = true) }
    }

    init {
        membersRepository.whoisEvents
            .filter { it.target.equals(_pendingWhoisTarget.value, ignoreCase = true) }
            .onEach { bundle ->
                _pendingWhoisTarget.value = null
                _selectedWhois.value = bundle
                _avatarUrl.value = bundle.avatarUrl ?: ownAvatarUrlIfSelf(bundle.target)
            }.launchIn(scope)
        // Late avatar patch for the open card (M3b) — replaces the URL and kicks the
        // fetch below via avatarUrl; ignored when the card moved on to another nick.
        membersRepository.avatarEvents.onEach { event ->
            if (event.nick.equals(_selectedWhois.value?.target, ignoreCase = true) && event.avatarUrl != null) {
                _selectedWhois.value = _selectedWhois.value?.copy(avatarUrl = event.avatarUrl)
                _avatarUrl.value = event.avatarUrl
            }
        }.launchIn(scope)
        avatarUrl.onEach { url ->
            _avatarBitmap.value = null
            if (url != null) {
                val bitmap = fetchAvatar(url)
                // Drop stale results: the card may have closed or moved on mid-fetch.
                if (_avatarUrl.value == url && bitmap != null) {
                    _avatarBitmap.value = bitmap
                }
            }
        }.launchIn(scope)
    }

    fun sigilsFor(nick: String): String =
        members.value.find { it.nick.equals(nick, ignoreCase = true) }?.let(::sigilsOf) ?: ""

    fun onNickClick(nick: String) {
        scope.launch {
            _pendingWhoisTarget.value = nick
            runCatching { ignoresRepository.refresh(networkSlug) }
            runCatching {
                val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                membersRepository.requestWhois(subject, networkId, nick)
            }.onFailure { _error.value = it.message }
        }
    }

    fun dismissWhois() {
        _pendingWhoisTarget.value = null
        _selectedWhois.value = null
        _avatarUrl.value = null
    }

    fun kick(nick: String) = runVerb { networkId -> membersRepository.kick(subject, networkId, channelName, nick) }

    fun ban(nick: String) =
        runVerb { networkId -> membersRepository.ban(subject, networkId, channelName, "$nick!*@*") }

    /** Server /ignore toggle — personal, needs no channel privilege. The server
     * normalises a bare nick to `nick!*@*`; unignoring drops every mask covering
     * the nick (usually exactly that one). */
    fun ignore(nick: String) {
        scope.launch {
            runCatching { ignoresRepository.addIgnore(networkSlug, nick) }
                .onFailure { _error.value = it.message }
        }
    }

    fun unignore(nick: String) {
        scope.launch {
            runCatching {
                ignoresRepository.covering(networkSlug, nick).forEach { mask ->
                    ignoresRepository.removeIgnore(networkSlug, mask).getOrThrow()
                }
            }.onFailure { _error.value = it.message }
        }
    }

    fun contactPrivately(nick: String) {
        scope.launch {
            runCatching {
                val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                membersRepository.openQueryWindow(subject, networkId, nick)
            }.onSuccess { _navigateToQuery.tryEmit(nick) }
                .onFailure { _error.value = it.message }
        }
    }

    /** [letter] is the privilege mode letter (q/a/o/h/v); [grant] true to add it, false
     * to remove it. Uses the dedicated op/deop/voice/devoice verbs where they exist,
     * the generic raw-MODE verb otherwise (halfop/owner/admin have none). */
    fun setMode(nick: String, letter: Char, grant: Boolean) = runVerb { networkId ->
        when (letter) {
            'o' -> if (grant) {
                membersRepository.op(subject, networkId, channelName, nick)
            } else {
                membersRepository.deop(subject, networkId, channelName, nick)
            }
            'v' -> if (grant) {
                membersRepository.voice(subject, networkId, channelName, nick)
            } else {
                membersRepository.devoice(subject, networkId, channelName, nick)
            }
            else -> membersRepository.setMode(
                subject,
                networkId,
                channelName,
                "${if (grant) "+" else "-"}$letter",
                listOf(nick),
            )
        }
    }

    private fun runVerb(action: suspend (networkId: Int) -> Unit) {
        scope.launch {
            runCatching {
                val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                action(networkId)
            }.onFailure { _error.value = it.message }
        }
    }
}
