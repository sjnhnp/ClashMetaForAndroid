package com.github.kr328.clash.service.util

import android.net.Uri

/**
 * Utility for applying GitHub mirror proxy to URLs
 */
object GithubMirror {
    
    private val GITHUB_DOMAINS = listOf(
        "github.com",
        "api.github.com",
        "raw.githubusercontent.com",
        "gist.githubusercontent.com",
        "objects.githubusercontent.com",
        "codeload.github.com"
    )
    
    /**
     * Apply GitHub mirror prefix to URL if it's a GitHub URL
     * 
     * @param url Original URL
     * @param mirror Mirror prefix (e.g., "https://ghfast.top" or "https://ghfast.top/")
     * @return Processed URL with mirror prefix, or original URL if not a GitHub URL
     */
    fun apply(url: String, mirror: String?): String {
        if (mirror.isNullOrBlank()) return url
        
        val uri = try {
            Uri.parse(url)
        } catch (e: Exception) {
            return url
        }
        
        val host = uri.host ?: return url
        
        // Check if URL is from GitHub
        if (GITHUB_DOMAINS.none { host.equals(it, ignoreCase = true) || host.endsWith(".$it", ignoreCase = true) }) {
            return url
        }
        
        // Normalize mirror prefix (ensure it ends with /)
        val prefix = if (mirror.endsWith("/")) mirror else "$mirror/"
        
        return "$prefix$url"
    }
}
