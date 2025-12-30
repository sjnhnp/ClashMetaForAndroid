package com.github.kr328.clash.service.gist

import com.github.kr328.clash.service.gist.model.GistCreateRequest
import com.github.kr328.clash.service.gist.model.GistFileContent
import com.github.kr328.clash.service.gist.model.GistResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * GitHub Gist API client for backup/restore operations.
 */
class GistApi(private val token: String) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    /**
     * Get all Gists for the authenticated user.
     */
    suspend fun listGists(): List<GistResponse> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/gists")
            .headers(buildHeaders())
            .get()
            .build()
        
        executeRequest(request) { body ->
            json.decodeFromString<List<GistResponse>>(body)
        }
    }
    
    /**
     * Get a specific Gist by ID.
     */
    suspend fun getGist(gistId: String): GistResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/gists/$gistId")
            .headers(buildHeaders())
            .get()
            .build()
        
        executeRequest(request) { body ->
            json.decodeFromString<GistResponse>(body)
        }
    }
    
    /**
     * Get raw file content from a Gist.
     */
    suspend fun getGistFileContent(rawUrl: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(rawUrl)
            .headers(buildHeaders())
            .get()
            .build()
        
        executeRequest(request) { it }
    }
    
    /**
     * Create a new Gist with encrypted backup data.
     */
    suspend fun createGist(
        description: String,
        files: Map<String, String>,
        public: Boolean = false
    ): GistResponse = withContext(Dispatchers.IO) {
        val gistFiles = files.mapValues { GistFileContent(it.value) }
        val createRequest = GistCreateRequest(
            description = description,
            public = public,
            files = gistFiles
        )
        
        val requestBody = json.encodeToString(createRequest)
            .toRequestBody(JSON_MEDIA_TYPE)
        
        val request = Request.Builder()
            .url("$BASE_URL/gists")
            .headers(buildHeaders())
            .post(requestBody)
            .build()
        
        executeRequest(request) { body ->
            json.decodeFromString<GistResponse>(body)
        }
    }
    
    /**
     * Delete a Gist by ID.
     */
    suspend fun deleteGist(gistId: String): Unit = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/gists/$gistId")
            .headers(buildHeaders())
            .delete()
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 204) {
                throw GistApiException("Delete failed: ${response.code} ${response.message}")
            }
        }
    }
    
    private fun buildHeaders(): okhttp3.Headers {
        return okhttp3.Headers.Builder()
            .add("Accept", "application/vnd.github+json")
            .add("Authorization", "Bearer $token")
            .add("X-GitHub-Api-Version", "2022-11-28")
            .add("User-Agent", "ClashMetaForAndroid")
            .build()
    }
    
    private inline fun <T> executeRequest(
        request: Request,
        parser: (String) -> T
    ): T {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            
            if (!response.isSuccessful) {
                throw GistApiException("API error: ${response.code} $body")
            }
            
            return parser(body)
        }
    }
    
    companion object {
        private const val BASE_URL = "https://api.github.com"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

/**
 * Exception for Gist API errors.
 */
class GistApiException(message: String, cause: Throwable? = null) : IOException(message, cause)
