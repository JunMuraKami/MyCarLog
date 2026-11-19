package jp.mycarlog.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fires from the daily alarm, and re-arms the alarm after reboot or app update. */
public class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            Reminder.schedule(c);
        } else {
            Reminder.check(c);
        }
    }
}
