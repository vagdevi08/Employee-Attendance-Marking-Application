package com.kbyai.facerecognition

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

object SupabaseClient {
    private const val SUPABASE_URL = "https://hmnzfdtoyxjxzwzxgdgr.supabase.co/"
    private const val SUPABASE_KEY = "sb_publishable_qTLlVaICguS6RwJh13uIEw_ReKIAOG_"

    private val api: SupabaseApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(SUPABASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()

        retrofit.create(SupabaseApi::class.java)
    }

    fun postAttendance(payload: SupabaseAttendance, onComplete: (() -> Unit)? = null) {
        api.sendAttendance(
            payload,
            SUPABASE_KEY,
            "Bearer $SUPABASE_KEY"
        ).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                onComplete?.invoke()
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                // TODO: enqueue for retry in offline mode
                onComplete?.invoke()
            }
        })
    }
}

