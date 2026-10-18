package com.wave.app.network

import retrofit2.http.GET
import retrofit2.http.Query

interface CloudflareService {
    @GET(".")
    suspend fun search(
        @Query("service") service: String, // spotify | soundcloud
        @Query("q") query: String
    ): List<TrackResponse>
}
