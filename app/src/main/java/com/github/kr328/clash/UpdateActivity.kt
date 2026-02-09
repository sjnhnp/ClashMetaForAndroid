package com.github.kr328.clash

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import com.github.kr328.clash.common.compat.versionCodeCompat
import com.github.kr328.clash.design.UpdateDesign
import com.github.kr328.clash.service.update.DownloadProgress
import com.github.kr328.clash.service.update.UpdateManager
import com.github.kr328.clash.service.update.UpdateResult
import kotlinx.coroutines.*

class UpdateActivity : BaseActivity<UpdateDesign>() {

    private val updateManager by lazy { UpdateManager(this) }
    private var currentDownloadId: Long = -1
    private var downloadMonitorJob: Job? = null

    private val installPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
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

        var cachedResult: UpdateResult? = null
        var currentVersionCode: Long = 0

        // 初始检查更新
        try {
            design.setChecking(true)

            currentVersionCode = try {
                packageManager.getPackageInfo(packageName, 0).versionCodeCompat
            } catch (e: Exception) {
                0L
            }

            val result = withContext(Dispatchers.IO) {
                updateManager.checkForUpdate(currentVersionCode, includePrerelease = false)
            }
            cachedResult = result
            design.setChecking(false)

            when (result) {
                is UpdateResult.UpdateAvailable -> {
                    design.showUpdateAvailable(
                        versionName = result.releaseInfo.tagName,
                        changelog = result.releaseInfo.body ?: "",
                        fileSize = result.fileSize
                    )
                }
                is UpdateResult.NoUpdate -> {
                    design.showNoUpdate()
                }
                is UpdateResult.NoApkFound -> {
                    design.showError(
                        getString(com.github.kr328.clash.design.R.string.update_no_apk_found)
                    )
                }
                is UpdateResult.Error -> {
                    design.showError(
                        result.exception.message
                            ?: getString(com.github.kr328.clash.design.R.string.unknown_error)
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            design.setChecking(false)
            design.showError(
                e.message ?: getString(com.github.kr328.clash.design.R.string.unknown_error)
            )
        }

        // 事件循环
        while (isActive) {
            val request = try {
                design.requests.receive()
            } catch (e: CancellationException) {
                break
            }

            try {
                when (request) {
                    UpdateDesign.Request.Download -> {
                        val result = if (cachedResult is UpdateResult.UpdateAvailable) {
                            cachedResult as UpdateResult.UpdateAvailable
                        } else {
                            val newResult = withContext(Dispatchers.IO) {
                                updateManager.checkForUpdate(
                                    currentVersionCode,
                                    includePrerelease = false
                                )
                            }
                            cachedResult = newResult
                            newResult as? UpdateResult.UpdateAvailable
                        }

                        if (result != null) {
                            startDownload(design, result)
                        } else {
                            design.showError(
                                getString(com.github.kr328.clash.design.R.string.update_check_failed)
                            )
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
                        when (val result = cachedResult) {
                            is UpdateResult.UpdateAvailable -> openUrl(result.releaseInfo.htmlUrl)
                            is UpdateResult.NoApkFound -> openUrl(result.releaseInfo.htmlUrl)
                            else -> {
                                val newResult = withContext(Dispatchers.IO) {
                                    updateManager.checkForUpdate(
                                        currentVersionCode,
                                        includePrerelease = false
                                    )
                                }
                                cachedResult = newResult
                                when (newResult) {
                                    is UpdateResult.UpdateAvailable ->
                                        openUrl(newResult.releaseInfo.htmlUrl)
                                    is UpdateResult.NoApkFound ->
                                        openUrl(newResult.releaseInfo.htmlUrl)
                                    else -> {}
                                }
                            }
                        }
                    }
                    UpdateDesign.Request.Cancel -> {
                        downloadMonitorJob?.cancel()
                        finish()
                        return
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                design.showError(
                    e.message ?: getString(com.github.kr328.clash.design.R.string.unknown_error)
                )
            }
        }
    }

    private fun CoroutineScope.startDownload(
        design: UpdateDesign,
        updateInfo: UpdateResult.UpdateAvailable
    ) {
        downloadMonitorJob?.cancel()

        downloadMonitorJob = launch {
            try {
                design.setDownloading(true)

                currentDownloadId = updateManager.startDownload(
                    downloadUrl = updateInfo.downloadUrl,
                    fileName = updateInfo.fileName,
                    title = getString(com.github.kr328.clash.design.R.string.downloading_update)
                )

                while (isActive) {
                    val progress = withContext(Dispatchers.IO) {
                        updateManager.getDownloadProgress(currentDownloadId)
                    }
                    when (progress) {
                        is DownloadProgress.Running -> {
                            design.setDownloadProgress(
                                progress.progress,
                                progress.bytesDownloaded,
                                progress.bytesTotal
                            )
                        }
                        is DownloadProgress.Completed -> {
                            design.setDownloadComplete()
                            return@launch
                        }
                        is DownloadProgress.Failed -> {
                            design.setDownloading(false)
                            design.showError(
                                getString(com.github.kr328.clash.design.R.string.download_failed)
                            )
                            return@launch
                        }
                        is DownloadProgress.Pending -> { /* 等待 */ }
                        is DownloadProgress.Paused -> { /* 暂停中 */ }
                        is DownloadProgress.Unknown -> { /* 未知 */ }
                    }
                    delay(500)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                design.setDownloading(false)
                design.showError(
                    e.message ?: getString(com.github.kr328.clash.design.R.string.unknown_error)
                )
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
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) { }
    }
}
