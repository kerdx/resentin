package pm.antani.resentin.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(channels: List<ChannelEntity>)

    /** REST refreshes (GET .../channels, query_windows_list) carry membership only —
     * no topic/modes/cursors/badges. Plain REPLACE would wipe those live WS-fed fields
     * on every refresh, so membership sync goes through here instead: insert truly new
     * rows, touch only source/joined on the ones already known. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(channels: List<ChannelEntity>)

    @Query("UPDATE channels SET source = :source, joined = :joined WHERE networkSlug = :networkSlug AND name = :name")
    suspend fun updateMembership(networkSlug: String, name: String, source: String, joined: Boolean)

    // Query windows (source = "query") and the synthetic "$server" pseudo-channel
    // (source = "server") aren't returned by GET /networks/:slug/channels, so they must
    // be excluded here — otherwise every REST refresh would wipe them out.
    @Query(
        "DELETE FROM channels WHERE networkSlug = :networkSlug AND name NOT IN (:names) " +
            "AND source NOT IN ('query', 'server')",
    )
    suspend fun deleteMissing(networkSlug: String, names: List<String>)

    @Query("DELETE FROM channels WHERE networkSlug = :networkSlug AND source = 'query' AND name NOT IN (:targetNicks)")
    suspend fun deleteMissingQueries(networkSlug: String, targetNicks: List<String>)

    @Query("DELETE FROM channels WHERE networkSlug = :networkSlug AND source = 'query' AND name COLLATE NOCASE = :name")
    suspend fun deleteQuery(networkSlug: String, name: String)

    @Query("UPDATE channels SET topic = :topic WHERE networkSlug = :networkSlug AND name COLLATE NOCASE = :name")
    suspend fun updateTopic(networkSlug: String, name: String, topic: String?)

    // Only ever meaningful on a source='query' row (a real channel's members aren't
    // individually avatar-tracked) — the WHERE clause doesn't enforce that itself since a
    // (networkSlug, name) match is already unambiguous, but callers only fire this off a
    // DM partner's nick.
    @Query("UPDATE channels SET avatarUrl = :avatarUrl WHERE networkSlug = :networkSlug AND name COLLATE NOCASE = :name")
    suspend fun updateAvatarUrl(networkSlug: String, name: String, avatarUrl: String?)

    @Query("SELECT avatarUrl FROM channels WHERE networkSlug = :networkSlug AND name COLLATE NOCASE = :name")
    suspend fun getAvatarUrl(networkSlug: String, name: String): String?

    @Query(
        "UPDATE channels SET modes = :modes, modesRawJson = :modesRawJson " +
            "WHERE networkSlug = :networkSlug AND name COLLATE NOCASE = :name",
    )
    suspend fun updateModes(networkSlug: String, name: String, modes: String?, modesRawJson: String?)

    // Mirrors the server's monotonic advance-only clamp on ReadCursor.set/4 — never
    // regress the locally-cached cursor on a stale/out-of-order update.
    @Query(
        "UPDATE channels SET lastReadMessageId = MAX(COALESCE(lastReadMessageId, 0), :messageId) " +
            "WHERE networkSlug = :networkSlug AND name COLLATE NOCASE = :name",
    )
    suspend fun advanceLastReadMessageId(networkSlug: String, name: String, messageId: Long)

    @Query("SELECT * FROM channels WHERE networkSlug = :networkSlug AND name COLLATE NOCASE = :name")
    fun observeChannel(networkSlug: String, name: String): Flow<ChannelEntity?>

    @Query("SELECT lastReadMessageId FROM channels WHERE networkSlug = :networkSlug AND name COLLATE NOCASE = :name")
    suspend fun getLastReadMessageId(networkSlug: String, name: String): Long?

    @Query(
        "UPDATE channels SET unreadMessages = :messages, unreadMentions = :mentions, severity = :severity " +
            "WHERE networkSlug = :networkSlug AND name COLLATE NOCASE = :name",
    )
    suspend fun updateUnreadCounts(networkSlug: String, name: String, messages: Int, mentions: Int, severity: String)
}
