package com.omiros.skillup;

import static com.omiros.skillup.Ui.*;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalDate;
import java.util.List;

/** One task: level plate, stats and a dot grid per year. */
public class TaskActivity extends Activity {
    private List<Engine.Task> tasks;
    private Engine.Task task;
    private TextView title;
    private LinearLayout body;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.init(this);
        LinearLayout root = column(this);
        root.setBackgroundColor(BG);

        LinearLayout header = row(this);
        header.setPadding(dp(this, 4), dp(this, 8), dp(this, 4), 0);
        header.setMinimumHeight(dp(this, 64));
        ImageView back = iconButton(this, R.drawable.ic_back, "Πίσω");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        header.addView(back);
        title = text(this, 22, TEXT, SEMI);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(dp(this, 4), 0, 0, 0);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1);
        titleLp.setMarginStart(-overhang(title, true));
        header.addView(title, titleLp);
        final ImageView more = iconButton(this, R.drawable.ic_more, "Μενού");
        more.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu(more);
            }
        });
        header.addView(more);
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        body = column(this);
        body.setPadding(dp(this, 16), dp(this, 8), dp(this, 16), dp(this, 40));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    /** Reads the tasks again and shows this one; closes the page if it is gone. */
    private void load() {
        tasks = Store.load(this);
        long id = getIntent().getLongExtra("id", -1);
        task = null;
        for (Engine.Task t : tasks) if (t.id == id) task = t;
        if (task == null) {
            finish();
            return;
        }
        render();
    }

    private void render() {
        LocalDate today = LocalDate.now();
        Engine.Result r = Engine.compute(task, today);
        title.setText(task.name);
        body.removeAllViews();

        body.addView(levelPlate(r));

        LinearLayout stats = row(this);
        stats.setBaselineAligned(false);
        stat(stats, r.streak, "σερί", 0);
        stat(stats, r.best, "καλύτερο", 1);
        if (task.avoid) {
            stat(stats, r.done, "καθαρές", 1);
            stat(stats, r.skips, "το έκανα", 2);
        } else {
            stat(stats, r.done, "done", 1);
            stat(stats, r.barely, "barely", 1);
            stat(stats, r.skips, "skip", 2);
        }
        body.addView(stats, topMargin(this, 12));

        Engine.Sched s = task.current();
        TextView plan = text(this, 16, TEXT, SEMI);
        plan.setText(task.avoid ? "Αποφυγή, κάθε μέρα" : Ui.schedule(s));
        body.addView(plan, topMargin(this, 28));
        TextView rule = text(this, 14, TEXT2, null);
        rule.setText(task.avoid ? avoidRule() : xpRule(s));
        lineHeight(rule, 20);
        body.addView(rule, topMargin(this, 6));
        if (!task.minimum.isEmpty()) {
            TextView min = text(this, 14, TEXT2, null);
            min.setText("Ελάχιστο: " + task.minimum);
            lineHeight(min, 20);
            body.addView(min, topMargin(this, 4));
        }

        LinearLayout legend = row(this);
        if (task.avoid) {
            legend(legend, LAVENDER, false, "καθαρή");
            legend(legend, SKIP_DOT, false, "το έκανα");
        } else {
            legend(legend, LAVENDER, false, "done");
            legend(legend, BARELY_DOT, false, "barely");
            legend(legend, SKIP_DOT, false, "skip");
            legend(legend, REST_RING, true, "ξεκούραση");
        }
        body.addView(legend, topMargin(this, 20));

        for (int y = today.getYear(); y >= task.created().getYear(); y--) {
            boolean current = y == today.getYear();
            TextView year = text(this, current ? 28 : 22, current ? TEXT : TEXT2, XBOLD);
            year.setText(String.valueOf(y));
            year.setLetterSpacing(current ? -0.01f : 0);
            year.setIncludeFontPadding(false);
            tnum(year);
            year.setPadding(0, dp(this, 28), 0, dp(this, 8));
            body.addView(year);
            body.addView(new YearGrid(this, y, r), new LinearLayout.LayoutParams(-1, -2));
        }
    }

    /** The one bold element: a lavender-tinted plate with the level in large round numerals. */
    private View levelPlate(Engine.Result r) {
        LinearLayout plate = column(this);
        plate.setBackground(shape(this, PLATE, 32));
        int pad = dp(this, 24);
        plate.setPadding(pad, pad, pad, pad);
        plate.setContentDescription("Level " + r.level);

        LinearLayout lvRow = new LinearLayout(this);
        lvRow.setBaselineAligned(true);
        TextView lv = text(this, 22, PLATE_TEXT2, SEMI);
        lv.setText("Lv");
        LinearLayout.LayoutParams lvLp = new LinearLayout.LayoutParams(-2, -2);
        lvLp.rightMargin = dp(this, 6);
        lvRow.addView(lv, lvLp);
        TextView level = text(this, 96, TEXT, XBOLD);
        level.setText(String.valueOf(r.level));
        level.setLetterSpacing(-0.02f);
        level.setIncludeFontPadding(false);
        tnum(level);
        lvRow.addView(level);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-2, -2);
        if (fontLoaded) {
            // Trim Geologica's empty ascent/descent around the digits (metrics: ascent .975em, cap .70em, descent .275em).
            float size = level.getTextSize();
            rowLp.topMargin = -Math.round(0.24f * size);
            rowLp.bottomMargin = -Math.round(0.20f * size);
        }
        plate.addView(lvRow, rowLp);

        XpBar bar = new XpBar(this, true);
        bar.set((float) r.into / r.need, 0);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(-1, dp(this, 12));
        barLp.topMargin = dp(this, 16);
        plate.addView(bar, barLp);

        TextView caption = text(this, 14, PLATE_TEXT2, MED);
        caption.setText(r.into + " από " + r.need + " XP για το Lv " + (r.level + 1));
        tnum(caption);
        LinearLayout.LayoutParams capLp = new LinearLayout.LayoutParams(-2, -2);
        capLp.topMargin = dp(this, 12);
        plate.addView(caption, capLp);
        return plate;
    }

    /** position: 0 first, 1 middle, 2 last — a connected group with tight inner corners. */
    private void stat(LinearLayout parent, int value, String label, int position) {
        LinearLayout tile = column(this);
        tile.setGravity(Gravity.CENTER);
        tile.setMinimumHeight(dp(this, 76));
        tile.setPadding(dp(this, 4), dp(this, 8), dp(this, 4), dp(this, 8));
        tile.setBackground(position == 0 ? shape(this, CARD, 24, 8, 8, 24)
                : position == 2 ? shape(this, CARD, 8, 24, 24, 8) : shape(this, CARD, 8));
        TextView v = text(this, 22, TEXT, XBOLD);
        v.setText(String.valueOf(value));
        v.setGravity(Gravity.CENTER);
        tnum(v);
        TextView l = text(this, 12, TEXT2, MED);
        l.setText(label);
        l.setGravity(Gravity.CENTER);
        autosize(l, 10, 12);
        tile.addView(v, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-1, lineBox(this, 12));
        llp.topMargin = dp(this, 2);
        tile.addView(l, llp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        if (position > 0) lp.leftMargin = dp(this, 3);
        parent.addView(tile, lp);
    }

    private void legend(LinearLayout parent, int color, boolean ring, String label) {
        View dot = new View(this);
        int size = dp(this, 10);
        if (ring) {
            android.graphics.drawable.GradientDrawable g = oval(0);
            g.setStroke(Math.max(1, Math.round(1.5f * getResources().getDisplayMetrics().density)), color);
            dot.setBackground(g);
        } else {
            dot.setBackground(oval(color));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        if (parent.getChildCount() > 0) lp.leftMargin = dp(this, 18);
        parent.addView(dot, lp);
        TextView l = text(this, 13, TEXT2, MED);
        l.setText(label);
        l.setPadding(dp(this, 6), 0, 0, 0);
        parent.addView(l);
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "Επεξεργασία");
        menu.getMenu().add(0, 2, 0, "Διαγραφή");
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == 1) {
                    TaskEditor.show(TaskActivity.this, tasks, task, new Runnable() {
                        @Override
                        public void run() {
                            load(); // the sheet may hold the list from before the last onResume
                        }
                    });
                } else {
                    confirmDelete();
                }
                return true;
            }
        });
        menu.show();
    }

    private void confirmDelete() {
        AlertDialog d = dialog(this, "Διαγραφή «" + task.name + "»;")
                .setMessage("Θα χαθούν το level και όλο το ιστορικό του.")
                .setPositiveButton("Διαγραφή", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        int at = tasks.indexOf(task);
                        tasks.remove(at);
                        if (!Store.save(TaskActivity.this, tasks)) {
                            tasks.add(at, task);
                            toast(TaskActivity.this, "Δεν διαγράφηκε. Έλεγξε τον ελεύθερο χώρο του κινητού.");
                            return;
                        }
                        finish();
                    }
                })
                .setNegativeButton("Άκυρο", null)
                .show();
        d.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(DESTRUCTIVE_TEXT);
    }
}
