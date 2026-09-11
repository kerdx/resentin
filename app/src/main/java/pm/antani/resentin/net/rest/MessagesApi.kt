package pm.antani.resentin.net.rest

import pm.antani.resentin.net.dto.ReadCursorRequestDto
import pm.antani.resentin.net.dto.ReadCursorResponseDto
import pm.antani.resentin.net.dto.ScrollbackMessageDto
import pm.antani.resentin.net.dto.SendMessageDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface MessagesApi {
    @POST("networks/{slug}/channels/{channel}/messages")
    suspend fun sendMessage(
        @Path("slug") slug: String,
        @Path("channel") channel: String,
        @Body body: SendMessageDto,
    ): Response<ScrollbackMessageDto>

    /** Same POST but without decoding the 201 body — service PRIVMSGs (NickServ,
     * ChanServ, ...) answer with a bare ack, not a scrollback row, so the typed
     * [sendMessage] converter blows up on them ("fields [id, network, ...] are
     * required"). The reply is never echoed into a window anyway (it lands on
     * $server via the services-sender routing), so there is nothing to record. */
    @POST("networks/{slug}/channels/{channel}/messages")
    suspend fun sendServiceMessage(
        @Path("slug") slug: String,
        @Path("channel") channel: String,
        @Body body: SendMessageDto,
    ): Response<ResponseBody>

    @GET("networks/{slug}/channels/{channel}/messages")
    suspend fun getMessages(
        @Path("slug") slug: String,
        @Path("channel") channel: String,
        @Query("after") after: Long? = null,
        @Query("before") before: Long? = null,
        @Query("limit") limit: Int? = null,
    ): List<ScrollbackMessageDto>

    /** Monotonic advance-only on the server — safe to call with any id, even a stale one. */
    @POST("networks/{slug}/channels/{channel}/read-cursor")
    suspend fun setReadCursor(
        @Path("slug") slug: String,
        @Path("channel") channel: String,
        @Body body: ReadCursorRequestDto,
    ): Response<ReadCursorResponseDto>
}
