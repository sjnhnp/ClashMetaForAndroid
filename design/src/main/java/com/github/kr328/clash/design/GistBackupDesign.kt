package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import com.github.kr328.clash.design.databinding.DesignSettingsCommonBinding
import com.github.kr328.clash.design.dialog.requestModelTextInput
import com.github.kr328.clash.design.preference.*
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.bindAppBarElevation
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.service.gist.GistStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GistBackupDesign(
    context: Context,
    private val gistStore: GistStore,
) : Design<GistBackupDesign.Request>(context) {
    
    enum class Request {
        Backup,
        Restore,
        ManageBackups,
    }
    
    private val binding = DesignSettingsCommonBinding
        .inflate(context.layoutInflater, context.root, false)
    
    override val root: View
        get() = binding.root
    
    // UI state for token/secret display
    private var tokenStatus: ClickablePreference? = null
    private var secretStatus: ClickablePreference? = null
    
    init {
        binding.surface = surface
        
        binding.activityBarLayout.applyFrom(context)
        
        binding.scrollRoot.bindAppBarElevation(binding.activityBarLayout)
        
        val screen = preferenceScreen(context) {
            category(R.string.gist_credentials)
            
            tokenStatus = clickable(
                title = R.string.github_token,
                summary = if (gistStore.githubToken != null) R.string.configured else R.string.not_configured
            ) {
                clicked {
                    launch {
                        val token = context.requestModelTextInput(
                            initial = gistStore.githubToken ?: "",
                            title = context.getString(R.string.github_token),
                            hint = context.getString(R.string.github_token_hint),
                            reset = context.getString(R.string.reset),
                        )
                        gistStore.githubToken = token?.takeIf { it.isNotBlank() }
                        updateCredentialStatus()
                    }
                }
            }
            
            secretStatus = clickable(
                title = R.string.encryption_secret,
                summary = if (gistStore.encryptionSecret != null) R.string.configured else R.string.not_configured
            ) {
                clicked {
                    launch {
                        val secret = context.requestModelTextInput(
                            initial = gistStore.encryptionSecret ?: "",
                            title = context.getString(R.string.encryption_secret),
                            hint = context.getString(R.string.encryption_secret_hint),
                            reset = context.getString(R.string.reset),
                        )
                        gistStore.encryptionSecret = secret?.takeIf { it.isNotBlank() }
                        updateCredentialStatus()
                    }
                }
            }
            
            tips(R.string.gist_credentials_tips)
            
            category(R.string.backup_operations)
            
            clickable(
                title = R.string.backup_now,
                summary = R.string.backup_now_summary,
                icon = R.drawable.ic_baseline_cloud_download
            ) {
                clicked {
                    if (!gistStore.isConfigured) {
                        launch { showToast(R.string.please_configure_credentials, ToastDuration.Long) }
                        return@clicked
                    }
                    requests.trySend(Request.Backup)
                }
            }
            
            clickable(
                title = R.string.restore_from_gist,
                summary = R.string.restore_from_gist_summary,
                icon = R.drawable.ic_baseline_restore
            ) {
                clicked {
                    if (!gistStore.isConfigured) {
                        launch { showToast(R.string.please_configure_credentials, ToastDuration.Long) }
                        return@clicked
                    }
                    requests.trySend(Request.Restore)
                }
            }
            
            clickable(
                title = R.string.manage_backups,
                summary = R.string.manage_backups_summary,
                icon = R.drawable.ic_outline_delete
            ) {
                clicked {
                    if (gistStore.githubToken == null) {
                        launch { showToast(R.string.please_configure_token, ToastDuration.Long) }
                        return@clicked
                    }
                    requests.trySend(Request.ManageBackups)
                }
            }
            
            tips(R.string.gist_backup_tips)
        }
        
        binding.content.addView(screen.root)
    }
    
    private fun updateCredentialStatus() {
        tokenStatus?.summary = context.getString(
            if (gistStore.githubToken != null) R.string.configured else R.string.not_configured
        )
        secretStatus?.summary = context.getString(
            if (gistStore.encryptionSecret != null) R.string.configured else R.string.not_configured
        )
    }
    
    suspend fun showProgress(message: String) {
        withContext(Dispatchers.Main) {
            showToast(message, ToastDuration.Short)
        }
    }
    
    suspend fun showSuccess(message: String) {
        withContext(Dispatchers.Main) {
            showToast(message, ToastDuration.Long)
        }
    }
    
    suspend fun showError(message: String) {
        withContext(Dispatchers.Main) {
            showToast(message, ToastDuration.Long)
        }
    }
}
