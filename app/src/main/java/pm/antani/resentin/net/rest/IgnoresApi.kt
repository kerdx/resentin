package pm.antani.resentin.net.rest

import pm.antani.resentin.net.dto.IgnoreRequestDto
import pm.antani.resentin.net.dto.IgnoresDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Server /ignore mask list (#162, per-network). A bare nick POSTs as-is — the
 * server normalises it to `nick!*@*` — and every mutation answers the resulting
 * full list, so no extra GET is needed to stay current. `:network_id` accepts a
 * slug (ResolveNetwork), like every other network-scoped route this app uses. */
interface IgnoresApi {
    @GET("networks/{slug}/ignores")
    suspend fun getIgnores(@Path("slug") slug: String): IgnoresDto

    @POST("networks/{slug}/ignores")
    suspend fun addIgnore(@Path("slug") slug: String, @Body body: IgnoreRequestDto): IgnoresDto

    @DELETE("networks/{slug}/ignores/{mask}")
    suspend fun removeIgnore(@Path("slug") slug: String, @Path("mask") mask: String): IgnoresDto
}
