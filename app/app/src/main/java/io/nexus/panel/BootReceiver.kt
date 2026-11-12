package io.nexus.panel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.nexus.panel.service.NexusService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            NexusService.start(context)
            // Bring up the kiosk UI too — the service alone leaves a black screen.
            // (Background activity starts are still allowed on Android 8.1.)
            try {
                val i = Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
            } catch (_: Exception) {}
        }
    }
}
