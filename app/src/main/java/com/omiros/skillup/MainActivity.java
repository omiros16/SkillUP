package com.omiros.skillup;

import static com.omiros.skillup.Ui.*;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
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
    /** Hold this long for Barely: long enough not to happen by accident, short enough not to feel like waiting. */
    private static final int HOLD_MS = 500;
    private static final TimeInterpolator EASE = new PathInterpolator(0.2f, 0f, 0f, 1f);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEEE d MMMM", new Locale("el", "GR"));

    private List<Engine.Task> tasks;
    private LocalDate today;
    private boolean showYesterday;
    /** XP bar start points for the next render, so a change animates from where it was. */
    private final Map<Long, Float> animFrom = new HashMap<>();

    private TextView dateView;
    private TextView summary;
    /** One button flips the home between today and yesterday. */
    private TextView dayButton;
    private GradientDrawable dayFill;
    private Drawable daySwap;
    private int dayButtonWidth;
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
        dayButton = text(this, 14, TEXT, SEMI);
        dayButton.setSingleLine(true);
        dayButton.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        dayFill = shape(this, CTRL, 20);
        dayButton.setBackground(inset(this, new RippleDrawable(ColorStateList.valueOf(RIPPLE), dayFill,
                shape(this, 0xFFFFFFFF, 20)), 0, 4)); // a 40dp pill in a 48dp target
        daySwap = getDrawable(R.drawable.ic_swap).mutate();
        daySwap.setBounds(0, 0, dp(this, 18), dp(this, 18));
        dayButton.setCompoundDrawablesRelative(null, null, daySwap, null);
        dayButton.setCompoundDrawablePadding(dp(this, 8));
        // Wide enough for either label, so the button never changes size.
        float label = Math.max(dayButton.getPaint().measureText("Σήμερα"), dayButton.getPaint().measureText("Χθες"));
        dayButtonWidth = Math.round(label) + dp(this, 2 * 18 + 8 + 18);
        dayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                switchDay();
            }
        });
        days.addView(dayButton, new LinearLayout.LayoutParams(dayButtonWidth, dp(this, 48)));
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

    private static final int[] DAY_TODAY = {CTRL, TEXT, TEXT2};
    private static final int[] DAY_YESTERDAY = {LAV_CONTAINER, ON_LAV_CONTAINER, ON_LAV_CONTAINER_2};

    /** The day button shows the day on screen; on yesterday it takes the lavender tone, as a reminder. */
    private void styleDay() {
        String label = showYesterday ? "Χθες" : "Σήμερα";
        dayButton.setText(label);
        // Centre label and icon together (padding after setBackground: the inset resets it).
        int content = Math.round(dayButton.getPaint().measureText(label)) + dp(this, 8 + 18);
        int pad = (dayButtonWidth - content) / 2;
        dayButton.setPaddingRelative(pad, 0, pad, 0);
        paintDay(showYesterday ? DAY_YESTERDAY : DAY_TODAY);
        dayButton.setContentDescription(showYesterday ? "Χθες. Πάτα για σήμερα" : "Σήμερα. Πάτα για χθες");
    }

    private void paintDay(int[] c) {
        dayFill.setColor(c[0]);
        dayButton.setTextColor(c[1]);
        daySwap.setTint(c[2]);
    }

    /** Flips the day: the button's colours cross-fade and the list slides in from the side of the day it shows. */
    private void switchDay() {
        final int[] from = showYesterday ? DAY_YESTERDAY : DAY_TODAY;
        showYesterday = !showYesterday;
        render();
        final int[] to = showYesterday ? DAY_YESTERDAY : DAY_TODAY;
        final ArgbEvaluator mix = new ArgbEvaluator();
        ValueAnimator fade = ValueAnimator.ofFloat(0f, 1f).setDuration(220);
        fade.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                float f = a.getAnimatedFraction();
                paintDay(new int[]{(Integer) mix.evaluate(f, from[0], to[0]), (Integer) mix.evaluate(f, from[1], to[1]),
                        (Integer) mix.evaluate(f, from[2], to[2])});
            }
        });
        fade.start();
        list.setAlpha(0f);
        list.setTranslationX(dp(this, showYesterday ? -32 : 32)); // yesterday comes in from the left
        list.animate().alpha(1f).translationX(0f).setDuration(260).setInterpolator(EASE).start();
    }

    private void render() {
        today = LocalDate.now();
        if (tasks.isEmpty()) showYesterday = false; // otherwise the next new task hides behind "Χθες"
        LocalDate date = showYesterday ? today.minusDays(1) : today;
        String d = date.format(DATE_FMT);
        dateView.setText(Character.toUpperCase(d.charAt(0)) + d.substring(1));
        styleDay();

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
        // What is still to do comes first; what is done (or barely) sinks to the bottom, under «Έγιναν».
        List<View> finished = new ArrayList<>();
        for (int i = 0; i < shown.size(); i++) {
            Engine.Task t = shown.get(i);
            if (t.avoid) continue;
            View card = taskCard(t, date, results.get(i), capW);
            Character m = t.marks.get(date);
            if (m != null && (m == Engine.DONE || m == Engine.BARELY)) finished.add(card); else list.addView(card);
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
        if (!finished.isEmpty()) {
            list.addView(sectionTitle("Έγιναν", list.getChildCount() > 0));
            for (View card : finished) list.addView(card);
        }
        if (shown.isEmpty()) {
            TextView none = text(this, 15, TEXT2, null);
            none.setText("Χθες δεν είχες ακόμα tasks.");
            none.setPadding(dp(this, 4), dp(this, 32), 0, 0);
            list.addView(none);
        }
        summary.setText(showYesterday ? "Αλλάζει ως τα μεσάνυχτα"
                : due == 1 ? "Μένει 1" : due > 1 ? "Μένουν " + due : "Τελείωσες για σήμερα");
        summary.setTextColor(due == 0 && !showYesterday ? LAV_TEXT : TEXT2);
    }

    private static String xpCaption(Engine.Result r) {
        return r.into + "/" + r.need + " XP";
    }

    private View taskCard(final Engine.Task t, LocalDate date, Engine.Result r, int captionWidth) {
        LinearLayout card = column(this);
        card.setTransitionName("task:" + t.id);
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
        h.setTransitionName("section:" + title);
        h.setPadding(dp(this, 4), dp(this, afterCards ? 16 : 4), 0, dp(this, 12));
        overhang(h, false); // «Έγιναν»: room for the tonos inside the 4dp padding
        if (Build.VERSION.SDK_INT >= 28) h.setAccessibilityHeading(true);
        return h;
    }

    /** Avoid task: one button. A day is clean unless tapped («Το έκανα»); tapping again undoes it. */
    private View avoidControl(final Engine.Task t, final LocalDate date) {
        final boolean slipped = t.marks.containsKey(date) && t.marks.get(date) == Engine.SKIP;
        boolean pending = !slipped && !date.isBefore(today); // today: clean so far, the +10 arrives at midnight
        String sub = slipped ? "−" + Engine.AVOID_SLIP_XP + " XP"
                : "+" + Engine.AVOID_CLEAN_XP + (pending ? " τα μεσάνυχτα" : " XP");
        return stateButton(slipped ? "Το έκανα" : "Καθαρό", sub, slipped ? WINE : pending ? LAV_CONTAINER : LAVENDER,
                slipped ? "Καθαρό" : "Το έκανα", new Runnable() {
                    @Override
                    public void run() {
                        mark(t, date, slipped ? null : Engine.SKIP);
                    }
                }, null, null);
    }

    /**
     * One button: tap = Done, hold = Barely, the same again = undo. Unmarked, a scheduled day shows the skip it
     * becomes at midnight; a rest day or a weekly task's free day shows how to mark it.
     */
    private View statusControl(final Engine.Task t, final LocalDate date) {
        Engine.Sched s = t.sched(date);
        final Character m = t.marks.get(date);
        boolean scheduled = !s.weekly() && s.on(date), past = date.isBefore(today);
        String label, sub;
        int fill;
        if (m != null && m == Engine.DONE) {
            label = "Done";
            fill = LAVENDER;
            sub = earns(t, s, date, Engine.DONE, scheduled) ? "+" + s.doneXp() + " XP" : "χωρίς XP";
        } else if (m != null && m == Engine.BARELY) {
            label = "Barely";
            fill = LAV_CONTAINER;
            sub = earns(t, s, date, Engine.BARELY, scheduled) ? "+" + s.barelyXp() + " XP" : "χωρίς XP";
        } else if (scheduled) {
            boolean counted = past || m != null; // an explicit skip (made before 1.5) counts at once
            label = "Skip";
            fill = counted ? WINE : WINE_PENDING;
            sub = "−" + s.doneXp() + (counted ? " XP" : " τα μεσάνυχτα");
        } else {
            label = s.weekly() ? (past ? "Όχι" : "Όχι σήμερα") : "Ξεκούραση";
            fill = CTRL;
            sub = "Πάτα για Done, κράτα για Barely";
        }
        boolean done = m != null && m == Engine.DONE, barely = m != null && m == Engine.BARELY;
        final Character onTap = done ? null : Engine.DONE, onHold = barely ? null : Engine.BARELY;
        View b = stateButton(label, sub, fill, done ? "Αναίρεση" : "Done", new Runnable() {
            @Override
            public void run() {
                mark(t, date, onTap);
            }
        }, barely ? "Αναίρεση" : "Barely", new Runnable() {
            @Override
            public void run() {
                mark(t, date, onHold);
            }
        });
        if (!(scheduled && m == null && past)) return b;
        LinearLayout box = column(this);
        box.addView(b);
        TextView note = text(this, 12, WINE_CAPTION, null);
        note.setText("Μπήκε αυτόματα skip. Αλλάζει μέχρι το τέλος της ημέρας.");
        lineHeight(note, 16);
        note.setPadding(dp(this, 4), 0, 0, 0);
        box.addView(note, topMargin(this, 8));
        return box;
    }

    /** Daily scheduled days always earn; on weekly tasks only the week's best marks up to its quota do. */
    private boolean earns(Engine.Task t, Engine.Sched s, LocalDate date, char mark, boolean scheduled) {
        return scheduled || s.weekly() && Engine.earns(t, date, mark, today);
    }

    /**
     * A full-width pill: the day's state on top, its XP below. Tap runs {@code tap}; holding runs {@code hold}
     * (if any) while the pill fills from the left. Screen readers get the two as named actions.
     */
    private View stateButton(String label, String sub, int fill, final String tapName, final Runnable tap,
                             final String holdName, final Runnable hold) {
        LinearLayout b = column(this);
        b.setGravity(Gravity.CENTER);
        b.setMinimumHeight(dp(this, 56));
        b.setPadding(dp(this, 16), dp(this, 6), dp(this, 16), dp(this, 6));
        boolean light = fill == LAVENDER;
        ClipDrawable progress = new ClipDrawable(shape(this, light ? 0x40000000 : 0x38FFFFFF, 28),
                Gravity.START, ClipDrawable.HORIZONTAL);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(light ? RIPPLE_ON_LAV : RIPPLE),
                new LayerDrawable(new Drawable[]{shape(this, fill, 28), progress}), shape(this, 0xFFFFFFFF, 28)));
        int ink = light ? ON_LAVENDER : fill == LAV_CONTAINER ? ON_LAV_CONTAINER : fill == CTRL ? TEXT : ON_WINE;
        int ink2 = light ? ON_LAVENDER_2 : fill == LAV_CONTAINER ? ON_LAV_CONTAINER_2 : fill == CTRL ? TEXT2 : ON_WINE_2;
        TextView l = text(this, 15, ink, SEMI);
        l.setText(label);
        l.setGravity(Gravity.CENTER);
        autosize(l, 12, 15);
        b.addView(l, new LinearLayout.LayoutParams(-1, lineBox(this, 15)));
        TextView x = text(this, 12, ink2, MED);
        x.setText(sub);
        x.setGravity(Gravity.CENTER);
        tnum(x);
        autosize(x, 10, 12);
        b.addView(x, new LinearLayout.LayoutParams(-1, lineBox(this, 12)));
        b.setContentDescription(label + ", " + sub);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                tap.run();
            }
        });
        if (hold != null) {
            b.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    hold.run();
                    return true; // the framework adds the long-press haptic
                }
            });
            b.setOnTouchListener(new Hold(b, progress));
        }
        b.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, tapName));
                if (hold != null) {
                    info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                            AccessibilityNodeInfo.ACTION_LONG_CLICK, holdName));
                }
            }
        });
        b.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return b;
    }

    /**
     * Press and hold: the pill fills from the left and, after HOLD_MS (or the phone's longer touch-and-hold
     * setting), performs its long click; letting go earlier is a tap. Scrolling cancels it.
     */
    private static final class Hold implements View.OnTouchListener, Runnable {
        private final View view;
        private final ClipDrawable progress;
        private final int ms, slop;
        private ValueAnimator fill;
        private boolean down, fired;

        Hold(View view, ClipDrawable progress) {
            this.view = view;
            this.progress = progress;
            ms = Math.max(HOLD_MS, ViewConfiguration.getLongPressTimeout());
            slop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
        }

        @Override
        public void run() {
            fired = true;
            stop();
            view.performLongClick();
        }

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down = true;
                    fired = false;
                    v.drawableHotspotChanged(e.getX(), e.getY());
                    v.setPressed(true);
                    v.postDelayed(this, ms);
                    fill = ValueAnimator.ofInt(0, 10000).setDuration(ms);
                    fill.setInterpolator(new LinearInterpolator());
                    fill.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(ValueAnimator a) {
                            progress.setLevel((Integer) a.getAnimatedValue());
                        }
                    });
                    fill.start();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (down && !inside(v, e)) stop();
                    return true;
                case MotionEvent.ACTION_UP:
                    boolean tap = down && !fired && inside(v, e);
                    stop();
                    if (tap) v.performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    stop();
                    return true;
                default:
                    return false;
            }
        }

        private boolean inside(View v, MotionEvent e) {
            return e.getX() >= -slop && e.getY() >= -slop && e.getX() < v.getWidth() + slop && e.getY() < v.getHeight() + slop;
        }

        private void stop() {
            down = false;
            view.removeCallbacks(this);
            if (fill != null) fill.cancel();
            progress.setLevel(0);
            view.setPressed(false);
        }
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
        if (!Store.save(this, tasks)) {
            if (old == null) t.marks.remove(date); else t.marks.put(date, old);
            toast(this, "Δεν αποθηκεύτηκε. Έλεγξε τον ελεύθερο χώρο του κινητού.");
            return;
        }
        Engine.Result after = Engine.compute(t, today);
        // A card that changes place (done sinks, undo rises) slides there; «Έγιναν» fades in and out.
        TransitionManager.beginDelayedTransition(list, new TransitionSet()
                .addTransition(new ChangeBounds())
                .addTransition(new Fade().addTarget("section:Έγιναν"))
                .setDuration(320).setInterpolator(EASE));
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
