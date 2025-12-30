package com.github.kr328.clash

import android.app.AlertDialog
import android.util.Log
import com.github.kr328.clash.design.GistBackupDesign
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.dialog.withModelProgressBar
import com.github.kr328.clash.service.gist.GistBackupManager
import com.github.kr328.clash.service.gist.GistStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class GistBackupActivity : BaseActivity<GistBackupDesign>() {
    
    private val gistStore by lazy { GistStore(this) }
    private val backupManager by lazy { GistBackupManager(this) }
    
    override suspend fun main() {
        val design = GistBackupDesign(this, gistStore)
        

setContentDesign(design)

        while (isActive) {
            select<Unit> {
                events.onReceive { }
                design.requests.onReceive { request ->
                    when (request) {
                        GistBackupDesign.Request.Backup -> performBackup(design)
                        GistBackupDesign.Request.Restore -> showRestoreDialog(design)
                        GistBackupDesign.Request.ManageBackups -> showManageBackupsDialog(design)
                    }
                }
            }
        }
    }
    
    private suspend fun performBackup(design: GistBackupDesign) {

        var result: GistBackupManager.Result<String>? = null
        
        withContext(Dispatchers.Main) {
            withModelProgressBar {
                configure {
                    isIndeterminate = true
                    text = getString(R.string.backup_in_progress)
                }
                
                result = backupManager.createBackup { message: String ->
                    launch(Dispatchers.Main) {
                        configure { text = message }
                    }
                }
            }
        }
        
        withContext(Dispatchers.Main) {
            val finalResult = result
            if (finalResult == null) {
                design.showError(getString(R.string.unknown_error))
                return@withContext
            }

            when (finalResult) {
                is GistBackupManager.Result.Success<*> -> {
                    design.showSuccess(getString(R.string.backup_success))
                }
                is GistBackupManager.Result.Error -> {
                    design.showError(finalResult.message)
                }
            }
        }
    }
    
    private suspend fun showRestoreDialog(design: GistBackupDesign) {
        Log.d("GistBackup", "showRestoreDialog called")
        
        var backupsResult: GistBackupManager.Result<List<GistBackupManager.BackupInfo>>? = null

        withContext(Dispatchers.Main) {
            withModelProgressBar {
                configure {
                    isIndeterminate = true
                    text = getString(R.string.loading_backups)
                }
                backupsResult = backupManager.listBackups()
            }
        }
        
        Log.d("GistBackup", "listBackups result: $backupsResult")
        
        val finalResult = backupsResult
        if (finalResult == null) {
            design.showError(getString(R.string.unknown_error))
            return
        }

        when (finalResult) {
            is GistBackupManager.Result.Success<*> -> {
                @Suppress("UNCHECKED_CAST")
                val backups = finalResult.data as? List<GistBackupManager.BackupInfo> ?: emptyList()
                Log.d("GistBackup", "Found ${backups.size} backups")
                
                if (backups.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        AlertDialog.Builder(this@GistBackupActivity)
                            .setTitle(R.string.gist_backup)
                            .setMessage(R.string.no_backups_found)
                            .setPositiveButton(R.string.ok, null)
                            .show()
                    }
                    return
                }
                
                val items = backups.map { it.description }.toTypedArray()
                
                val selectedBackup = withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine<GistBackupManager.BackupInfo?> { cont ->
                         val dialog = AlertDialog.Builder(this@GistBackupActivity)
                            .setTitle(R.string.select_backup_to_restore)
                            .setItems(items) { _, which ->
                                cont.resume(backups[which])
                            }
                            .setNegativeButton(R.string.cancel) { _, _ -> cont.resume(null) }
                            .setOnCancelListener { cont.resume(null) }
                            .show()
                         
                         cont.invokeOnCancellation { dialog.dismiss() }
                    }
                }
                
                if (selectedBackup != null) {
                    performRestore(design, selectedBackup.id)
                }
            }
            is GistBackupManager.Result.Error -> {
                Log.e("GistBackup", "Error: ${finalResult.message}")
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(this@GistBackupActivity)
                        .setTitle(R.string.error)
                        .setMessage(finalResult.message)
                        .setPositiveButton(R.string.ok, null)
                        .show()
                }
            }
        }
    }
    
    private suspend fun performRestore(design: GistBackupDesign, gistId: String) {
        val confirmed = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<Boolean> { cont ->
                val dialog = AlertDialog.Builder(this@GistBackupActivity)
                    .setTitle(R.string.restore_warning_title)
                    .setMessage(R.string.restore_warning_message)
                    .setPositiveButton(R.string.restore) { _, _ -> cont.resume(true) }
                    .setNegativeButton(R.string.cancel) { _, _ -> cont.resume(false) }
                    .setOnCancelListener { cont.resume(false) }
                    .show()
                cont.invokeOnCancellation { dialog.dismiss() }
            }
        }
        
        if (confirmed) {
            doRestore(design, gistId)
        }
    }
    
    private suspend fun doRestore(design: GistBackupDesign, gistId: String) {
        var result: GistBackupManager.Result<Unit>? = null

        withContext(Dispatchers.Main) {
            withModelProgressBar {
                configure {
                    isIndeterminate = true
                    text = getString(R.string.restore_in_progress)
                }
                
                result = backupManager.restoreBackup(gistId) { message: String ->
                    launch(Dispatchers.Main) {
                        configure { text = message }
                    }
                }
            }
        }
        
        withContext(Dispatchers.Main) {
            val finalResult = result
            if (finalResult == null) {
                design.showError(getString(R.string.unknown_error))
                return@withContext
            }

            when (finalResult) {
                is GistBackupManager.Result.Success<*> -> {
                    AlertDialog.Builder(this@GistBackupActivity)
                        .setTitle(R.string.restore_success)
                        .setMessage(R.string.restart_app_message)
                        .setPositiveButton(R.string.ok) { _, _ ->
                            finishAffinity()
                        }
                        .setCancelable(false)
                        .show()
                }
                is GistBackupManager.Result.Error -> {
                    AlertDialog.Builder(this@GistBackupActivity)
                        .setTitle(R.string.error)
                        .setMessage(finalResult.message)
                        .setPositiveButton(R.string.ok, null)
                        .show()
                }
            }
        }
    }
    
    private suspend fun showManageBackupsDialog(design: GistBackupDesign) {
        Log.d("GistBackup", "showManageBackupsDialog called")
        
        var backupsResult: GistBackupManager.Result<List<GistBackupManager.BackupInfo>>? = null

        withContext(Dispatchers.Main) {
            withModelProgressBar {
                configure {
                    isIndeterminate = true
                    text = getString(R.string.loading_backups)
                }
                backupsResult = backupManager.listBackups()
            }
        }
        
        Log.d("GistBackup", "listBackups result: $backupsResult")
        
        val finalResult = backupsResult
        if (finalResult == null) {
            design.showError(getString(R.string.unknown_error))
            return
        }

        when (finalResult) {
            is GistBackupManager.Result.Success<*> -> {
                @Suppress("UNCHECKED_CAST")
                val backups = finalResult.data as? List<GistBackupManager.BackupInfo> ?: emptyList()
                Log.d("GistBackup", "Found ${backups.size} backups for management")
                
                if (backups.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        AlertDialog.Builder(this@GistBackupActivity)
                            .setTitle(R.string.gist_backup)
                            .setMessage(R.string.no_backups_found)
                            .setPositiveButton(R.string.ok, null)
                            .show()
                    }
                    return
                }
                
                val items = backups.map { it.description }.toTypedArray()
                val selected = BooleanArray(items.size)
                
                val toDelete = withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine<List<GistBackupManager.BackupInfo>?> { cont ->
                        val dialog = AlertDialog.Builder(this@GistBackupActivity)
                            .setTitle(R.string.select_backups_to_delete)
                            .setMultiChoiceItems(items, selected) { _, which, isChecked ->
                                selected[which] = isChecked
                            }
                            .setPositiveButton(R.string.delete) { _, _ ->
                                val list = backups.filterIndexed { index: Int, _: GistBackupManager.BackupInfo -> selected[index] }
                                cont.resume(list)
                            }
                            .setNegativeButton(R.string.cancel) { _, _ -> cont.resume(null) }
                            .setOnCancelListener { cont.resume(null) }
                            .show()
                        
                        cont.invokeOnCancellation { dialog.dismiss() }
                    }
                }
                
                if (!toDelete.isNullOrEmpty()) {
                    deleteBackups(design, toDelete)
                }
            }
            is GistBackupManager.Result.Error -> {
                Log.e("GistBackup", "Error: ${finalResult.message}")
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(this@GistBackupActivity)
                        .setTitle(R.string.error)
                        .setMessage(finalResult.message)
                        .setPositiveButton(R.string.ok, null)
                        .show()
                }
            }
        }
    }
    
    private suspend fun deleteBackups(
        design: GistBackupDesign,
        backups: List<GistBackupManager.BackupInfo>
    ) {
        var successCount = 0
        var failCount = 0
        
        withContext(Dispatchers.Main) {
            withModelProgressBar {
                configure {
                    isIndeterminate = false
                    max = backups.size
                    text = getString(R.string.deleting_backups)
                }
                
                backups.forEachIndexed { index: Int, backup: GistBackupManager.BackupInfo ->
                    configure { 
                        progress = index
                        text = backup.description 
                    }
                    
                    when (backupManager.deleteBackup(backup.id)) {
                        is GistBackupManager.Result.Success<*> -> successCount++
                        is GistBackupManager.Result.Error -> failCount++
                    }
                }
            }
        }
        
        withContext(Dispatchers.Main) {
            val message = if (failCount == 0) {
                getString(R.string.deleted_n_backups, successCount)
            } else {
                getString(R.string.deleted_with_errors, successCount, failCount)
            }
            
            AlertDialog.Builder(this@GistBackupActivity)
                .setTitle(R.string.gist_backup)
                .setMessage(message)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }
}
