package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*

// --- Github API ---

interface GithubService {
    @GET("user/repos")
    suspend fun getUserRepos(
        @Header("Authorization") token: String,
        @Query("visibility") visibility: String = "all",
        @Query("affiliation") affiliation: String = "owner,collaborator",
        @Query("per_page") perPage: Int = 100
    ): List<GithubRepoResponse>

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getFileContent(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Query("ref") ref: String?,
        @Header("Authorization") token: String?
    ): GithubFileResponse

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun updateFileContent(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Header("Authorization") token: String,
        @Body request: GithubUpdateContentRequest
    ): GithubUpdateResponse
}

data class GithubRepoResponse(
    val id: Long,
    val name: String,
    @Json(name = "full_name") val fullName: String,
    val description: String?,
    @Json(name = "html_url") val htmlUrl: String,
    val language: String?
)

data class GithubFileResponse(
    val sha: String,
    val content: String?
)

data class GithubUpdateContentRequest(
    val message: String,
    val content: String,
    val sha: String,
    val branch: String
)

data class GithubUpdateResponse(
    val content: Any?
)

object GithubApiClient {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .build()

    val service: GithubService = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(GithubService::class.java)
}

// --- Gemini API ---

interface GeminiService {
    @POST("v1beta/models/gemini-1.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse

    @POST
    suspend fun generateContentCustom(
        @Url url: String,
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse

    @GET("v1beta/models")
    suspend fun listModels(
        @Query("key") apiKey: String
    ): GeminiListModelsResponse
}

data class GeminiListModelsResponse(
    val models: List<GeminiModelInfo>?
)

data class GeminiModelInfo(
    val name: String,
    val displayName: String? = null,
    val description: String? = null,
    val supportedGenerationMethods: List<String>? = null
)

data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null
)

data class Content(
    val role: String? = null,
    val parts: List<Part>
)

data class Part(
    val text: String
)

data class GenerationConfig(
    val temperature: Float
)

data class GenerateContentResponse(
    val candidates: List<Candidate>?
)

data class Candidate(
    val content: Content?
)

object GeminiApiClient {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val client = OkHttpClient.Builder().build()

    val service: GeminiService = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(GeminiService::class.java)
}

// --- OpenAI API (Compatible) ---

interface OpenAiService {
    @POST("v1/chat/completions")
    suspend fun getCompletion(
        @Header("Authorization") auth: String,
        @Body request: OpenAiRequest
    ): OpenAiResponse

    @GET("v1/models")
    suspend fun listModels(
        @Header("Authorization") authHeader: String
    ): OpenAiListModelsResponse
}

data class OpenAiListModelsResponse(
    val data: List<OpenAiModelInfo>?
)

data class OpenAiModelInfo(
    val id: String
)

data class OpenAiRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val temperature: Float? = null
)

data class OpenAiMessage(
    val role: String,
    val content: String
)

data class OpenAiResponse(
    val choices: List<OpenAiChoice>?
)

data class OpenAiChoice(
    val message: OpenAiMessage?
)
