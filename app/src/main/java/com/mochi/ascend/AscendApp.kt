package com.mochi.ascend

import android.app.Application
import com.mochi.ascend.data.Store
import com.mochi.ascend.notify.Reminders

class AscendApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Reminders.ensureChannel(this)
        // AlarmManager calls are binder work; never on the launch path.
        Store.inBackground { Reminders.rescheduleAll(this) }
    }
}