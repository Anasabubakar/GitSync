package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GithubUser(
    @Json(name = "login") val login: String,
    @Json(name = "avatar_url") val avatarUrl: String?
)

@JsonClass(generateAdapter = true)
data class GithubRepoResponse(
    @Json(name = "id") val id: Long,
    @Json(name = "name") val name: String,
    @Json(name = "description") val description: String?,
    @Json(name = "html_url") val htmlUrl: String,
    @Json(name = "fork") val fork: Boolean,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String,
    @Json(name = "language") val language: String?
)

@JsonClass(generateAdapter = true)
data class GithubCommitUser(
    @Json(name = "name") val name: String,
    @Json(name = "date") val date: String
)

@JsonClass(generateAdapter = true)
data class GithubCommitDetails(
    @Json(name = "message") val message: String,
    @Json(name = "committer") val committer: GithubCommitUser
)

@JsonClass(generateAdapter = true)
data class GithubCommitResponse(
    @Json(name = "sha") val sha: String,
    @Json(name = "commit") val commit: GithubCommitDetails
)

@JsonClass(generateAdapter = true)
data class GithubContentResponse(
    @Json(name = "type") val type: String,
    @Json(name = "encoding") val encoding: String?,
    @Json(name = "size") val size: Long,
    @Json(name = "name") val name: String,
    @Json(name = "path") val path: String,
    @Json(name = "content") val content: String?,
    @Json(name = "sha") val sha: String
)

@JsonClass(generateAdapter = true)
data class GithubUpdateContentRequest(
    @Json(name = "message") val message: String,
    @Json(name = "content") val content: String, // base64 encoded content
    @Json(name = "sha") val sha: String,         // required if updating existing file
    @Json(name = "branch") val branch: String
)

@JsonClass(generateAdapter = true)
data class GithubUpdateContentResponse(
    @Json(name = "content") val content: GithubContentResponse?
)

interface GithubService {
    @GET("user/repos")
    suspend fun getAuthenticatedRepos(
        @Header("Authorization") token: String,
        @Query("sort") sort: String = "updated",
        @Query("per_page") perPage: Int = 100
    ): List<GithubRepoResponse>

    @GET("users/{username}/repos")
    suspend fun getPublicRepos(
        @Path("username") username: String,
        @Query("sort") sort: String = "updated",
        @Query("per_page") perPage: Int = 100
    ): List<GithubRepoResponse>

    @GET("repos/{owner}/{repo}/commits")
    suspend fun getCommits(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Authorization") token: String?,
        @Query("per_page") perPage: Int = 10
    ): List<GithubCommitResponse>

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getFileContent(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Query("ref") ref: String?,
        @Header("Authorization") token: String?
    ): GithubContentResponse

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun updateFileContent(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Header("Authorization") token: String,
        @Body request: GithubUpdateContentRequest
    ): GithubUpdateContentResponse
}

object GithubApiClient {
    private const val BASE_URL = "https://api.github.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    val service: GithubService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
        retrofit.create(GithubService::class.java)
    }
}
