package com.mochi.ascend

import android.app.Application
import com.mochi.ascend.data.AutoBackup
import com.mochi.ascend.data.Store
import com.mochi.ascend.data.WeeklyRoll
import com.mochi.ascend.notify.Reminders

class AscendApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)

        // Weekly rollover and the opt-in snapshot both run off the launch path.
        Store.inBackground {
            try {
                WeeklyRoll.apply()
            } catch (_: Throwable) {
                // Rollover is a convenience; never block launch on it.
            }
            AutoBackup.run(this)
        }

        Reminders.ensureChannel(this)
        // AlarmManager calls are binder work; never on the launch path.
        Store.inBackground { Reminders.rescheduleAll(this) }
    }
}