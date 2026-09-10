package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable

@Serializable
data class NotifyRequestDto(val nicks: List<String>)