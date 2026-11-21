package com.omiros.skillup;

import static com.omiros.skillup.Ui.*;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.util.List;

/** Create / edit dialog: name, bare minimum, schedule. */
final class TaskEditor {
    private TaskEditor() {
    }

    /** {@code task} null = new task. Saves {@code tasks} and runs {@code onSaved}. */
    static void show(final Activity a, final List<Engine.Task> tasks, final Engine.Task task, final Runnable onSaved) {
        final boolean isNew = task == null;
        Engine.Sched cur = isNew ? null : task.current();
        // [0] days bitmask, [1] times per week, [2] 1 = weekly mode
        final int[] st = {
                cur == null || cur.weekly() ? 0x7F : cur.days,
                cur != null && cur.weekly() ? cur.times : 3,
                cur != null && cur.weekly() ? 1 : 0};

        LinearLayout form = column(a);
        int pad = dp(a, 22);
        form.setPadding(pad, dp(a, 8), pad, 0);

        final EditText name = field(a, form, "Όνομα", "π.χ. Διάβασμα", isNew ? "" : task.name);
        final EditText min = field(a, form, "Ελάχιστο (barely)", "π.χ. 5 λεπτά", isNew ? "" : task.minimum);

        form.addView(label(a, "Πότε"));
        LinearLayout modes = row(a);
        final TextView byDays = pill(a, "Ορισμένες μέρες");
        final TextView byTimes = pill(a, "Φορές/εβδομάδα");
        modes.addView(byDays, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(0, -2, 1);
        gap.leftMargin = dp(a, 8);
        modes.addView(byTimes, gap);
        form.addView(modes);

        final LinearLayout dayRow = row(a);
        dayRow.setPadding(0, dp(a, 14), 0, 0);
        final TextView[] dayViews = new TextView[7];
        for (int i = 0; i < 7; i++) {
            TextView d = text(a, 14, TEXT, MEDIUM);
            d.setText(DAY_LETTER[i]);
            d.setGravity(Gravity.CENTER);
            d.setContentDescription(DAY_FULL[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(a, 40), 1);
            if (i > 0) lp.leftMargin = dp(a, 6);
            dayRow.addView(d, lp);
            dayViews[i] = d;
        }
        form.addView(dayRow);

        final LinearLayout timesRow = row(a);
        timesRow.setPadding(0, dp(a, 14), 0, 0);
        TextView minus = stepper(a, "−", "Λιγότερες φορές");
        final TextView count = text(a, 22, TEXT, NUM);
        count.setGravity(Gravity.CENTER);
        TextView plus = stepper(a, "+", "Περισσότερες φορές");
        TextView per = text(a, 15, DIM, null);
        per.setText("φορές την εβδομάδα");
        per.setPadding(dp(a, 12), 0, 0, 0);
        timesRow.addView(minus);
        timesRow.addView(count, new LinearLayout.LayoutParams(dp(a, 44), -2));
        timesRow.addView(plus);
        timesRow.addView(per);
        form.addView(timesRow);

        final TextView preview = text(a, 13, DIM, null);
        preview.setPadding(0, dp(a, 16), 0, dp(a, 4));
        form.addView(preview);

        final AlertDialog dialog = dialog(a)
                .setTitle(isNew ? "Νέο task" : "Επεξεργασία")
                .setView(form)
                .setPositiveButton(isNew ? "Δημιουργία" : "Αποθήκευση", null)
                .setNegativeButton("Άκυρο", null)
                .create();

        final Runnable refresh = new Runnable() {
            @Override
            public void run() {
                boolean weekly = st[2] == 1;
                stylePill(a, byDays, !weekly);
                stylePill(a, byTimes, weekly);
                dayRow.setVisibility(weekly ? View.GONE : View.VISIBLE);
                timesRow.setVisibility(weekly ? View.VISIBLE : View.GONE);
                for (int i = 0; i < 7; i++) {
                    boolean on = (st[0] & (1 << i)) != 0;
                    dayViews[i].setBackground(on ? round(LAVENDER, 0, 0, dp(a, 20)) : round(0, FAINT, dp(a, 1), dp(a, 20)));
                    dayViews[i].setTextColor(on ? BLACK : DIM);
                    dayViews[i].setSelected(on);
                }
                count.setText(String.valueOf(st[1]));
                boolean valid = name.getText().toString().trim().length() > 0 && (weekly || st[0] != 0);
                preview.setText(weekly || st[0] != 0
                        ? xpRule(new Engine.Sched(LocalDate.now(), st[0], weekly ? st[1] : 0))
                        : "Διάλεξε τουλάχιστον μία μέρα.");
                Button save = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                if (save != null) {
                    save.setEnabled(valid);
                    save.setAlpha(valid ? 1f : 0.4f);
                }
            }
        };
        for (int i = 0; i < 7; i++) {
            final int bit = 1 << i;
            dayViews[i].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    st[0] ^= bit;
                    refresh.run();
                }
            });
        }

        byDays.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                st[2] = 0;
                refresh.run();
            }
        });
        byTimes.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                st[2] = 1;
                refresh.run();
            }
        });
        minus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                st[1] = Math.max(1, st[1] - 1);
                refresh.run();
            }
        });
        plus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                st[1] = Math.min(7, st[1] + 1);
                refresh.run();
            }
        });
        name.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refresh.run();
            }
        });

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface di) {
                refresh.run();
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        LocalDate today = LocalDate.now();
                        boolean weekly = st[2] == 1;
                        int days = weekly ? 0 : st[0], times = weekly ? st[1] : 0;
                        Engine.Task t = task;
                        if (isNew) {
                            t = new Engine.Task();
                            t.id = System.currentTimeMillis();
                            t.scheds.add(new Engine.Sched(today, days, times));
                            tasks.add(t);
                        } else {
                            t.setSchedule(today, days, times);
                        }
                        t.name = name.getText().toString().trim();
                        t.minimum = min.getText().toString().trim();
                        if (!Store.save(a, tasks)) {
                            toast(a, "Δεν αποθηκεύτηκε. Έλεγξε τον ελεύθερο χώρο του κινητού.");
                            return;
                        }
                        dialog.dismiss();
                        onSaved.run();
                    }
                });
            }
        });
        if (isNew) dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
    }

    private static EditText field(Activity a, LinearLayout form, String title, String hint, String value) {
        form.addView(label(a, title));
        EditText e = new EditText(a);
        e.setTextColor(TEXT);
        e.setHintTextColor(0xFF5C5868);
        e.setHint(hint);
        e.setText(value);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});
        e.setSelection(e.getText().length());
        form.addView(e);
        return e;
    }

    private static TextView label(Activity a, String s) {
        TextView tv = text(a, 13, DIM, null);
        tv.setText(s);
        tv.setPadding(0, dp(a, 14), 0, dp(a, 2));
        return tv;
    }

    private static TextView pill(Activity a, String s) {
        TextView tv = text(a, 14, TEXT, MEDIUM);
        tv.setText(s);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true); // before setMinHeight: it resets the height limits
        tv.setMinHeight(dp(a, 40));
        tv.setPadding(dp(a, 8), 0, dp(a, 8), 0);
        return tv;
    }

    private static void stylePill(Activity a, TextView tv, boolean on) {
        tv.setBackground(ripple(on ? BARELY_BG : 0, on ? LAVENDER : FAINT, dp(a, 20)));
        tv.setTextColor(on ? TEXT : DIM);
        tv.setSelected(on);
    }

    private static TextView stepper(Activity a, String s, String description) {
        TextView tv = text(a, 22, TEXT, null);
        tv.setText(s);
        tv.setGravity(Gravity.CENTER);
        tv.setContentDescription(description);
        tv.setBackground(ripple(TRACK, 0, dp(a, 22)));
        tv.setLayoutParams(new LinearLayout.LayoutParams(dp(a, 44), dp(a, 44)));
        return tv;
    }
}
