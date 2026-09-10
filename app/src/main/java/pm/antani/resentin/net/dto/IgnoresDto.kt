package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable

/** Server /ignore mask list (#162). GET answers `{masks: [...]}`; POST and DELETE
 * answer the same shape plus the touched mask and outcome (ignored on decode —
 * ignoreUnknownKeys covers them). */
@Serializable
data class IgnoresDto(
    val masks: List<String> = emptyList(),
)

@Serializable
data class IgnoreRequestDto(
    val mask: String,
)
