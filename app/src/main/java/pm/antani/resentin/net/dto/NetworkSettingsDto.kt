package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable

@Serializable
data class IdentityUpdateDto(
    val nick: String? = null,
    val ident: String? = null,
    val realname: String? = null,
)

@Serializable
data class ConnectionStateUpdateDto(
    val connectionState: String,
    val reason: String? = null,
)

@Serializable
data class PerformDto(
    val performList: String? = null,
    val operPassSet: Boolean = false,
)

@Serializable
data class PerformUpdateDto(
    val performList: String? = null,
)

@Serializable
data class TopicUpdateDto(
    val body: String,
)

/** KVIrc-style CTCP USERINFO profile edit (`PATCH /networks/:slug/profile`). All 5
 * fields are always sent — a blank string explicitly clears that field server-side,
 * mirroring cicchetto's own editor (no partial-update distinction on this endpoint). */
@Serializable
data class ProfileUpdateDto(
    val age: String = "",
    val gender: String = "",
    val location: String = "",
    val languages: String = "",
    val custom: String = "",
)
