package com.omiros.skillup;

import static com.omiros.skillup.Ui.*;

import android.Manifest;
import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
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
import java.util.ArrayList;
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
        Ui.init(this);
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

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        showYesterday = false; // the reminder is about today
    }

    // ---------------------------------------------------------------- layout

    private View buildUi() {
        LinearLayout root = column(this);
        root.setBackgroundColor(BG);

        LinearLayout header = row(this);
        header.setPadding(dp(this, 16), dp(this, 16), dp(this, 4), 0);
        LinearLayout titles = column(this);
        TextView title = text(this, 28, TEXT, XBOLD);
        title.setText("SkillUP");
        title.setLetterSpacing(-0.01f);
        title.setSingleLine(true);
        dateView = text(this, 14, TEXT2, null);
        titles.addView(title);
        LinearLayout.LayoutParams dateLp = new LinearLayout.LayoutParams(-2, -2);
        dateLp.topMargin = dp(this, 2);
        titles.addView(dateView, dateLp);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));

        ImageView add = iconButton(this, R.drawable.ic_add, "Νέο task");
        add.setBackground(inset(this, pressOval(CTRL, RIPPLE), 4, 4)); // 40dp tonal circle in a 48dp target
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
        days.setPadding(dp(this, 16), dp(this, 12), dp(this, 16), dp(this, 12));
        todayChip = dayToggle("Σήμερα", false);
        yesterdayChip = dayToggle("Χθες", true);
        days.addView(todayChip, new LinearLayout.LayoutParams(-2, dp(this, 48)));
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(-2, dp(this, 48));
        gap.leftMargin = dp(this, 3);
        days.addView(yesterdayChip, gap);
        summary = text(this, 14, TEXT2, MED);
        summary.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        summary.setMaxLines(2);
        LinearLayout.LayoutParams sumLp = new LinearLayout.LayoutParams(0, -2, 1);
        sumLp.leftMargin = dp(this, 12);
        days.addView(summary, sumLp);
        root.addView(days);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        list = column(this);
        list.setPadding(dp(this, 16), dp(this, 4), dp(this, 16), dp(this, 32));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        return root;
    }

    private TextView dayToggle(String label, final boolean yesterday) {
        TextView c = text(this, 14, TEXT2, SEMI);
        c.setText(label);
        c.setSingleLine(true);
        c.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        c.setMinWidth(dp(this, 104));
        c.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showYesterday = yesterday;
                render();
            }
        });
        return c;
    }

    /** Connected pair: tight inner corners; the selected one becomes a full pill with a check. */
    private void styleToggle(TextView c, boolean on, boolean first) {
        float[] corners = on ? new float[]{20} : first ? new float[]{20, 8, 8, 20} : new float[]{8, 20, 20, 8};
        c.setBackground(inset(this, press(this, on ? LAV_CONTAINER : CTRL, RIPPLE, corners), 0, 4));
        c.setTextColor(on ? ON_LAV_CONTAINER : TEXT2);
        Drawable check = null;
        if (on) {
            check = getDrawable(R.drawable.ic_check).mutate();
            check.setTint(ON_LAV_CONTAINER);
            check.setBounds(0, 0, dp(this, 18), dp(this, 18));
        }
        c.setCompoundDrawablesRelative(check, null, null, null);
        c.setCompoundDrawablePadding(dp(this, 6));
        // Centre the content without the width jumping between states (padding after setBackground: insets reset it).
        float content = c.getPaint().measureText(c.getText().toString()) + (on ? dp(this, 24) : 0);
        int pad = Math.max(dp(this, 14), Math.round((dp(this, 104) - content) / 2));
        c.setPaddingRelative(pad, dp(this, 4), pad, dp(this, 4));
        c.setSelected(on);
    }

    private void render() {
        today = LocalDate.now();
        if (tasks.isEmpty()) showYesterday = false; // otherwise the next new task hides behind "Χθες"
        LocalDate date = showYesterday ? today.minusDays(1) : today;
        String d = date.format(DATE_FMT);
        dateView.setText(Character.toUpperCase(d.charAt(0)) + d.substring(1));
        styleToggle(todayChip, !showYesterday, true);
        styleToggle(yesterdayChip, showYesterday, false);

        list.removeAllViews();
        dayBar.setVisibility(tasks.isEmpty() ? View.GONE : View.VISIBLE);
        if (tasks.isEmpty()) {
            list.addView(emptyState());
            return;
        }

        List<Engine.Task> shown = new ArrayList<>();
        List<Engine.Result> results = new ArrayList<>();
        int due = 0;
        float captionWidth = 0;
        for (Engine.Task t : tasks) {
            Engine.Result r = Engine.compute(t, today);
            if (Engine.due(t, r, today)) due++;
            if (t.created().isAfter(date)) continue;
            shown.add(t);
            results.add(r);
            captionWidth = Math.max(captionWidth, measure(this, MED, 12, true, xpCaption(r)));
        }
        // One caption width for every card, so all XP bars end at the same x.
        int capW = (int) Math.ceil(captionWidth) + dp(this, 2); // slack against layout rounding; text is end-aligned
        for (int i = 0; i < shown.size(); i++) {
            if (!shown.get(i).avoid) list.addView(taskCard(shown.get(i), date, results.get(i), capW));
        }
        boolean heading = false;
        for (int i = 0; i < shown.size(); i++) {
            if (!shown.get(i).avoid) continue;
            if (!heading) {
                heading = true;
                list.addView(sectionTitle("Αποφεύγω", list.getChildCount() > 0));
            }
            list.addView(taskCard(shown.get(i), date, results.get(i), capW));
        }
        if (shown.isEmpty()) {
            TextView none = text(this, 15, TEXT2, null);
            none.setText("Χθες δεν είχες ακόμα tasks.");
            none.setPadding(dp(this, 4), dp(this, 32), 0, 0);
            list.addView(none);
        }
        summary.setText(showYesterday ? "" : due == 1 ? "Μένει 1" : due > 1 ? "Μένουν " + due : "Τελείωσες για σήμερα");
        summary.setTextColor(due == 0 ? LAV_TEXT : TEXT2);
    }

    private static String xpCaption(Engine.Result r) {
        return r.into + "/" + r.need + " XP";
    }

    private View taskCard(final Engine.Task t, LocalDate date, Engine.Result r, int captionWidth) {
        LinearLayout card = column(this);
        card.setBackground(press(this, CARD, RIPPLE, 28));
        card.setPadding(dp(this, 16), dp(this, 20), dp(this, 16), dp(this, 16));
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, TaskActivity.class).putExtra("id", t.id));
            }
        });

        LinearLayout head = row(this);
        head.setGravity(Gravity.TOP);
        head.setBaselineAligned(false);
        LinearLayout lv = column(this);
        lv.setMinimumWidth(dp(this, 52));
        TextView lvLabel = text(this, 12, TEXT2, SEMI);
        lvLabel.setText("Lv");
        lvLabel.setLetterSpacing(0.02f);
        TextView lvNum = text(this, 40, TEXT, XBOLD);
        lvNum.setText(String.valueOf(r.level));
        lvNum.setLetterSpacing(-0.02f);
        lvNum.setIncludeFontPadding(false);
        tnum(lvNum);
        lv.addView(lvLabel);
        LinearLayout.LayoutParams numLp = new LinearLayout.LayoutParams(-2, -2);
        numLp.topMargin = dp(this, 2);
        lv.addView(lvNum, numLp);
        lv.setContentDescription("Level " + r.level);
        head.addView(lv);

        LinearLayout info = column(this);
        LinearLayout nameRow = row(this);
        TextView name = text(this, 18, TEXT, SEMI);
        name.setText(t.name);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, -2, 1);
        nameLp.setMarginStart(-overhang(name, true));
        nameRow.addView(name, nameLp);
        if (r.streak > 0) {
            TextView streak = text(this, 14, TEXT2, SEMI);
            streak.setText("🔥 " + r.streak);
            tnum(streak);
            streak.setContentDescription("Σερί " + r.streak);
            LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(-2, -2);
            sLp.leftMargin = dp(this, 8);
            nameRow.addView(streak, sLp);
        }
        info.addView(nameRow);

        Engine.Sched s = t.sched(date);
        String meta = t.avoid ? null // the control itself says "Καθαρό, μέχρι τώρα"
                : s.weekly()
                ? (date.equals(today) && r.weekRequired > 0 // a week that starts on Sunday may ask for nothing
                        ? r.weekCount + " από " + r.weekRequired + " αυτή την εβδομάδα" : Ui.schedule(s))
                : s.on(date) ? null : (date.equals(today) ? "Ξεκούραση σήμερα" : "Ήταν μέρα ξεκούρασης");
        addMeta(info, meta);
        addMeta(info, t.minimum.isEmpty() ? null : "Ελάχιστο: " + t.minimum);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, -2, 1);
        infoLp.leftMargin = dp(this, 12);
        head.addView(info, infoLp);
        card.addView(head);

        LinearLayout xpRow = row(this);
        XpBar bar = new XpBar(this, false);
        Float from = animFrom.remove(t.id);
        bar.set((float) r.into / r.need, from == null ? -1 : from);
        xpRow.addView(bar, new LinearLayout.LayoutParams(0, dp(this, 8), 1));
        TextView xp = text(this, 12, TEXT2, MED);
        xp.setText(xpCaption(r));
        xp.setGravity(Gravity.END);
        xp.setSingleLine(true);
        tnum(xp);
        LinearLayout.LayoutParams xpLp = new LinearLayout.LayoutParams(captionWidth, -2);
        xpLp.leftMargin = dp(this, 12);
        xpRow.addView(xp, xpLp);
        card.addView(xpRow, topMargin(this, 14));

        card.addView(t.avoid ? avoidControl(t, date) : statusControl(t, date), topMargin(this, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(this, 12);
        card.setLayoutParams(lp);
        return card;
    }

    private void addMeta(LinearLayout parent, String s) {
        if (s == null) return;
        TextView tv = text(this, 13, TEXT2, null);
        tv.setText(s);
        lineHeight(tv, 18);
        tv.setPadding(0, dp(this, 2), 0, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMarginStart(-overhang(tv, true));
        parent.addView(tv, lp);
    }

    private View sectionTitle(String title, boolean afterCards) {
        TextView h = text(this, 20, TEXT, SEMI);
        h.setText(title);
        h.setPadding(dp(this, 4), dp(this, afterCards ? 16 : 4), 0, dp(this, 12));
        if (Build.VERSION.SDK_INT >= 28) h.setAccessibilityHeading(true);
        return h;
    }

    /**
     * Avoid task: [Το έκανα | Καθαρό]. A day is clean unless "Το έκανα" is marked; today stays
     * "clean so far" (pending style, no XP yet) until midnight. Tapping "Το έκανα" again undoes it.
     */
    private View avoidControl(final Engine.Task t, final LocalDate date) {
        final boolean slipped = t.marks.containsKey(date) && t.marks.get(date) == Engine.SKIP;
        boolean ended = date.isBefore(today), pending = !slipped && !ended;
        LinearLayout segs = new LinearLayout(this);
        segs.setMinimumHeight(dp(this, 56));
        segs.setBaselineAligned(false);
        segs.addView(segment("Το έκανα", "−" + Engine.AVOID_SLIP_XP, slipped, 0,
                WINE, ON_WINE, ON_WINE_2, RIPPLE, new Runnable() {
                    @Override
                    public void run() {
                        mark(t, date, slipped ? null : Engine.SKIP);
                    }
                }));
        View clean = segment("Καθαρό", ended ? "+" + Engine.AVOID_CLEAN_XP : null, !slipped, 2,
                pending ? LAV_CONTAINER : LAVENDER, pending ? ON_LAV_CONTAINER : ON_LAVENDER,
                pending ? ON_LAV_CONTAINER_2 : ON_LAVENDER_2, pending ? RIPPLE : RIPPLE_ON_LAV, new Runnable() {
                    @Override
                    public void run() {
                        if (slipped) mark(t, date, null); // already clean otherwise: nothing to change
                    }
                });
        if (pending) {
            // Clean so far: the +10 arrives at midnight.
            TextView sub = text(this, 12, ON_LAV_CONTAINER_2, MED);
            sub.setText("+" + Engine.AVOID_CLEAN_XP + " τα μεσάνυχτα");
            tnum(sub);
            sub.setGravity(Gravity.CENTER);
            autosize(sub, 10, 12);
            ((LinearLayout) clean).addView(sub, new LinearLayout.LayoutParams(-1, lineBox(this, 12)));
            clean.setContentDescription("Καθαρό μέχρι τώρα, +" + Engine.AVOID_CLEAN_XP + " XP τα μεσάνυχτα");
        }
        segs.addView(clean);
        LinearLayout box = column(this);
        box.addView(segs, new LinearLayout.LayoutParams(-1, -2));
        return box;
    }

    /** (Skip | Ξεκούραση | Όχι) / Barely / Done. Tapping the selected one clears it. */
    private View statusControl(final Engine.Task t, final LocalDate date) {
        Engine.Sched s = t.sched(date);
        final Character m = t.marks.get(date);
        final boolean weekly = s.weekly();
        final boolean scheduled = !weekly && s.on(date);
        boolean autoSkip = scheduled && m == null && date.isBefore(today);
        // Weekly: only the week's best marks up to its quota earn XP; Engine works out whether this one would.
        boolean doneEarns = scheduled || weekly && Engine.earns(t, date, Engine.DONE, today);
        boolean barelyEarns = scheduled || weekly && Engine.earns(t, date, Engine.BARELY, today);

        int sel = m == null ? (scheduled && !autoSkip ? -1 : 2)
                : m == Engine.DONE ? 0 : m == Engine.BARELY ? 1 : 2;

        LinearLayout box = column(this);
        LinearLayout segs = new LinearLayout(this);
        segs.setMinimumHeight(dp(this, 56));
        segs.setBaselineAligned(false);
        // Left to right: Skip (or Ξεκούραση / Όχι), Barely, Done.
        String first = weekly ? (date.equals(today) ? "Όχι σήμερα" : "Όχι") : scheduled ? "Skip" : "Ξεκούραση";
        segs.addView(segment(first, scheduled ? "−" + s.doneXp() : null, sel == 2, 0,
                scheduled ? WINE : REST_SEL, scheduled ? ON_WINE : TEXT, scheduled ? ON_WINE_2 : TEXT2, RIPPLE,
                new Runnable() {
                    @Override
                    public void run() {
                        // Skip toggles an explicit skip; the other two just clear the day.
                        mark(t, date, scheduled && (m == null || m != Engine.SKIP) ? Engine.SKIP : null);
                    }
                }));
        segs.addView(segment("Barely", barelyEarns ? "+" + s.barelyXp() : null, sel == 1, 1,
                LAV_CONTAINER, ON_LAV_CONTAINER, ON_LAV_CONTAINER_2, RIPPLE, new Runnable() {
                    @Override
                    public void run() {
                        mark(t, date, m != null && m == Engine.BARELY ? null : Engine.BARELY);
                    }
                }));
        segs.addView(segment("Done", doneEarns ? "+" + s.doneXp() : null, sel == 0, 2,
                LAVENDER, ON_LAVENDER, ON_LAVENDER_2, RIPPLE_ON_LAV, new Runnable() {
                    @Override
                    public void run() {
                        mark(t, date, m != null && m == Engine.DONE ? null : Engine.DONE);
                    }
                }));
        box.addView(segs, new LinearLayout.LayoutParams(-1, -2));
        if (autoSkip) {
            TextView note = text(this, 12, WINE_CAPTION, null);
            note.setText("Μπήκε αυτόματα skip. Αλλάζει μέχρι το τέλος της ημέρας.");
            lineHeight(note, 16);
            note.setPadding(dp(this, 4), 0, 0, 0);
            box.addView(note, topMargin(this, 8));
        }
        return box;
    }

    /** position 0 / 1 / 2 in a connected group; selected = full pill. */
    private View segment(String label, String xp, boolean selected, int position,
                         int selFill, int selText, int selSub, int selRipple, final Runnable action) {
        LinearLayout seg = column(this);
        seg.setGravity(Gravity.CENTER);
        seg.setPadding(dp(this, 8), dp(this, 6), dp(this, 8), dp(this, 6));
        float[] corners = selected ? new float[]{28}
                : position == 0 ? new float[]{28, 8, 8, 28} : position == 2 ? new float[]{8, 28, 28, 8} : new float[]{8};
        seg.setBackground(press(this, selected ? selFill : CTRL, selected ? selRipple : RIPPLE, corners));
        seg.setSelected(selected);
        TextView l = text(this, 14, selected ? selText : TEXT, SEMI);
        l.setText(label);
        l.setGravity(Gravity.CENTER);
        autosize(l, 11, 14);
        seg.addView(l, new LinearLayout.LayoutParams(-1, lineBox(this, 14)));
        if (xp != null) {
            TextView x = text(this, 12, selected ? selSub : TEXT2, MED);
            x.setText(xp + " XP");
            x.setGravity(Gravity.CENTER);
            tnum(x);
            autosize(x, 10, 12);
            seg.addView(x, new LinearLayout.LayoutParams(-1, lineBox(this, 12)));
        }
        seg.setContentDescription(label + (xp != null ? ", " + xp + " XP" : ""));
        seg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                action.run();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
        if (position > 0) lp.leftMargin = dp(this, 3);
        seg.setLayoutParams(lp);
        return seg;
    }

    private View emptyState() {
        LinearLayout box = column(this);
        box.setBackground(shape(this, CARD, 28));
        int pad = dp(this, 24);
        box.setPadding(pad, pad, pad, pad);
        TextView h = text(this, 24, TEXT, SEMI);
        h.setText("Ξεκίνα με ένα task");
        TextView p = text(this, 15, TEXT2, null);
        p.setText("Π.χ. Διάβασμα, με ελάχιστο 5 λεπτά. Κάθε done ή barely σε ανεβάζει, κάθε skip σε ρίχνει.");
        lineHeight(p, 22);
        TextView b = text(this, 15, ON_LAVENDER, SEMI);
        b.setText("Νέο task");
        b.setGravity(Gravity.CENTER);
        b.setBackground(press(this, LAVENDER, RIPPLE_ON_LAV, 26));
        b.setPaddingRelative(dp(this, 20), 0, dp(this, 24), 0);
        Drawable plus = getDrawable(R.drawable.ic_add).mutate();
        plus.setTint(ON_LAVENDER);
        plus.setBounds(0, 0, dp(this, 20), dp(this, 20));
        b.setCompoundDrawablesRelative(plus, null, null, null);
        b.setCompoundDrawablePadding(dp(this, 8));
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                newTask();
            }
        });
        box.addView(h);
        box.addView(p, topMargin(this, 8));
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(-2, dp(this, 52));
        bLp.topMargin = dp(this, 20);
        box.addView(b, bLp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(this, 24);
        box.setLayoutParams(lp);
        return box;
    }

    // ---------------------------------------------------------------- actions

    private void newTask() {
        TaskEditor.show(this, tasks, null, new Runnable() {
            @Override
            public void run() {
                tasks = Store.load(MainActivity.this); // the sheet may hold the list from before the last onResume
                render();
            }
        });
    }

    private void mark(Engine.Task t, LocalDate date, Character m) {
        if (!today.equals(LocalDate.now())) { // the screen stayed on past midnight: show the new day first
            toast(this, "Ξεκίνησε νέα μέρα");
            render();
            return;
        }
        Engine.Result before = Engine.compute(t, today);
        Character old = m == null ? t.marks.remove(date) : t.marks.put(date, m);
        if (old == null && m == null) return; // nothing to clear, so nothing to write
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
                            openNotificationSettings();
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
                    openNotificationSettings();
                }
            }
        }, at / 60, at % 60, true).show();
    }

    // ---------------------------------------------------------------- backup

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        tasks = Store.load(this); // if Android recreated the screen, this runs before onResume loads them
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
            dialog(this, "Φόρτωση backup;")
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

    /**
     * The system dialog is asked once, at first launch; Android may refuse to show it again, so later the
     * app's notification settings, which always work, turn notifications on. The alarm is set either way.
     */
    private void openNotificationSettings() {
        toast(this, "Άνοιξε τις ειδοποιήσεις για το SkillUP");
        try {
            startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        } catch (ActivityNotFoundException ignored) {
        }
    }
}
