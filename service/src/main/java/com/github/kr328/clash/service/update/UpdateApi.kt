package com.github.kr328.clash.service.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * GitHub Release API client for version checking.
 */
class UpdateApi(private val mirror: String? = null) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    /**
     * Apply mirror to URL if configured.
     */
    private fun applyMirror(url: String): String {
        if (mirror.isNullOrBlank()) return url
        val prefix = if (mirror.endsWith("/")) mirror else "$mirror/"
        return "$prefix$url"
    }
    
    /**
     * Get the latest release from GitHub.
     * @param owner Repository owner
     * @param repo Repository name
     * @return Latest release info, or null if no release found
     */
    suspend fun getLatestRelease(owner: String, repo: String): ReleaseInfo? = withContext(Dispatchers.IO) {
        val url = applyMirror("$BASE_URL/repos/$owner/$repo/releases/latest")
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "ClashMetaForAndroid")
            .get()
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 404) {
                    return@withContext null
                }
                throw UpdateApiException("API error: ${response.code} ${response.message}")
            }
            
            val body = response.body?.string() ?: return@withContext null
            json.decodeFromString<ReleaseInfo>(body)
        }
    }
    
    /**
     * Get all releases (including pre-releases) from GitHub.
     * @param owner Repository owner
     * @param repo Repository name
     * @param perPage Number of releases to fetch
     * @return List of releases
     */
    suspend fun getReleases(
        owner: String, 
        repo: String,
        perPage: Int = 10
    ): List<ReleaseInfo> = withContext(Dispatchers.IO) {
        val url = applyMirror("$BASE_URL/repos/$owner/$repo/releases?per_page=$perPage")
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "ClashMetaForAndroid")
            .get()
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw UpdateApiException("API error: ${response.code} ${response.message}")
            }
            
            val body = response.body?.string() ?: return@withContext emptyList()
            json.decodeFromString<List<ReleaseInfo>>(body)
        }
    }
    
    companion object {
        private const val BASE_URL = "https://api.github.com"
    }
}

/**
 * Release info from GitHub API.
 */
@Serializable
data class ReleaseInfo(
    val id: Long,
    @SerialName("tag_name")
    val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url")
    val htmlUrl: String,
    @SerialName("published_at")
    val publishedAt: String? = null,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val assets: List<ReleaseAsset> = emptyList()
) {
    /**
     * Parse version code from tag name.
     * Supports formats like "v3.0.3", "3.0.3", etc.
     */
    fun parseVersionCode(): Long {
        val versionStr = tagName.removePrefix("v").removePrefix("V")
        val parts = versionStr.split(".", "-", "_")
            .filter { it.all { c -> c.isDigit() } }
            .take(3)
        
        return when (parts.size) {
            3 -> {
                val major = parts[0].toLongOrNull() ?: 0
                val minor = parts[1].toLongOrNull() ?: 0
                val patch = parts[2].toLongOrNull() ?: 0
                major * 100000 + minor * 1000 + patch
            }
            2 -> {
                val major = parts[0].toLongOrNull() ?: 0
                val minor = parts[1].toLongOrNull() ?: 0
                major * 100000 + minor * 1000
            }
            1 -> {
                parts[0].toLongOrNull() ?: 0
            }
            else -> 0
        }
    }
    
    /**
     * Find APK asset from release assets.
     */
    fun findApkAsset(): ReleaseAsset? {
        // Prefer arm64-v8a APK
        return assets.find { 
            it.name.endsWith(".apk") && 
            (it.name.contains("arm64") || it.name.contains("arm64-v8a"))
        } ?: assets.find { it.name.endsWith(".apk") }
    }
}

/**
 * Release asset info from GitHub API.
 */
@Serializable
data class ReleaseAsset(
    val id: Long,
    val name: String,
    val size: Long,
    @SerialName("browser_download_url")
    val browserDownloadUrl: String,
    @SerialName("content_type")
    val contentType: String? = null
)

/**
 * Exception for Update API errors.
 */
class UpdateApiException(message: String, cause: Throwable? = null) : IOException(message, cause)
