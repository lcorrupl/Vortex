package com.example.network

import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Url

interface CobaltService {
    @Headers(
        "Accept: application/json",
        "Content-Type: application/json"
    )
    @POST
    suspend fun getDownloadLink(
        @Url endpointUrl: String,
        @Body request: CobaltRequest
    ): CobaltResponse
}
