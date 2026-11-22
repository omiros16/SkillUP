package com.omiros.skillup;

import static com.omiros.skillup.Ui.*;

import android.app.Activity;
import android.app.Dialog;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalDate;
import java.util.List;

/** Create / edit sheet: name, bare minimum, schedule. A floating bottom sheet with rounded corners. */
final class TaskEditor {
    private TaskEditor() {
    }

    /** {@code task} null = new task. Saves {@code tasks} and runs {@code onSaved}. */
    static void show(final Activity a, final List<Engine.Task> tasks, final Engine.Task task, final Runnable onSaved) {
        final boolean isNew = task == null;
        Engine.Sched cur = isNew ? null : task.current();
        // [0] days bitmask, [1] times per week, [2] 1 = weekly mode, [3] 1 = avoid task (chosen only at creation)
        final int[] st = {
                cur == null || cur.weekly() ? 0x7F : cur.days,
                cur != null && cur.weekly() ? cur.times : 3,
                cur != null && cur.weekly() ? 1 : 0,
                !isNew && task.avoid ? 1 : 0};

        final Dialog dialog = new Dialog(a, R.style.SheetTheme);

        LinearLayout form = column(a);
        form.setPadding(dp(a, 24), dp(a, 24), dp(a, 24), 0);
        TextView title = text(a, 24, TEXT, SEMI);
        title.setText(isNew ? "Νέο task" : "Επεξεργασία");
        form.addView(title);

        // Kind: something to do, or something to avoid. Fixed once created (it changes what past days mean).
        final TextView toDo = modeToggle(a, "Να κάνω");
        final TextView toAvoid = modeToggle(a, "Να αποφεύγω");
        if (isNew) {
            LinearLayout kinds = row(a);
            kinds.addView(toDo, new LinearLayout.LayoutParams(0, dp(a, 48), 1));
            LinearLayout.LayoutParams kGap = new LinearLayout.LayoutParams(0, dp(a, 48), 1);
            kGap.leftMargin = dp(a, 3);
            kinds.addView(toAvoid, kGap);
            LinearLayout.LayoutParams kindsLp = new LinearLayout.LayoutParams(-1, -2);
            kindsLp.topMargin = dp(a, 16);
            form.addView(kinds, kindsLp);
        }

        final EditText name = field(a, form, "Όνομα", "π.χ. Διάβασμα", isNew ? "" : task.name);
        final LinearLayout minBox = column(a);
        final EditText min = field(a, minBox, "Ελάχιστο (barely)", "π.χ. 5 λεπτά", isNew ? "" : task.minimum);
        form.addView(minBox);

        // Schedule (hidden for avoid tasks: they apply every day).
        final LinearLayout schedBox = column(a);
        form.addView(schedBox);
        schedBox.addView(label(a, "Πότε", 20));
        LinearLayout modes = row(a);
        final TextView byDays = modeToggle(a, "Ορισμένες μέρες");
        final TextView byTimes = modeToggle(a, "Φορές/εβδομάδα");
        modes.addView(byDays, new LinearLayout.LayoutParams(0, dp(a, 48), 1));
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(0, dp(a, 48), 1);
        gap.leftMargin = dp(a, 3);
        modes.addView(byTimes, gap);
        schedBox.addView(modes);

        // Day toggles: 48dp slots carry the click; the visual inside is a squircle (off) or a circle (on).
        final LinearLayout dayRow = row(a);
        final FrameLayout[] slots = new FrameLayout[7];
        final TextView[] dayViews = new TextView[7];
        int contentWidth = a.getResources().getDisplayMetrics().widthPixels - dp(a, 64);
        int size = Math.min(dp(a, 44), contentWidth / 7 - dp(a, 4));
        for (int i = 0; i < 7; i++) {
            FrameLayout slot = new FrameLayout(a);
            slot.setClickable(true);
            slot.setContentDescription(DAY_FULL[i]);
            TextView d = text(a, 15, TEXT2, MED);
            d.setText(DAY_LETTER[i]);
            d.setGravity(Gravity.CENTER);
            d.setDuplicateParentStateEnabled(true);
            d.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            slot.addView(d, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
            dayRow.addView(slot, new LinearLayout.LayoutParams(0, dp(a, 48), 1));
            slots[i] = slot;
            dayViews[i] = d;
        }
        LinearLayout.LayoutParams dayLp = new LinearLayout.LayoutParams(-1, dp(a, 48));
        dayLp.topMargin = dp(a, 16);
        schedBox.addView(dayRow, dayLp);

        final LinearLayout timesRow = row(a);
        TextView minus = stepper(a, "−", "Λιγότερες φορές");
        final TextView count = text(a, 28, TEXT, XBOLD);
        count.setGravity(Gravity.CENTER);
        tnum(count);
        TextView plus = stepper(a, "+", "Περισσότερες φορές");
        TextView per = text(a, 15, TEXT2, null);
        per.setText("φορές την εβδομάδα");
        timesRow.addView(minus);
        timesRow.addView(count, new LinearLayout.LayoutParams(dp(a, 56), -2));
        timesRow.addView(plus);
        LinearLayout.LayoutParams perLp = new LinearLayout.LayoutParams(-2, -2);
        perLp.leftMargin = dp(a, 12);
        timesRow.addView(per, perLp);
        LinearLayout.LayoutParams timesLp = new LinearLayout.LayoutParams(-1, -2);
        timesLp.topMargin = dp(a, 16);
        schedBox.addView(timesRow, timesLp);

        final TextView preview = text(a, 13, LAV_TEXT, MED);
        preview.setPadding(dp(a, 4), 0, 0, 0);
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(-1, -2);
        previewLp.topMargin = dp(a, 16);
        form.addView(preview, previewLp);

        // Buttons stay outside the scroll view, so they remain visible above the keyboard.
        LinearLayout buttons = row(a);
        buttons.setPadding(dp(a, 24), dp(a, 24), dp(a, 24), dp(a, 20));
        TextView cancel = sheetButton(a, "Άκυρο");
        cancel.setBackground(press(a, CTRL, RIPPLE, 26));
        cancel.setTextColor(TEXT);
        final TextView save = sheetButton(a, isNew ? "Δημιουργία" : "Αποθήκευση");
        buttons.addView(cancel, new LinearLayout.LayoutParams(0, dp(a, 52), 1));
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(0, dp(a, 52), 1);
        saveLp.leftMargin = dp(a, 12);
        buttons.addView(save, saveLp);

        LinearLayout sheet = column(a);
        sheet.setBackground(shape(a, SHEET, 28));
        ScrollView scroll = new ScrollView(a);
        scroll.addView(form);
        sheet.addView(scroll, new LinearLayout.LayoutParams(-1, -2, 1));
        sheet.addView(buttons, new LinearLayout.LayoutParams(-1, -2));
        FrameLayout root = new FrameLayout(a);
        root.setPadding(dp(a, 8), 0, dp(a, 8), dp(a, 8));
        root.addView(sheet, new FrameLayout.LayoutParams(-1, -2));

        final Runnable refresh = new Runnable() {
            @Override
            public void run() {
                boolean weekly = st[2] == 1, avoid = st[3] == 1;
                styleMode(a, toDo, !avoid, true);
                styleMode(a, toAvoid, avoid, false);
                minBox.setVisibility(avoid ? View.GONE : View.VISIBLE);
                schedBox.setVisibility(avoid ? View.GONE : View.VISIBLE);
                name.setHint(avoid ? "π.χ. Όχι shorts" : "π.χ. Διάβασμα");
                styleMode(a, byDays, !weekly, true);
                styleMode(a, byTimes, weekly, false);
                dayRow.setVisibility(weekly ? View.GONE : View.VISIBLE);
                timesRow.setVisibility(weekly ? View.VISIBLE : View.GONE);
                for (int i = 0; i < 7; i++) {
                    boolean on = (st[0] & (1 << i)) != 0;
                    dayViews[i].setBackground(on ? pressOval(LAVENDER, RIPPLE_ON_LAV) : press(a, CTRL, RIPPLE, 14));
                    dayViews[i].setTextColor(on ? ON_LAVENDER : TEXT2);
                    dayViews[i].setTypeface(on ? SEMI : MED);
                    slots[i].setSelected(on);
                }
                count.setText(String.valueOf(st[1]));
                boolean anyDay = avoid || weekly || st[0] != 0;
                preview.setText(avoid ? avoidRule() : anyDay
                        ? xpRule(new Engine.Sched(LocalDate.now(), st[0], weekly ? st[1] : 0))
                        : "Διάλεξε τουλάχιστον μία μέρα.");
                preview.setTextColor(anyDay ? LAV_TEXT : WINE_CAPTION);
                boolean valid = name.getText().toString().trim().length() > 0 && anyDay;
                save.setEnabled(valid);
                save.setBackground(valid ? press(a, LAVENDER, RIPPLE_ON_LAV, 26) : shape(a, DISABLED, 26));
                save.setTextColor(valid ? ON_LAVENDER : ON_DISABLED);
            }
        };
        for (int i = 0; i < 7; i++) {
            final int bit = 1 << i;
            slots[i].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    st[0] ^= bit;
                    refresh.run();
                }
            });
        }
        toDo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                st[3] = 0;
                refresh.run();
            }
        });
        toAvoid.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                st[3] = 1;
                refresh.run();
            }
        });
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
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LocalDate today = LocalDate.now();
                boolean avoid = st[3] == 1, weekly = !avoid && st[2] == 1;
                int days = avoid ? 0x7F : weekly ? 0 : st[0], times = weekly ? st[1] : 0;
                Engine.Task t = task;
                if (isNew) {
                    t = new Engine.Task();
                    t.id = System.currentTimeMillis();
                    t.avoid = avoid;
                    t.scheds.add(new Engine.Sched(today, days, times));
                    tasks.add(t);
                } else {
                    t.setSchedule(today, days, times);
                }
                t.name = name.getText().toString().trim();
                t.minimum = avoid ? "" : min.getText().toString().trim();
                if (!Store.save(a, tasks)) {
                    if (isNew) tasks.remove(t); // so a retry does not add it twice
                    toast(a, "Δεν αποθηκεύτηκε. Έλεγξε τον ελεύθερο χώρο του κινητού.");
                    return;
                }
                dialog.dismiss();
                onSaved.run();
            }
        });

        refresh.run();
        dialog.setContentView(root);
        dialog.setCanceledOnTouchOutside(true);
        Window w = dialog.getWindow();
        w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        w.setGravity(Gravity.BOTTOM);
        w.getDecorView().setPadding(0, 0, 0, 0);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | (isNew
                ? WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE : WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN));
        dialog.show();
    }

    private static EditText field(Activity a, LinearLayout form, String title, String hint, String value) {
        form.addView(label(a, title, 16));
        // The 4-arg constructor applies the filled style; the Activity context does not carry the sheet's theme.
        EditText e = new EditText(a, null, 0, R.style.SheetField);
        e.setTypeface(REG);
        e.setHint(hint);
        e.setText(value);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});
        e.setSelection(e.getText().length());
        overhang(e, false); // 16dp padding already leaves room; only the clip needs widening
        form.addView(e, new LinearLayout.LayoutParams(-1, dp(a, 56)));
        return e;
    }

    private static TextView label(Activity a, String s, int topDp) {
        TextView tv = text(a, 13, TEXT2, MED);
        tv.setText(s);
        tv.setPadding(dp(a, 4), dp(a, topDp), 0, dp(a, 8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMarginStart(-overhang(tv, true));
        tv.setLayoutParams(lp);
        return tv;
    }

    private static TextView modeToggle(Activity a, String s) {
        TextView tv = text(a, 14, TEXT2, MED);
        tv.setText(s);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(a, 12), 0, dp(a, 12), 0);
        autosize(tv, 11, 14);
        return tv;
    }

    /** Connected pair; the selected half becomes a full pill and heavier. */
    private static void styleMode(Activity a, TextView tv, boolean on, boolean first) {
        float[] corners = on ? new float[]{24} : first ? new float[]{24, 8, 8, 24} : new float[]{8, 24, 24, 8};
        tv.setBackground(press(a, on ? LAV_CONTAINER : CTRL, RIPPLE, corners));
        tv.setPadding(dp(a, 12), 0, dp(a, 12), 0);
        tv.setTextColor(on ? ON_LAV_CONTAINER : TEXT2);
        tv.setTypeface(on ? SEMI : MED);
        tv.setSelected(on);
    }

    private static TextView stepper(Activity a, String s, String description) {
        TextView tv = text(a, 22, TEXT, MED);
        tv.setText(s);
        tv.setGravity(Gravity.CENTER);
        tv.setContentDescription(description);
        tv.setBackground(pressOval(CTRL, RIPPLE));
        tv.setLayoutParams(new LinearLayout.LayoutParams(dp(a, 48), dp(a, 48)));
        return tv;
    }

    private static TextView sheetButton(Activity a, String s) {
        TextView tv = text(a, 15, TEXT, SEMI);
        tv.setText(s);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        return tv;
    }
}
