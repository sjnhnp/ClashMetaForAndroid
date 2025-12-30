package com.github.kr328.clash.service.gist.model

import kotlinx.serialization.Serializable

/**
 * Data model representing a complete backup of CMFA configuration.
 */
@Serializable
data class GistBackupData(
    val version: Int = CURRENT_VERSION,
    val timestamp: Long = System.currentTimeMillis(),
    val appName: String = "ClashMetaForAndroid",
    
    // Service settings (from ServiceStore)
    val serviceSettings: Map<String, String> = emptyMap(),
    
    // UI settings (from UiStore)
    val uiSettings: Map<String, String> = emptyMap(),
    
    // Imported profiles metadata
    val profiles: List<ProfileBackup> = emptyList(),
    
    // Profile configurations (config.yaml content for each profile)
    val profileConfigs: Map<String, String> = emptyMap(),
    
    // Provider files content
    val providerFiles: Map<String, String> = emptyMap(),
    
    // Proxy selections
    val selections: Map<String, String> = emptyMap(),
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

/**
 * Backup data for a single profile.
 */
@Serializable
data class ProfileBackup(
    val uuid: String,
    val name: String,
    val type: String,
    val source: String,
    val interval: Long,
    val upload: Long,
    val download: Long,
    val total: Long,
    val expire: Long,
    val createdAt: Long,
)

/**
 * Response model for Gist API.
 */
@Serializable
data class GistResponse(
    val id: String,
    val description: String?,
    val files: Map<String, GistFile>,
    val created_at: String? = null,
    val updated_at: String? = null,
)

@Serializable
data class GistFile(
    val filename: String? = null,
    val content: String? = null,
    val raw_url: String? = null,
)

/**
 * Request model for creating/updating Gist.
 */
@Serializable
data class GistCreateRequest(
    val description: String,
    val public: Boolean = false,
    val files: Map<String, GistFileContent>,
)

@Serializable
data class GistFileContent(
    val content: String,
)
