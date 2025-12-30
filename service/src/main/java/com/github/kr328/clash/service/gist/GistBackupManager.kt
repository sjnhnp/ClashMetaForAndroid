package com.github.kr328.clash.service.gist

import android.content.Context
import android.content.SharedPreferences
import com.github.kr328.clash.service.PreferenceProvider
import com.github.kr328.clash.service.data.Database
import com.github.kr328.clash.service.data.Imported
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.data.Selection
import com.github.kr328.clash.service.data.SelectionDao
import com.github.kr328.clash.service.gist.model.GistBackupData
import com.github.kr328.clash.service.gist.model.GistResponse
import com.github.kr328.clash.service.gist.model.ProfileBackup
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.util.importedDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Manager for creating and restoring Gist backups.
 */
class GistBackupManager(private val context: Context) {
    
    private val gistStore = GistStore(context)
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }
    
    /**
     * Result of backup/restore operations.
     */
    sealed class Result<out T> {
        data class Success<T>(val data: T) : Result<T>()
        data class Error(val message: String, val exception: Throwable? = null) : Result<Nothing>()
    }
    
    /**
     * Backup info for display in UI.
     */
    data class BackupInfo(
        val id: String,
        val description: String,
        val createdAt: String,
    )
    
    /**
     * Create a backup and upload to Gist.
     */
    suspend fun createBackup(onProgress: (String) -> Unit = {}): Result<String> {
        val token = gistStore.githubToken ?: return Result.Error("GitHub Token not configured")
        val secret = gistStore.encryptionSecret ?: return Result.Error("Encryption secret not configured")
        
        return try {
            onProgress("Collecting settings...")
            val backupData = collectBackupData()
            
            onProgress("Encrypting data...")
            val jsonData = json.encodeToString(backupData)
            val encryptedData = GistCrypto.encrypt(jsonData, secret)
            
            onProgress("Uploading to Gist...")
            val api = GistApi(token)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val description = "${BACKUP_PREFIX}_${dateFormat.format(Date())}"
            
            val response = api.createGist(
                description = description,
                files = mapOf(BACKUP_FILENAME to encryptedData),
                public = false
            )
            
            Result.Success(response.id)
        } catch (e: Exception) {
            Result.Error("Backup failed: ${e.message}", e)
        }
    }
    
    /**
     * List available backups from Gist.
     */
    suspend fun listBackups(): Result<List<BackupInfo>> {
        val token = gistStore.githubToken ?: return Result.Error("GitHub Token not configured")
        
        return try {
            val api = GistApi(token)
            val gists = api.listGists()
            
            val backups = gists
                .filter { it.description?.startsWith(BACKUP_PREFIX) == true }
                .map { gist ->
                    BackupInfo(
                        id = gist.id,
                        description = gist.description ?: "",
                        createdAt = gist.created_at ?: ""
                    )
                }
                .sortedByDescending { it.createdAt }
            
            Result.Success(backups)
        } catch (e: Exception) {
            Result.Error("Failed to list backups: ${e.message}", e)
        }
    }
    
    /**
     * Restore from a specific Gist backup.
     */
    suspend fun restoreBackup(
        gistId: String,
        onProgress: (String) -> Unit = {}
    ): Result<Unit> {
        val token = gistStore.githubToken ?: return Result.Error("GitHub Token not configured")
        val secret = gistStore.encryptionSecret ?: return Result.Error("Encryption secret not configured")
        
        return try {
            onProgress("Fetching backup...")
            val api = GistApi(token)
            val gist = api.getGist(gistId)
            
            val file = gist.files[BACKUP_FILENAME]
                ?: return Result.Error("Invalid backup: missing data file")
            
            val encryptedData = file.raw_url?.let { api.getGistFileContent(it) }
                ?: file.content
                ?: return Result.Error("Invalid backup: empty content")
            
            onProgress("Decrypting data...")
            val jsonData = try {
                GistCrypto.decrypt(encryptedData, secret)
            } catch (e: Exception) {
                return Result.Error("Decryption failed: wrong secret key or corrupted data")
            }
            
            val backupData = json.decodeFromString<GistBackupData>(jsonData)
            
            onProgress("Restoring settings...")
            restoreBackupData(backupData, onProgress)
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Restore failed: ${e.message}", e)
        }
    }
    
    /**
     * Delete a backup from Gist.
     */
    suspend fun deleteBackup(gistId: String): Result<Unit> {
        val token = gistStore.githubToken ?: return Result.Error("GitHub Token not configured")
        
        return try {
            val api = GistApi(token)
            api.deleteGist(gistId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Delete failed: ${e.message}", e)
        }
    }
    
    /**
     * Collect all data for backup.
     */
    private suspend fun collectBackupData(): GistBackupData = withContext(Dispatchers.IO) {
        val servicePrefs = PreferenceProvider.createSharedPreferencesFromContext(context)
        val uiPrefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)
        
        // Collect settings
        val serviceSettings = prefsToMap(servicePrefs)
        val uiSettings = prefsToMap(uiPrefs)
        
        // Collect profiles
        val importedDao = ImportedDao()
        val selectionDao = SelectionDao()
        val uuids = importedDao.queryAllUUIDs()
        
        val profiles = mutableListOf<ProfileBackup>()
        val profileConfigs = mutableMapOf<String, String>()
        val providerFiles = mutableMapOf<String, String>()
        val selections = mutableMapOf<String, String>()
        
        for (uuid in uuids) {
            val imported = importedDao.queryByUUID(uuid) ?: continue
            
            // Profile metadata
            profiles.add(
                ProfileBackup(
                    uuid = uuid.toString(),
                    name = imported.name,
                    type = imported.type.name,
                    source = imported.source,
                    interval = imported.interval,
                    upload = imported.upload,
                    download = imported.download,
                    total = imported.total,
                    expire = imported.expire,
                    createdAt = imported.createdAt
                )
            )
            
            // Profile config file
            val profileDir = context.importedDir.resolve(uuid.toString())
            val configFile = profileDir.resolve("config.yaml")
            if (configFile.exists()) {
                profileConfigs[uuid.toString()] = configFile.readText()
            }
            
            // Provider files
            val providersDir = profileDir.resolve("providers")
            if (providersDir.exists() && providersDir.isDirectory) {
                providersDir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        val key = "${uuid}/providers/${file.name}"
                        providerFiles[key] = file.readText()
                    }
                }
            }
            
            // Proxy selections
            val profileSelections = selectionDao.querySelections(uuid)
            for (selection in profileSelections) {
                val key = "${uuid}/${selection.proxy}"
                selections[key] = selection.selected
            }
        }
        
        GistBackupData(
            serviceSettings = serviceSettings,
            uiSettings = uiSettings,
            profiles = profiles,
            profileConfigs = profileConfigs,
            providerFiles = providerFiles,
            selections = selections
        )
    }
    
    /**
     * Restore data from backup.
     */
    private suspend fun restoreBackupData(
        data: GistBackupData,
        onProgress: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        // Restore service settings
        onProgress("Restoring service settings...")
        val servicePrefs = PreferenceProvider.createSharedPreferencesFromContext(context)
        mapToPrefs(data.serviceSettings, servicePrefs)
        
        // Restore UI settings
        onProgress("Restoring UI settings...")
        val uiPrefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)
        mapToPrefs(data.uiSettings, uiPrefs)
        
        // Restore profiles
        val importedDao = ImportedDao()
        val selectionDao = SelectionDao()
        
        for (profile in data.profiles) {
            onProgress("Restoring profile: ${profile.name}...")
            
            val uuid = UUID.fromString(profile.uuid)
            
            // Check if profile already exists
            if (importedDao.exists(uuid)) {
                // Update existing profile
                val imported = Imported(
                    uuid = uuid,
                    name = profile.name,
                    type = Profile.Type.valueOf(profile.type),
                    source = profile.source,
                    interval = profile.interval,
                    upload = profile.upload,
                    download = profile.download,
                    total = profile.total,
                    expire = profile.expire,
                    createdAt = profile.createdAt
                )
                importedDao.update(imported)
            } else {
                // Insert new profile
                val imported = Imported(
                    uuid = uuid,
                    name = profile.name,
                    type = Profile.Type.valueOf(profile.type),
                    source = profile.source,
                    interval = profile.interval,
                    upload = profile.upload,
                    download = profile.download,
                    total = profile.total,
                    expire = profile.expire,
                    createdAt = profile.createdAt
                )
                importedDao.insert(imported)
            }
            
            // Restore config file
            val profileDir = context.importedDir.resolve(uuid.toString())
            profileDir.mkdirs()
            
            data.profileConfigs[profile.uuid]?.let { config ->
                profileDir.resolve("config.yaml").writeText(config)
            }
            
            // Restore provider files
            val providersDir = profileDir.resolve("providers")
            providersDir.mkdirs()
            
            data.providerFiles
                .filterKeys { it.startsWith("${profile.uuid}/providers/") }
                .forEach { (key, content) ->
                    val fileName = key.substringAfterLast("/")
                    providersDir.resolve(fileName).writeText(content)
                }
        }
        
        // Restore selections
        onProgress("Restoring proxy selections...")
        for ((key, selected) in data.selections) {
            val parts = key.split("/", limit = 2)
            if (parts.size == 2) {
                val uuid = UUID.fromString(parts[0])
                val proxy = parts[1]
                selectionDao.setSelected(Selection(uuid, proxy, selected))
            }
        }
    }
    
    /**
     * Convert SharedPreferences to Map for serialization.
     */
    private fun prefsToMap(prefs: SharedPreferences): Map<String, String> {
        val map = mutableMapOf<String, String>()
        prefs.all.forEach { (key, value) ->
            when (value) {
                is String -> map["s:$key"] = value
                is Int -> map["i:$key"] = value.toString()
                is Long -> map["l:$key"] = value.toString()
                is Boolean -> map["b:$key"] = value.toString()
                is Float -> map["f:$key"] = value.toString()
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    val stringSet = value as? Set<String>
                    stringSet?.let { map["ss:$key"] = it.joinToString("\u0000") }
                }
            }
        }
        return map
    }
    
    /**
     * Restore Map to SharedPreferences.
     */
    private fun mapToPrefs(map: Map<String, String>, prefs: SharedPreferences) {
        val editor = prefs.edit()
        map.forEach { (key, value) ->
            val type = key.substringBefore(":")
            val actualKey = key.substringAfter(":")
            when (type) {
                "s" -> editor.putString(actualKey, value)
                "i" -> editor.putInt(actualKey, value.toIntOrNull() ?: 0)
                "l" -> editor.putLong(actualKey, value.toLongOrNull() ?: 0L)
                "b" -> editor.putBoolean(actualKey, value.toBooleanStrictOrNull() ?: false)
                "f" -> editor.putFloat(actualKey, value.toFloatOrNull() ?: 0f)
                "ss" -> editor.putStringSet(actualKey, value.split("\u0000").toSet())
            }
        }
        editor.apply()
    }
    
    companion object {
        private const val BACKUP_PREFIX = "CMFA_Backup"
        private const val BACKUP_FILENAME = "cmfa_backup.enc"
    }
}

/**
 * Extension function to use ImportedDao from Database.
 */
private fun ImportedDao(): com.github.kr328.clash.service.data.ImportedDao {
    return Database.database.openImportedDao()
}

/**
 * Extension function to use SelectionDao from Database.
 */
private fun SelectionDao(): com.github.kr328.clash.service.data.SelectionDao {
    return Database.database.openSelectionProxyDao()
}
