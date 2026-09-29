package com.example.pixelclock.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * One receiver, two registrations:
 *  - DYNAMIC (by ClockTickService): ACTION_TIME_TICK, SCREEN_ON, USER_PRESENT, TIMEZONE_CHANGED, TIME_SET
 *    (ACTION_TIME_TICK can NEVER be declared in the manifest – the OS only delivers it to runtime receivers.)
 *  - MANIFEST: TIMEZONE_CHANGED, TIME_SET, BOOT_COMPLETED, MY_PACKAGE_REPLACED
 */
class TimeTickReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> ClockTickService.startSafely(app)
        }
        val pending = goAsync()
        scope.launch {
            try {
                val widgets = ClockRefresher.refreshAll(app)
                if (widgets == 0) app.stopService(Intent(app, ClockTickService::class.java)) // nothing to update ⇒ no FGS
            } catch (t: Throwable) {
                Log.e("TimeTickReceiver", "refresh failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
