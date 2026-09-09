package pm.antani.resentin.domain.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pm.antani.resentin.net.dto.IgnoreRequestDto
import pm.antani.resentin.net.rest.IgnoresApi

/** Masks in [masks] whose nick part matches [nick] — the client-side mirror of the
 * server's casemapped glob match, good enough to decide "is this nick covered".
 * A `*!*@*`-style mask covers nobody attributable, so it never matches here. */
fun coveringMasks(masks: List<String>, nick: String): List<String> =
    masks.filter { mask ->
        val nickPart = mask.substringBefore('!')
        nickPart != "*" && nickPart.equals(nick, ignoreCase = true)
    }

/** Server /ignore list (#162) per network slug. Mutations answer the resulting
 * list, which replaces the cache outright — the live session re-syncs itself
 * server-side, this client just mirrors the reply. */
class IgnoresRepository(
    private val authRepository: AuthRepository,
) {
    private val _ignores = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val ignores: StateFlow<Map<String, List<String>>> = _ignores.asStateFlow()

    suspend fun refresh(networkSlug: String): Result<List<String>> = runCatching {
        authRepository.api(IgnoresApi::class.java).getIgnores(networkSlug).masks
    }.onSuccess { _ignores.value = _ignores.value + (networkSlug to it) }

    suspend fun addIgnore(networkSlug: String, nick: String): Result<List<String>> = runCatching {
        authRepository.api(IgnoresApi::class.java).addIgnore(networkSlug, IgnoreRequestDto(nick)).masks
    }.onSuccess { _ignores.value = _ignores.value + (networkSlug to it) }

    suspend fun removeIgnore(networkSlug: String, mask: String): Result<List<String>> = runCatching {
        authRepository.api(IgnoresApi::class.java).removeIgnore(networkSlug, mask).masks
    }.onSuccess { _ignores.value = _ignores.value + (networkSlug to it) }

    /** Masks covering [nick] right now (empty = not ignored). */
    fun covering(networkSlug: String, nick: String): List<String> =
        coveringMasks(_ignores.value[networkSlug].orEmpty(), nick)
}
