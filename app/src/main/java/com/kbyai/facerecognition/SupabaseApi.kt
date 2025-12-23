package com.kbyai.facerecognition

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface SupabaseApi {
    @POST("rest/v1/attendance")
    fun sendAttendance(
        @Body payload: SupabaseAttendance,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Header("Prefer") prefer: String = "return=minimal"
    ): Call<Void>
}

