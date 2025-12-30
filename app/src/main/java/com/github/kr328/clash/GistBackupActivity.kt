package com.github.kr328.clash

import android.app.AlertDialog
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
        withContext(Dispatchers.Main) {
            withModelProgressBar {
                configure {
                    isIndeterminate = true
                    text = getString(R.string.backup_in_progress)
                }
                
                val result = backupManager.createBackup { message: String ->
                    launch(Dispatchers.Main) {
                        configure { text = message }
                    }
                }
                
                when (result) {
                    is GistBackupManager.Result.Success<*> -> {
                        design.showSuccess(getString(R.string.backup_success))
                    }
                    is GistBackupManager.Result.Error -> {
                        design.showError(result.message)
                    }
                }
            }
        }
    }
    
    private suspend fun showRestoreDialog(design: GistBackupDesign) {
        val backupsResult = withContext(Dispatchers.Main) {
            withModelProgressBar {
                configure {
                    isIndeterminate = true
                    text = getString(R.string.loading_backups)
                }
                backupManager.listBackups()
            }
        }
        
        when (backupsResult) {
            is GistBackupManager.Result.Success<*> -> {
                @Suppress("UNCHECKED_CAST")
                val backups = backupsResult.data as List<GistBackupManager.BackupInfo>
                if (backups.isEmpty()) {
                    design.showError(getString(R.string.no_backups_found))
                    return
                }
                
                val items = backups.map { it.description }.toTypedArray()
                
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(this@GistBackupActivity)
                        .setTitle(R.string.select_backup_to_restore)
                        .setItems(items) { _, which ->
                            val selectedBackup = backups[which]
                            launch {
                                performRestore(design, selectedBackup.id)
                            }
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
            }
            is GistBackupManager.Result.Error -> {
                design.showError(backupsResult.message)
            }
        }
    }
    
    private suspend fun performRestore(design: GistBackupDesign, gistId: String) {
        withContext(Dispatchers.Main) {
            AlertDialog.Builder(this@GistBackupActivity)
                .setTitle(R.string.restore_warning_title)
                .setMessage(R.string.restore_warning_message)
                .setPositiveButton(R.string.restore) { _, _ ->
                    launch {
                        doRestore(design, gistId)
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }
    
    private suspend fun doRestore(design: GistBackupDesign, gistId: String) {
        withContext(Dispatchers.Main) {
            withModelProgressBar {
                configure {
                    isIndeterminate = true
                    text = getString(R.string.restore_in_progress)
                }
                
                val result = backupManager.restoreBackup(gistId) { message: String ->
                    launch(Dispatchers.Main) {
                        configure { text = message }
                    }
                }
                
                when (result) {
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
                        design.showError(result.message)
                    }
                }
            }
        }
    }
    
    private suspend fun showManageBackupsDialog(design: GistBackupDesign) {
        val backupsResult = withContext(Dispatchers.Main) {
            withModelProgressBar {
                configure {
                    isIndeterminate = true
                    text = getString(R.string.loading_backups)
                }
                backupManager.listBackups()
            }
        }
        
        when (backupsResult) {
            is GistBackupManager.Result.Success<*> -> {
                @Suppress("UNCHECKED_CAST")
                val backups = backupsResult.data as List<GistBackupManager.BackupInfo>
                if (backups.isEmpty()) {
                    design.showError(getString(R.string.no_backups_found))
                    return
                }
                
                val items = backups.map { it.description }.toTypedArray()
                val selected = BooleanArray(items.size)
                
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(this@GistBackupActivity)
                        .setTitle(R.string.select_backups_to_delete)
                        .setMultiChoiceItems(items, selected) { _, which: Int, isChecked: Boolean ->
                            selected[which] = isChecked
                        }
                        .setPositiveButton(R.string.delete) { _, _ ->
                            val toDelete = backups.filterIndexed { index: Int, _: GistBackupManager.BackupInfo -> selected[index] }
                            if (toDelete.isNotEmpty()) {
                                launch {
                                    deleteBackups(design, toDelete)
                                }
                            }
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
            }
            is GistBackupManager.Result.Error -> {
                design.showError(backupsResult.message)
            }
        }
    }
    
    private suspend fun deleteBackups(
        design: GistBackupDesign,
        backups: List<GistBackupManager.BackupInfo>
    ) {
        withContext(Dispatchers.Main) {
            withModelProgressBar {
                configure {
                    isIndeterminate = false
                    max = backups.size
                    text = getString(R.string.deleting_backups)
                }
                
                var successCount = 0
                var failCount = 0
                
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
                
                if (failCount == 0) {
                    design.showSuccess(getString(R.string.deleted_n_backups, successCount))
                } else {
                    design.showError(getString(R.string.deleted_with_errors, successCount, failCount))
                }
            }
        }
    }
}
