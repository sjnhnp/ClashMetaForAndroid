package com.github.kr328.clash

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

class DialerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 恢复应用图标 (如果之前被隐藏)
        restoreAppIcon(context)
        
        // 启动主界面
        val launchIntent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
    }
    
    private fun restoreAppIcon(context: Context) {
        val aliasComponentName = ComponentName(context, mainActivityAlias)
        val currentState = context.packageManager.getComponentEnabledSetting(aliasComponentName)
        
        // 如果图标被隐藏（组件被禁用），则恢复它
        if (currentState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            context.packageManager.setComponentEnabledSetting(
                aliasComponentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}