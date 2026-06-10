package com.example.mediarecorder

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface GeminiMusicService {
    @Multipart
    @POST("v1/music/convert")
    suspend fun convertMusic(
        @Part audioFile: MultipartBody.Part,
        @Part("genre") genre: RequestBody,
        @Part("weather") weather: RequestBody,
        @Part("location") location: RequestBody
    ): retrofit2.Response<ResponseBody>

    companion object {
        private const val BASE_URL = "https://api.gemini-lyria-music.internal/"

        fun create(): GeminiMusicService {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(GeminiMusicService::class.java)
        }
    }
}
