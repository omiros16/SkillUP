package com.omiros.skillup;

import static com.omiros.skillup.Ui.*;

import android.Manifest;
import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TimePicker;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int REQ_EXPORT = 1, REQ_IMPORT = 2, REQ_NOTIF = 3;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEEE d MMMM", new Locale("el", "GR"));

    private List<Engine.Task> tasks;
    private LocalDate today;
    private boolean showYesterday;
    /** XP bar start points for the next render, so a change animates from where it was. */
    private final Map<Long, Float> animFrom = new HashMap<>();

    private TextView dateView;
    private TextView summary;
    private TextView todayChip;
    private TextView yesterdayChip;
    private LinearLayout dayBar;
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        showYesterday = state != null && state.getBoolean("yesterday");
        setContentView(buildUi());
        if (state == null && Store.remindOn(this) && !hasNotificationPermission() && !Store.askedPermission(this)) {
            Store.setAskedPermission(this);
            requestNotificationPermission();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        tasks = Store.load(this);
        render();
        Reminder.schedule(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("yesterday", showYesterday);
    }

    // ---------------------------------------------------------------- layout

    private View buildUi() {
        LinearLayout root = column(this);
        root.setBackgroundColor(BLACK);

        LinearLayout header = row(this);
        header.setPadding(dp(this, 20), dp(this, 14), dp(this, 6), 0);
        LinearLayout titles = column(this);
        TextView title = text(this, 30, TEXT, NUM);
        title.setText("SkillUP");
        dateView = text(this, 14, DIM, null);
        titles.addView(title);
        titles.addView(dateView);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));

        ImageView add = iconButton(this, R.drawable.ic_add, "Νέο task");
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                newTask();
            }
        });
        header.addView(add);
        final ImageView more = iconButton(this, R.drawable.ic_more, "Μενού");
        more.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu(more);
            }
        });
        header.addView(more);
        root.addView(header);

        LinearLayout days = dayBar = row(this);
        days.setPadding(dp(this, 20), dp(this, 14), dp(this, 20), dp(this, 6));
        todayChip = chip("Σήμερα", false);
        yesterdayChip = chip("Χθες", true);
        days.addView(todayChip);
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(-2, -2);
        gap.leftMargin = dp(this, 8);
        days.addView(yesterdayChip, gap);
        summary = text(this, 14, DIM, null);
        summary.setGravity(Gravity.END);
        days.addView(summary, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(days);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        list = column(this);
        list.setPadding(dp(this, 20), 0, dp(this, 20), dp(this, 40));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        return root;
    }

    private TextView chip(String label, final boolean yesterday) {
        TextView c = text(this, 14, TEXT, MEDIUM);
        c.setText(label);
        c.setGravity(Gravity.CENTER);
        c.setMinHeight(dp(this, 36));
        c.setPadding(dp(this, 16), 0, dp(this, 16), 0);
        c.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showYesterday = yesterday;
                render();
            }
        });
        return c;
    }

    private void render() {
        today = LocalDate.now();
        LocalDate date = showYesterday ? today.minusDays(1) : today;
        String d = date.format(DATE_FMT);
        dateView.setText(Character.toUpperCase(d.charAt(0)) + d.substring(1));
        for (TextView c : new TextView[]{todayChip, yesterdayChip}) {
            boolean on = (c == yesterdayChip) == showYesterday;
            c.setBackground(ripple(on ? BARELY_BG : 0, on ? LAVENDER : FAINT, dp(this, 18)));
            c.setTextColor(on ? TEXT : DIM);
            c.setSelected(on);
        }

        list.removeAllViews();
        dayBar.setVisibility(tasks.isEmpty() ? View.GONE : View.VISIBLE);
        if (tasks.isEmpty()) {
            list.addView(emptyState());
            return;
        }

        int due = 0, shown = 0;
        for (Engine.Task t : tasks) {
            if (t.created().isAfter(date)) continue;
            Engine.Result r = Engine.compute(t, today);
            if (Engine.due(t, r, today)) due++;
            if (shown++ > 0) list.addView(divider());
            list.addView(taskRow(t, date, r));
        }
        if (shown == 0) {
            TextView none = text(this, 15, DIM, null);
            none.setText("Χθες δεν είχες ακόμα tasks.");
            none.setPadding(0, dp(this, 32), 0, 0);
            list.addView(none);
        }
        summary.setText(showYesterday ? "" : due == 1 ? "Μένει 1" : due > 1 ? "Μένουν " + due : "Τελείωσες για σήμερα");
    }

    private View taskRow(final Engine.Task t, LocalDate date, Engine.Result r) {
        LinearLayout row = row(this);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, dp(this, 18), 0, dp(this, 18));
        row.setBackground(ripple(0, 0, dp(this, 12)));
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, TaskActivity.class).putExtra("id", t.id));
            }
        });

        LinearLayout lv = column(this);
        TextView lvLabel = text(this, 12, DIM, null);
        lvLabel.setText("Lv");
        TextView lvNum = text(this, 38, TEXT, NUM);
        lvNum.setText(String.valueOf(r.level));
        lvNum.setIncludeFontPadding(false);
        lv.addView(lvLabel);
        lv.addView(lvNum);
        lv.setContentDescription("Level " + r.level);
        row.addView(lv, new LinearLayout.LayoutParams(dp(this, 60), -2));

        LinearLayout info = column(this);
        LinearLayout nameRow = row(this);
        TextView name = text(this, 17, TEXT, MEDIUM);
        name.setText(t.name);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        nameRow.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        if (r.streak > 0) {
            TextView streak = text(this, 14, DIM, MEDIUM);
            streak.setText("🔥 " + r.streak);
            streak.setContentDescription("Σερί " + r.streak);
            nameRow.addView(streak);
        }
        info.addView(nameRow);

        Engine.Sched s = t.sched(date);
        String meta = s.weekly()
                ? (date.equals(today) ? r.weekCount + " από " + r.weekRequired + " αυτή την εβδομάδα" : Ui.schedule(s))
                : s.on(date) ? null : (date.equals(today) ? "Ξεκούραση σήμερα" : "Ήταν μέρα ξεκούρασης");
        addSmall(info, meta);
        addSmall(info, t.minimum.isEmpty() ? null : "Ελάχιστο: " + t.minimum);

        LinearLayout barRow = row(this);
        barRow.setPadding(0, dp(this, 10), 0, 0);
        XpBar bar = new XpBar(this);
        Float from = animFrom.remove(t.id);
        bar.set((float) r.into / r.need, from == null ? -1 : from);
        barRow.addView(bar, new LinearLayout.LayoutParams(0, dp(this, 6), 1));
        TextView xp = text(this, 12, DIM, null);
        xp.setText(r.into + "/" + r.need + " XP");
        xp.setPadding(dp(this, 10), 0, 0, 0);
        barRow.addView(xp);
        info.addView(barRow);

        info.addView(statusControl(t, date, r));
        row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    private void addSmall(LinearLayout parent, String s) {
        if (s == null) return;
        TextView tv = text(this, 13, DIM, null);
        tv.setText(s);
        tv.setPadding(0, dp(this, 2), 0, 0);
        parent.addView(tv);
    }

    /** Done / Barely / (Skip | Ξεκούραση | Όχι σήμερα). Tapping the selected one clears it. */
    private View statusControl(final Engine.Task t, final LocalDate date, Engine.Result r) {
        Engine.Sched s = t.sched(date);
        final Character m = t.marks.get(date);
        final boolean weekly = s.weekly();
        final boolean scheduled = !weekly && s.on(date);
        boolean autoSkip = scheduled && m == null && date.isBefore(today);
        boolean counted = m != null && m != Engine.SKIP;
        // Weekly: once the quota is met, more times earn nothing.
        boolean earns = scheduled || (weekly && !(date.equals(today) && r.weekCount - (counted ? 1 : 0) >= r.weekRequired));

        int sel = m == null ? (scheduled && !autoSkip ? -1 : 2)
                : m == Engine.DONE ? 0 : m == Engine.BARELY ? 1 : 2;

        LinearLayout box = column(this);
        LinearLayout segs = row(this);
        segs.setPadding(0, dp(this, 14), 0, 0);
        segs.addView(segment("Done", earns ? "+" + s.doneXp() : null, sel == 0, LAVENDER, BLACK, new Runnable() {
            @Override
            public void run() {
                mark(t, date, m != null && m == Engine.DONE ? null : Engine.DONE);
            }
        }), weight(0));
        segs.addView(segment("Barely", earns ? "+" + s.barelyXp() : null, sel == 1, BARELY_BG, TEXT, new Runnable() {
            @Override
            public void run() {
                mark(t, date, m != null && m == Engine.BARELY ? null : Engine.BARELY);
            }
        }), weight(8));
        String third = weekly ? "Όχι σήμερα" : scheduled ? "Skip" : "Ξεκούραση";
        segs.addView(segment(third, scheduled ? "−" + s.doneXp() : null, sel == 2,
                scheduled ? WINE : REST_BG, scheduled ? WINE_TEXT : 0xFFB8B4C4, new Runnable() {
                    @Override
                    public void run() {
                        // Skip toggles an explicit skip; the other two just clear the day.
                        mark(t, date, scheduled && (m == null || m != Engine.SKIP) ? Engine.SKIP : null);
                    }
                }), weight(8));
        box.addView(segs);
        if (autoSkip) addSmall(box, "Μπήκε αυτόματα skip. Αλλάζει μέχρι το τέλος της ημέρας.");
        return box;
    }

    private LinearLayout.LayoutParams weight(int leftMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(this, 52), 1);
        lp.leftMargin = dp(this, leftMarginDp);
        return lp;
    }

    private View segment(String label, String xp, boolean selected, int selBg, int selText, final Runnable action) {
        LinearLayout seg = column(this);
        seg.setGravity(Gravity.CENTER);
        seg.setBackground(ripple(selected ? selBg : 0, selected ? 0 : FAINT, dp(this, 14)));
        seg.setSelected(selected);
        TextView l = text(this, 14, selected ? selText : TEXT, MEDIUM);
        l.setText(label);
        l.setGravity(Gravity.CENTER);
        seg.addView(l);
        if (xp != null) {
            TextView x = text(this, 12, selected ? selText : DIM, null);
            x.setText(xp + " XP");
            x.setGravity(Gravity.CENTER);
            seg.addView(x);
        }
        seg.setContentDescription(label + (xp != null ? ", " + xp + " XP" : ""));
        seg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                action.run();
            }
        });
        return seg;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(this, 1)));
        return v;
    }

    private View emptyState() {
        LinearLayout box = column(this);
        box.setPadding(0, dp(this, 72), 0, 0);
        TextView h = text(this, 22, TEXT, MEDIUM);
        h.setText("Ξεκίνα με ένα task");
        TextView p = text(this, 15, DIM, null);
        p.setText("Π.χ. Διάβασμα, με ελάχιστο 5 λεπτά. Κάθε done ή barely σε ανεβάζει, κάθε skip σε ρίχνει.");
        p.setPadding(0, dp(this, 8), 0, dp(this, 24));
        p.setLineSpacing(0, 1.2f);
        TextView b = text(this, 15, BLACK, MEDIUM);
        b.setText("Νέο task");
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(this, 48));
        b.setPadding(dp(this, 24), 0, dp(this, 24), 0);
        b.setBackground(ripple(LAVENDER, 0, dp(this, 24)));
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                newTask();
            }
        });
        box.addView(h);
        box.addView(p);
        box.addView(b, new LinearLayout.LayoutParams(-2, -2));
        return box;
    }

    // ---------------------------------------------------------------- actions

    private void newTask() {
        TaskEditor.show(this, tasks, null, new Runnable() {
            @Override
            public void run() {
                render();
            }
        });
    }

    private void mark(Engine.Task t, LocalDate date, Character m) {
        Engine.Result before = Engine.compute(t, today);
        Character old = m == null ? t.marks.remove(date) : t.marks.put(date, m);
        if (!Store.save(this, tasks)) {
            if (old == null) t.marks.remove(date); else t.marks.put(date, old);
            toast(this, "Δεν αποθηκεύτηκε. Έλεγξε τον ελεύθερο χώρο του κινητού.");
            return;
        }
        Engine.Result after = Engine.compute(t, today);
        if (after.level > before.level) {
            animFrom.put(t.id, 0f);
            toast(this, "Level up! " + t.name + " Lv " + after.level);
        } else if (after.level < before.level) {
            animFrom.put(t.id, 1f);
            toast(this, t.name + ": έπεσες στο Lv " + after.level);
        } else {
            animFrom.put(t.id, (float) before.into / before.need);
        }
        render();
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        boolean on = Store.remindOn(this);
        int at = Store.remindAt(this);
        String time = String.format(Locale.ROOT, "%02d:%02d", at / 60, at % 60);
        menu.getMenu().add(0, 1, 0, !on ? "Υπενθύμιση: κλειστή"
                : hasNotificationPermission() ? "Υπενθύμιση στις " + time : "Υπενθύμιση: δώσε άδεια ειδοποιήσεων");
        if (on) menu.getMenu().add(0, 2, 0, "Κλείσιμο υπενθύμισης");
        menu.getMenu().add(0, 3, 0, "Εξαγωγή backup");
        menu.getMenu().add(0, 4, 0, "Εισαγωγή backup");
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case 1:
                        if (Store.remindOn(MainActivity.this) && !hasNotificationPermission()) {
                            requestNotificationPermission();
                        } else {
                            pickReminderTime();
                        }
                        return true;
                    case 2:
                        Store.setReminder(MainActivity.this, false, Store.remindAt(MainActivity.this));
                        Reminder.schedule(MainActivity.this);
                        toast(MainActivity.this, "Η υπενθύμιση έκλεισε");
                        return true;
                    case 3:
                        startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                                .addCategory(Intent.CATEGORY_OPENABLE)
                                .setType("application/json")
                                .putExtra(Intent.EXTRA_TITLE, "SkillUP-" + LocalDate.now() + ".json"), REQ_EXPORT);
                        return true;
                    case 4:
                        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                                .addCategory(Intent.CATEGORY_OPENABLE)
                                .setType("*/*"), REQ_IMPORT);
                        return true;
                }
                return false;
            }
        });
        menu.show();
    }

    private void pickReminderTime() {
        int at = Store.remindAt(this);
        new TimePickerDialog(this, R.style.DialogTheme, new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(TimePicker view, int h, int min) {
                Store.setReminder(MainActivity.this, true, h * 60 + min);
                Reminder.schedule(MainActivity.this);
                if (hasNotificationPermission()) {
                    toast(MainActivity.this, String.format(Locale.ROOT, "Υπενθύμιση στις %02d:%02d", h, min));
                } else {
                    requestNotificationPermission();
                }
            }
        }, at / 60, at % 60, true).show();
    }

    // ---------------------------------------------------------------- backup

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_EXPORT) {
            try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                out.write(Store.toJson(tasks).getBytes(StandardCharsets.UTF_8));
                toast(this, "Το backup αποθηκεύτηκε");
            } catch (Exception e) {
                toast(this, "Το backup δεν αποθηκεύτηκε. Δοκίμασε άλλο φάκελο.");
            }
        } else if (requestCode == REQ_IMPORT) {
            final List<Engine.Task> imported;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] chunk = new byte[16384];
                for (int n; (n = in.read(chunk)) > 0; ) {
                    buf.write(chunk, 0, n);
                    if (buf.size() > 20_000_000) throw new IllegalArgumentException("too big");
                }
                imported = Store.parse(new String(buf.toByteArray(), StandardCharsets.UTF_8));
            } catch (Exception e) {
                toast(this, "Αυτό το αρχείο δεν είναι backup του SkillUP.");
                return;
            }
            dialog(this)
                    .setTitle("Φόρτωση backup;")
                    .setMessage("Τα τωρινά δεδομένα (" + tasks.size() + " tasks) θα αντικατασταθούν από το backup ("
                            + imported.size() + " tasks).")
                    .setPositiveButton("Φόρτωση", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            if (!Store.save(MainActivity.this, imported)) {
                                toast(MainActivity.this, "Δεν φορτώθηκε. Έλεγξε τον ελεύθερο χώρο του κινητού.");
                                return;
                            }
                            tasks = imported;
                            render();
                            toast(MainActivity.this, "Το backup φορτώθηκε");
                        }
                    })
                    .setNegativeButton("Άκυρο", null)
                    .show();
        }
    }

    // ---------------------------------------------------------------- notifications permission

    private boolean hasNotificationPermission() {
        return Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != REQ_NOTIF || grantResults.length == 0) return;
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Reminder.schedule(this);
        } else if (Build.VERSION.SDK_INT >= 33
                && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                && Store.askedPermission(this)) {
            // Android will not ask again: send the user to the app's notification settings.
            toast(this, "Άνοιξε τις ειδοποιήσεις για το SkillUP");
            try {
                startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
            } catch (ActivityNotFoundException ignored) {
            }
        }
    }
}
