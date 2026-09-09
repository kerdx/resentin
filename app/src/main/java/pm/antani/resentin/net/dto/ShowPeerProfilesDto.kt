package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable

/** Account-wide preference controlling server-side peer profile/avatar discovery. */
@Serializable
data class ShowPeerProfilesDto(
    val showPeerProfiles: Boolean = false,
)
