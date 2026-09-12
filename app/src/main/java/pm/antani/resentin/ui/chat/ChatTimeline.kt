package pm.antani.resentin.ui.chat

import pm.antani.resentin.data.db.MessageEntity

sealed interface ChatTimelineRow {
    val messages: List<MessageEntity>
    val key: String

    data class Message(val message: MessageEntity) : ChatTimelineRow {
        override val messages: List<MessageEntity> = listOf(message)
        override val key: String = "message-${message.id}"
    }

    data class PresenceBurst(override val messages: List<MessageEntity>) : ChatTimelineRow {
        override val key: String = "presence-${messages.first().id}"
        val joins: Int = messages.count { it.kind == "join" }
        val leaves: Int = messages.count { it.kind == "part" || it.kind == "quit" }
    }
}

/** Collapse rapid join/part/quit flapping for one nick into a single timeline row.
 * Original messages remain in Room and are available by expanding the summary. */
fun buildChatTimeline(
    messages: List<MessageEntity>,
    readCursor: Long?,
    maxGapMs: Long = PRESENCE_BURST_MAX_GAP_MS,
    maxSpanMs: Long = PRESENCE_BURST_MAX_SPAN_MS,
): List<ChatTimelineRow> {
    val rows = mutableListOf<ChatTimelineRow>()
    var index = 0

    while (index < messages.size) {
        val first = messages[index]
        if (!first.isPresenceTransition()) {
            rows += ChatTimelineRow.Message(first)
            index++
            continue
        }

        val burst = mutableListOf(first)
        var nextIndex = index + 1
        while (nextIndex < messages.size) {
            val previous = burst.last()
            val candidate = messages[nextIndex]
            val gap = candidate.serverTime - previous.serverTime
            val span = candidate.serverTime - first.serverTime
            val crossesReadCursor = readCursor != null && (candidate.id > readCursor) != (first.id > readCursor)
            if (!candidate.isPresenceTransition() ||
                !candidate.sender.equals(first.sender, ignoreCase = true) ||
                gap !in 0..maxGapMs || span !in 0..maxSpanMs ||
                crossesReadCursor
            ) {
                break
            }
            burst += candidate
            nextIndex++
        }

        val hasJoin = burst.any { it.kind == "join" }
        val hasLeave = burst.any { it.kind == "part" || it.kind == "quit" }
        if (burst.size >= MIN_PRESENCE_BURST_SIZE && hasJoin && hasLeave) {
            rows += ChatTimelineRow.PresenceBurst(burst)
        } else {
            burst.forEach { rows += ChatTimelineRow.Message(it) }
        }
        index = nextIndex
    }

    return rows
}

private fun MessageEntity.isPresenceTransition(): Boolean =
    kind == "join" || kind == "part" || kind == "quit"

private const val MIN_PRESENCE_BURST_SIZE = 3
private const val PRESENCE_BURST_MAX_GAP_MS = 30_000L
private const val PRESENCE_BURST_MAX_SPAN_MS = 120_000L
