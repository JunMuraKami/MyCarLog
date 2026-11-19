package jp.mycarlog.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Daily check that posts a notification for maintenance that is due soon or overdue. */
public final class Reminder {
    private static final String CHANNEL = "maintenance";
    private static final int NOTIFY_ID = 1;

    private Reminder() { }

    /** Schedule a daily check around 9:00. Safe to call repeatedly. */
    public static void schedule(Context c) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 9);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_MONTH, 1);
        PendingIntent pi = PendingIntent.getBroadcast(c, 0,
                new Intent(c, ReminderReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = c.getSystemService(AlarmManager.class);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY, pi);
    }

    /** Post (or clear) the reminder notification. Returns the number of items due. */
    public static int check(Context c) {
        Store store = new Store(c);
        List<String> lines = new ArrayList<>();
        for (Store.Due d : store.alerts(LocalDate.now()))
            lines.add("[" + d.vehicle.name + "] [" + d.s.label() + "] "
                    + (d.level == 2 ? "の期限を過ぎています" : "が近づいています"));
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (lines.isEmpty()) {
            nm.cancel(NOTIFY_ID);
            return 0;
        }
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "整備の予定",
                NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent open = PendingIntent.getActivity(c, 0,
                new Intent(c, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.InboxStyle style = new Notification.InboxStyle();
        for (String l : lines) style.addLine(l);
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("整備の時期です(" + lines.size() + "件)")
                .setContentText(lines.get(0))
                .setStyle(style)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build();
        try {
            nm.notify(NOTIFY_ID, n);
        } catch (SecurityException ignored) { } // notification permission denied
        return lines.size();
    }
}
