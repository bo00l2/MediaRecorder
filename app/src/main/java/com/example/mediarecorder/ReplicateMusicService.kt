package com.example.mediarecorder

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

// TODO:여기에 본인의 Replicate API 토큰을 입력하세요. (예: "r8_...")
// 토큰이 없거나 유효하지 않으면 이전과 동일한 로컬 Mock 모드로 실행됩니다.
const val REPLICATE_API_TOKEN = ""

interface ReplicateMusicService {

    @POST("v1/predictions")
    suspend fun createPrediction(
        @Header("Authorization") authorization: String,
        @Body request: ReplicatePredictionRequest
    ): Response<ReplicatePredictionResponse>

    @GET("v1/predictions/{id}")
    suspend fun getPrediction(
        @Header("Authorization") authorization: String,
        @Path("id") id: String
    ): Response<ReplicatePredictionResponse>

    companion object {
        private const val BASE_URL = "https://api.replicate.com/"

        fun create(): ReplicateMusicService {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ReplicateMusicService::class.java)
        }
    }
}

data class ReplicatePredictionRequest(
    val version: String,
    val input: Map<String, Any>
)

data class ReplicatePredictionResponse(
    val id: String,
    val status: String,
    val output: Any?, // output은 문자열(URL) 또는 문자열 리스트일 수 있습니다.
    val error: String?,
    val urls: Map<String, String>?
)
