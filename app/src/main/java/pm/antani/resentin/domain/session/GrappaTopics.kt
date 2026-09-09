package pm.antani.resentin.domain.session

import pm.antani.resentin.irc.canonicalTarget

fun userTopic(subject: String): String = "grappa:user:${subject}"

fun networkTopic(subject: String, networkSlug: String): String =
    "${userTopic(subject)}/network:${networkSlug}"

fun channelTopic(subject: String, networkSlug: String, channelName: String): String =
    "${networkTopic(subject, networkSlug)}/channel:${canonicalTarget(channelName)}"
