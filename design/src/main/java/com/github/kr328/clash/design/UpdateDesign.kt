package com.github.kr328.clash.design

import android.content.Context
import android.text.format.Formatter
import android.view.View
import com.github.kr328.clash.design.databinding.DesignUpdateBinding
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.bindAppBarElevation
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdateDesign(context: Context) : Design<UpdateDesign.Request>(context) {
    
    enum class Request {
        Download,
        Install,
        OpenInBrowser,
        Cancel
    }
    
    private val binding = DesignUpdateBinding
        .inflate(context.layoutInflater, context.root, false)
    
    override val root: View
        get() = binding.root
    
    init {
        binding.self = this
        
        binding.activityBarLayout.applyFrom(context)
        binding.activityBarLayout.title = context.getString(R.string.check_update)
        
        binding.scrollRoot.bindAppBarElevation(binding.activityBarLayout)
        
        binding.activityBarLayout.findViewById<View>(R.id.activity_bar_close_view).setOnClickListener {
            requests.trySend(Request.Cancel)
        }
    }
    
    suspend fun setChecking(checking: Boolean) = withContext(Dispatchers.Main) {
        binding.checking = checking
        binding.hasUpdate = false
        binding.noUpdate = false
        binding.downloading = false
        binding.downloadComplete = false
        binding.hasError = false
    }
    
    suspend fun showUpdateAvailable(versionName: String, changelog: String, fileSize: Long) = withContext(Dispatchers.Main) {
        binding.checking = false
        binding.hasUpdate = true
        binding.noUpdate = false
        binding.versionName = versionName
        binding.changelog = changelog.ifBlank { context.getString(R.string.no_changelog) }
        binding.fileSize = Formatter.formatFileSize(context, fileSize)
    }
    
    suspend fun showNoUpdate() = withContext(Dispatchers.Main) {
        binding.checking = false
        binding.hasUpdate = false
        binding.noUpdate = true
    }
    
    suspend fun showError(message: String) = withContext(Dispatchers.Main) {
        binding.checking = false
        binding.hasError = true
        binding.errorMessage = message
    }
    
    suspend fun setDownloading(downloading: Boolean) = withContext(Dispatchers.Main) {
        binding.downloading = downloading
        binding.downloadProgress = 0f
    }
    
    suspend fun setDownloadProgress(progress: Float, bytesDownloaded: Long, bytesTotal: Long) = withContext(Dispatchers.Main) {
        binding.downloadProgress = progress
        binding.downloadProgressText = "${Formatter.formatFileSize(context, bytesDownloaded)} / ${Formatter.formatFileSize(context, bytesTotal)}"
    }
    
    suspend fun setDownloadComplete() = withContext(Dispatchers.Main) {
        binding.downloading = false
        binding.downloadComplete = true
    }
    
    fun request(request: Request) {
        requests.trySend(request)
    }
}
