package com.github.kr328.clash

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import com.github.kr328.clash.common.compat.versionCodeCompat
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.design.UpdateDesign
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.service.update.DownloadProgress
import com.github.kr328.clash.service.update.UpdateManager
import com.github.kr328.clash.service.update.UpdateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UpdateActivity : BaseActivity<UpdateDesign>() {
    
    private val updateManager by lazy { UpdateManager(this) }
    private var currentDownloadId: Long = -1
    
    private val installPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Check if we can install and retry
        if (canInstallPackages()) {
            currentDownloadId.takeIf { it >= 0 }?.let { downloadId ->
                updateManager.getDownloadedFile(downloadId)?.let { file ->
                    if (file.exists()) {
                        updateManager.installApk(file)
                    }
                }
            }
        }
    }
    
    override suspend fun main() {
        val design = UpdateDesign(this)
        setContentDesign(design)
        
        val versionCode = packageManager.getPackageInfo(packageName, 0).versionCodeCompat
        var cachedResult: UpdateResult? = null
        
        try {
            // Start checking for updates
            design.setChecking(true)
            
            val result = updateManager.checkForUpdate(versionCode, includePrerelease = false)
            cachedResult = result
            
            when (result) {
                is UpdateResult.UpdateAvailable -> {
                    design.setChecking(false)
                    design.showUpdateAvailable(
                        versionName = result.releaseInfo.tagName,
                        changelog = result.releaseInfo.body ?: "",
                        fileSize = result.fileSize
                    )
                }
                is UpdateResult.NoUpdate -> {
                    design.setChecking(false)
                    design.showNoUpdate()
                }
                is UpdateResult.NoApkFound -> {
                    design.setChecking(false)
                    design.showError(getString(com.github.kr328.clash.design.R.string.update_no_apk_found))
                }
                is UpdateResult.Error -> {
                    design.setChecking(false)
                    design.showError(result.exception.message ?: getString(com.github.kr328.clash.design.R.string.unknown_error))
                }
            }
        } catch (e: Exception) {
            design.setChecking(false)
            design.showError(e.message ?: getString(com.github.kr328.clash.design.R.string.unknown_error))
        }
        
        while (isActive) {
            when (val request = design.requests.receive()) {
                UpdateDesign.Request.Download -> {
                    val result = if (cachedResult is UpdateResult.UpdateAvailable) {
                        cachedResult as UpdateResult.UpdateAvailable
                    } else {
                        val newResult = updateManager.checkForUpdate(versionCode, includePrerelease = false)
                        cachedResult = newResult
                        newResult as? UpdateResult.UpdateAvailable
                    }
                    
                    if (result != null) {
                        startDownload(design, result)
                    } else {
                        design.showError(getString(com.github.kr328.clash.design.R.string.update_check_failed))
                    }
                }
                UpdateDesign.Request.Install -> {
                    currentDownloadId.takeIf { it >= 0 }?.let { downloadId ->
                        val file = updateManager.getDownloadedFile(downloadId)
                        if (file != null && file.exists()) {
                            if (!canInstallPackages()) {
                                requestInstallPermission()
                            } else {
                                updateManager.installApk(file)
                            }
                        }
                    }
                }
                UpdateDesign.Request.OpenInBrowser -> {
                    var result = cachedResult
                    
                    if (result == null || result is UpdateResult.Error) {
                        result = updateManager.checkForUpdate(versionCode, includePrerelease = false)
                        cachedResult = result
                    }
                    
                    if (result is UpdateResult.UpdateAvailable) {
                        openUrl(result.releaseInfo.htmlUrl)
                    } else if (result is UpdateResult.NoApkFound) {
                        openUrl(result.releaseInfo.htmlUrl)
                    } else {
                        // If we still can't get the URL, standard fallback could be opening releases page
                        // but we don't have the repo URL handy here without duplicating logic.
                        // For now just ignore or maybe show toast.
                    }
                }
                UpdateDesign.Request.Cancel -> {
                    finish()
                }
            }
        }
    }
    
    private suspend fun startDownload(design: UpdateDesign, updateInfo: UpdateResult.UpdateAvailable) {
        design.setDownloading(true)
        
        currentDownloadId = updateManager.startDownload(
            downloadUrl = updateInfo.downloadUrl,
            fileName = updateInfo.fileName,
            title = getString(com.github.kr328.clash.design.R.string.downloading_update)
        )
        
        // Monitor download progress
        launch {
            while (isActive) {
                val progress = updateManager.getDownloadProgress(currentDownloadId)
                when (progress) {
                    is DownloadProgress.Running -> {
                        design.setDownloadProgress(progress.progress, progress.bytesDownloaded, progress.bytesTotal)
                    }
                    is DownloadProgress.Completed -> {
                        design.setDownloadComplete()
                        break
                    }
                    is DownloadProgress.Failed -> {
                        design.setDownloading(false)
                        design.showError(getString(com.github.kr328.clash.design.R.string.download_failed))
                        break
                    }
                    else -> {}
                }
                delay(500)
            }
        }
    }
    
    private fun canInstallPackages(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }
    
    private fun requestInstallPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:$packageName")
            }
            installPermissionLauncher.launch(intent)
        }
    }
    
    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            // Ignore
        }
    }
}
