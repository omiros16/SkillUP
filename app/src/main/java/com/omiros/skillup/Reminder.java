package com.omiros.skillup;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * One evening reminder, only when something is still due today. Each firing arms the next day's.
 * Also receives boot / app update / clock changes, which wipe or shift alarms.
 */
public class Reminder extends BroadcastReceiver {
    private static final String ACTION_FIRE = "com.omiros.skillup.REMIND";
    private static final String CHANNEL = "evening";

    @Override
    public void onReceive(Context c, Intent intent) {
        if (ACTION_FIRE.equals(intent.getAction()) && Store.remindOn(c)) notifyIfDue(c);
        schedule(c);
    }

    static void schedule(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = PendingIntent.getBroadcast(c, 0, new Intent(c, Reminder.class).setAction(ACTION_FIRE),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        if (!Store.remindOn(c)) return;
        int at = Store.remindAt(c);
        LocalDateTime when = LocalDate.now().atTime(at / 60, at % 60);
        if (!when.isAfter(LocalDateTime.now())) when = when.plusDays(1);
        // Inexact on purpose: no exact-alarm permission needed; a few minutes late is fine for an evening nudge.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pi);
    }

    private static void notifyIfDue(Context c) {
        LocalDate today = LocalDate.now();
        List<String> due = new ArrayList<>();
        for (Engine.Task t : Store.load(c)) {
            if (Engine.due(t, Engine.compute(t, today), today)) due.add(t.name);
        }
        if (due.isEmpty()) return;

        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Βραδινή υπενθύμιση", NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("Όταν έχεις ακόμα tasks για σήμερα");
        nm.createNotificationChannel(ch);

        PendingIntent open = PendingIntent.getActivity(c, 0,
                new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String title = due.size() == 1 ? "Σου μένει 1 για σήμερα" : "Σου μένουν " + due.size() + " για σήμερα";
        nm.notify(1, new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(Ui.LAVENDER)
                .setContentTitle(title)
                .setContentText(android.text.TextUtils.join(", ", due) + ". Έστω και barely.")
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build());
    }
}
