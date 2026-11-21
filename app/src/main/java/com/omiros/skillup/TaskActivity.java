package com.omiros.skillup;

import static com.omiros.skillup.Ui.*;

import android.app.Activity;
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

/** One task: level, stats and a dot grid per year. */
public class TaskActivity extends Activity {
    private List<Engine.Task> tasks;
    private Engine.Task task;
    private TextView title;
    private LinearLayout body;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = column(this);
        root.setBackgroundColor(BLACK);

        LinearLayout header = row(this);
        header.setPadding(dp(this, 4), dp(this, 8), dp(this, 6), 0);
        ImageView back = iconButton(this, R.drawable.ic_back, "Πίσω");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        header.addView(back);
        title = text(this, 20, TEXT, MEDIUM);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(dp(this, 4), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
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
        body = column(this);
        body.setPadding(dp(this, 20), dp(this, 8), dp(this, 20), dp(this, 40));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
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

        LinearLayout hero = row(this);
        hero.setGravity(Gravity.BOTTOM);
        TextView lv = text(this, 20, DIM, NUM);
        lv.setText("Lv ");
        TextView level = text(this, 64, TEXT, NUM);
        level.setText(String.valueOf(r.level));
        level.setIncludeFontPadding(false);
        hero.addView(lv);
        hero.addView(level);
        hero.setContentDescription("Level " + r.level);
        body.addView(hero);

        XpBar bar = new XpBar(this);
        bar.set((float) r.into / r.need, 0);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(-1, dp(this, 10));
        barLp.topMargin = dp(this, 12);
        body.addView(bar, barLp);
        TextView toNext = text(this, 13, DIM, null);
        toNext.setText(r.into + " από " + r.need + " XP για το Lv " + (r.level + 1));
        toNext.setPadding(0, dp(this, 8), 0, 0);
        body.addView(toNext);

        LinearLayout stats = row(this);
        stats.setPadding(0, dp(this, 28), 0, 0);
        stat(stats, r.streak, "σερί");
        stat(stats, r.best, "καλύτερο");
        stat(stats, r.done, "done");
        stat(stats, r.barely, "barely");
        stat(stats, r.skips, "skip");
        body.addView(stats);

        Engine.Sched s = task.current();
        String plan = Ui.schedule(s) + "\n" + xpRule(s) + (task.minimum.isEmpty() ? "" : "\nΕλάχιστο: " + task.minimum);
        TextView planView = text(this, 14, DIM, null);
        planView.setText(plan);
        planView.setLineSpacing(0, 1.3f);
        planView.setPadding(0, dp(this, 24), 0, 0);
        body.addView(planView);

        LinearLayout legend = row(this);
        legend.setPadding(0, dp(this, 28), 0, 0);
        legend(legend, LAVENDER, false, "done");
        legend(legend, LAVENDER_DIM, false, "barely");
        legend(legend, WINE, false, "skip");
        legend(legend, REST, true, "ξεκούραση");
        body.addView(legend);

        for (int y = today.getYear(); y >= task.created().getYear(); y--) {
            TextView year = text(this, 22, TEXT, NUM);
            year.setText(String.valueOf(y));
            year.setPadding(0, dp(this, 24), 0, dp(this, 8));
            body.addView(year);
            body.addView(new YearGrid(this, y, r), new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void stat(LinearLayout parent, int value, String label) {
        LinearLayout c = column(this);
        TextView v = text(this, 24, TEXT, NUM);
        v.setText(String.valueOf(value));
        TextView l = text(this, 12, DIM, null);
        l.setText(label);
        c.addView(v);
        c.addView(l);
        parent.addView(c, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private void legend(LinearLayout parent, int color, boolean ring, String label) {
        View dot = new View(this);
        int size = dp(this, 10);
        dot.setBackground(ring ? round(0, color, dp(this, 2), size) : round(color, 0, 0, size));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        if (parent.getChildCount() > 0) lp.leftMargin = dp(this, 16);
        parent.addView(dot, lp);
        TextView l = text(this, 13, DIM, null);
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
                            render();
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
        dialog(this)
                .setTitle("Διαγραφή «" + task.name + "»;")
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
    }
}
