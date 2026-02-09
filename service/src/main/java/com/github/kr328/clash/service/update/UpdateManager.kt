package com.github.kr328.clash.service.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.service.store.ServiceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Manager for handling app updates.
 */
class UpdateManager(private val context: Context) {
    
    private val store = ServiceStore(context)
    private val updateApi = UpdateApi(store.githubMirror)
    
    /**
     * Check for updates and return update info if available.
     * @param currentVersionCode Current app version code
     * @param includePrerelease Whether to include pre-release versions
     * @return UpdateResult containing update info if available
     */
    suspend fun checkForUpdate(
        currentVersionCode: Long,
        includePrerelease: Boolean = false
    ): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val releases = if (includePrerelease) {
                updateApi.getReleases(REPO_OWNER, REPO_NAME, 10)
            } else {
                val latest = updateApi.getLatestRelease(REPO_OWNER, REPO_NAME)
                if (latest != null) listOf(latest) else emptyList()
            }
            
            // Filter out drafts and optionally prereleases
            val validReleases = releases
                .filter { !it.draft }
                .filter { includePrerelease || !it.prerelease }
            
            if (validReleases.isEmpty()) {
                return@withContext UpdateResult.NoUpdate
            }
            
            // Find the latest release with higher version
            val latestRelease = validReleases
                .maxByOrNull { it.parseVersionCode() }
                ?: return@withContext UpdateResult.NoUpdate
            
            val remoteVersionCode = latestRelease.parseVersionCode()
            
            Log.d("Update check: current=$currentVersionCode, remote=$remoteVersionCode")
            
            if (remoteVersionCode > currentVersionCode) {
                val apkAsset = latestRelease.findApkAsset()
                if (apkAsset != null) {
                    UpdateResult.UpdateAvailable(
                        releaseInfo = latestRelease,
                        downloadUrl = applyMirror(apkAsset.browserDownloadUrl),
                        fileName = apkAsset.name,
                        fileSize = apkAsset.size
                    )
                } else {
                    UpdateResult.NoApkFound(latestRelease)
                }
            } else {
                UpdateResult.NoUpdate
            }
        } catch (e: Exception) {
            Log.w("Update check failed", e)
            UpdateResult.Error(e)
        }
    }
    
    /**
     * Apply GitHub mirror if configured.
     */
    private fun applyMirror(url: String): String {
        val mirror = store.githubMirror
        if (mirror.isNullOrBlank()) {
            return url
        }
        
        // Apply mirror prefix
        val mirrorUrl = mirror.trimEnd('/')
        return "$mirrorUrl/$url"
    }
    
    /**
     * Download APK using system DownloadManager.
     * @param downloadUrl URL to download the APK from
     * @param fileName Name of the file to save
     * @param title Notification title
     * @return Download ID
     */
    fun startDownload(
        downloadUrl: String,
        fileName: String,
        title: String
    ): Long {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        
        // Clean up old APK files
        cleanupOldApks()
        
        val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
            setTitle(title)
            setDescription(fileName)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            setMimeType("application/vnd.android.package-archive")
        }
        
        return downloadManager.enqueue(request)
    }
    
    /**
     * Get download progress.
     * @param downloadId Download ID from startDownload
     * @return DownloadProgress with current status
     */
    fun getDownloadProgress(downloadId: Long): DownloadProgress {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        
        downloadManager.query(query).use { cursor ->
            if (!cursor.moveToFirst()) {
                return DownloadProgress.Unknown
            }
            
            val statusColumn = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val bytesDownloadedColumn = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val bytesTotalColumn = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val reasonColumn = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
            
            val status = cursor.getInt(statusColumn)
            val bytesDownloaded = cursor.getLong(bytesDownloadedColumn)
            val bytesTotal = cursor.getLong(bytesTotalColumn)
            
            return when (status) {
                DownloadManager.STATUS_PENDING -> DownloadProgress.Pending
                DownloadManager.STATUS_RUNNING -> DownloadProgress.Running(bytesDownloaded, bytesTotal)
                DownloadManager.STATUS_PAUSED -> DownloadProgress.Paused(bytesDownloaded, bytesTotal)
                DownloadManager.STATUS_SUCCESSFUL -> DownloadProgress.Completed
                DownloadManager.STATUS_FAILED -> {
                    val reason = cursor.getInt(reasonColumn)
                    DownloadProgress.Failed(reason)
                }
                else -> DownloadProgress.Unknown
            }
        }
    }
    
    /**
     * Wait for download to complete.
     */
    suspend fun waitForDownload(downloadId: Long): DownloadProgress = suspendCancellableCoroutine { cont ->
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    context.unregisterReceiver(this)
                    val progress = getDownloadProgress(downloadId)
                    cont.resume(progress)
                }
            }
        }
        
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        
        cont.invokeOnCancellation {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }
    
    /**
     * Get downloaded file path.
     */
    fun getDownloadedFile(downloadId: Long): File? {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        
        downloadManager.query(query).use { cursor ->
            if (!cursor.moveToFirst()) return null
            
            val localUriColumn = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            val localUri = cursor.getString(localUriColumn) ?: return null
            
            return try {
                File(Uri.parse(localUri).path!!)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Install APK file.
     */
    fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.update",
            file
        )
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        
        context.startActivity(intent)
    }
    
    /**
     * Clean up old downloaded APK files.
     */
    private fun cleanupOldApks() {
        try {
            val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            downloadDir?.listFiles()?.filter { it.extension == "apk" }?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.w("Failed to cleanup old APKs", e)
        }
    }
    
    companion object {
        // Default repository - can be changed for forks
        var REPO_OWNER = "sjnhnp" // Replace with your GitHub username
        var REPO_NAME = "ClashMetaForAndroid"
        
        /**
         * Configure repository for update checks.
         */
        fun configureRepository(owner: String, repo: String) {
            REPO_OWNER = owner
            REPO_NAME = repo
        }
    }
}

/**
 * Result of update check.
 */
sealed class UpdateResult {
    object NoUpdate : UpdateResult()
    
    data class UpdateAvailable(
        val releaseInfo: ReleaseInfo,
        val downloadUrl: String,
        val fileName: String,
        val fileSize: Long
    ) : UpdateResult()
    
    data class NoApkFound(val releaseInfo: ReleaseInfo) : UpdateResult()
    
    data class Error(val exception: Exception) : UpdateResult()
}

/**
 * Download progress state.
 */
sealed class DownloadProgress {
    object Unknown : DownloadProgress()
    object Pending : DownloadProgress()
    data class Running(val bytesDownloaded: Long, val bytesTotal: Long) : DownloadProgress() {
        val progress: Float get() = if (bytesTotal > 0) bytesDownloaded.toFloat() / bytesTotal else 0f
    }
    data class Paused(val bytesDownloaded: Long, val bytesTotal: Long) : DownloadProgress()
    object Completed : DownloadProgress()
    data class Failed(val reason: Int) : DownloadProgress()
}
