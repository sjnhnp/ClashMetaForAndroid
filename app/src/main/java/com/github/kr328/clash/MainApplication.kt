package com.github.kr328.clash

import android.app.Application
import android.content.Context
import com.github.kr328.clash.common.Global
import com.github.kr328.clash.common.compat.currentProcessName
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.service.util.sendServiceRecreated
import com.github.kr328.clash.util.clashDir
import java.io.File
import java.io.FileOutputStream


@Suppress("unused")
class MainApplication : Application() {
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
        } else {
            sendServiceRecreated()
        }
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
        extractAssetIfExists("ASN.mmdb", File(clashDir, "ASN.mmdb"))
    }

    fun finalize() {
        Global.destroy()
    }
}
