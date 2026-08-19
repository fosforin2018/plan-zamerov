package com.zamerplan.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zamerplan.app.model.Storage

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val s = SettingsStore(ctx)
            ReminderScheduler.scheduleAll(ctx, Storage(ctx).load(), s)
        }
    }
}
