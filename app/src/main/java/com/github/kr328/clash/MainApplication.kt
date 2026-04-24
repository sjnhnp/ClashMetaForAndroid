package com.github.kr328.clash

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.github.kr328.clash.common.Global
import com.github.kr328.clash.common.compat.currentProcessName
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.service.util.sendServiceRecreated
import com.github.kr328.clash.util.clashDir
import java.io.File
import java.io.FileOutputStream
import com.github.kr328.clash.design.R as DesignR


@Suppress("unused")
class MainApplication : Application() {
    private val uiStore by lazy(LazyThreadSafetyMode.NONE) { UiStore(this) }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)

        Global.init(this)
    }

    override fun onCreate() {
        super.onCreate()

        val processName = currentProcessName
        extractGeoFiles()

        Log.d("Process $processName started")

        if (processName == packageName) {
            Remote.launch()
            setupShortcuts()
        } else {
            sendServiceRecreated()
        }
    }

    private fun setupShortcuts() {
        if (uiStore.hideAppIcon) {
            // Prevent launcher activity not found.
            ShortcutManagerCompat.removeAllDynamicShortcuts(this)
            return
        }

        val icon = IconCompat.createWithResource(this, R.mipmap.ic_launcher)
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
            Intent.FLAG_ACTIVITY_NO_ANIMATION

        val toggle = ShortcutInfoCompat.Builder(this, "toggle_clash")
            .setShortLabel(getString(DesignR.string.shortcut_toggle_short))
            .setLongLabel(getString(DesignR.string.shortcut_toggle_long))
            .setIcon(icon)
            .setIntent(
                Intent(Intents.ACTION_TOGGLE_CLASH)
                    .setClassName(this, ExternalControlActivity::class.java.name)
                    .addFlags(flags)
            )
            .setRank(0)
            .build()

        val start = ShortcutInfoCompat.Builder(this, "start_clash")
            .setShortLabel(getString(DesignR.string.shortcut_start_short))
            .setLongLabel(getString(DesignR.string.shortcut_start_long))
            .setIcon(icon)
            .setIntent(
                Intent(Intents.ACTION_START_CLASH)
                    .setClassName(this, ExternalControlActivity::class.java.name)
                    .addFlags(flags)
            )
            .setRank(1)
            .build()

        val stop = ShortcutInfoCompat.Builder(this, "stop_clash")
            .setShortLabel(getString(DesignR.string.shortcut_stop_short))
            .setLongLabel(getString(DesignR.string.shortcut_stop_long))
            .setIcon(icon)
            .setIntent(
                Intent(Intents.ACTION_STOP_CLASH)
                    .setClassName(this, ExternalControlActivity::class.java.name)
                    .addFlags(flags)
            )
            .setRank(2)
            .build()

        ShortcutManagerCompat.setDynamicShortcuts(this, listOf(toggle, start, stop))
    }

    private fun extractGeoFiles() {
        clashDir.mkdirs()

        val updateDate = packageManager.getPackageInfo(packageName, 0).lastUpdateTime
        
        // Helper function to safely extract asset file if it exists in APK
        fun extractAssetIfExists(assetName: String, targetFile: File) {
            try {
                if (targetFile.exists() && targetFile.lastModified() < updateDate) {
                    targetFile.delete()
                }
                if (!targetFile.exists()) {
                    assets.open(assetName).use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d("Extracted $assetName to ${targetFile.path}")
                }
            } catch (e: java.io.FileNotFoundException) {
                // Asset not bundled in APK, user needs to download it manually
                Log.d("Asset $assetName not found in APK, skipping extraction")
            } catch (e: Exception) {
                Log.w("Failed to extract $assetName: ${e.message}")
            }
        }
        
        extractAssetIfExists("geoip.metadb", File(clashDir, "geoip.metadb"))
        extractAssetIfExists("geosite.dat", File(clashDir, "geosite.dat"))
        // ASN.mmdb removed to reduce APK size
    }

    fun finalize() {
        Global.destroy()
    }
}
