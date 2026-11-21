package com.omiros.skillup;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Colours, type and the few view helpers both screens share. */
final class Ui {
    static final int BLACK = 0xFF000000;
    static final int DIALOG = 0xFF16141C;
    static final int TRACK = 0xFF1E1C27;
    static final int LINE = 0xFF19171F;
    static final int TEXT = 0xFFEDEBF3;
    static final int DIM = 0xFF8C8898;
    static final int FAINT = 0xFF2E2B38;
    static final int LAVENDER = 0xFFA497D9;
    static final int LAVENDER_DIM = 0xFF5F5784;
    static final int BARELY_BG = 0xFF3A3456;
    static final int WINE = 0xFF6E2F3C;
    static final int WINE_TEXT = 0xFFF0B9C3;
    static final int REST = 0xFF3A3746;
    static final int REST_BG = 0xFF201E28;
    static final int EMPTY = 0xFF141319;

    static final Typeface NUM = Typeface.create("sans-serif-condensed", Typeface.BOLD);
    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    static final String[] DAY_SHORT = {"Δευ", "Τρί", "Τετ", "Πέμ", "Παρ", "Σάβ", "Κυρ"};
    static final String[] DAY_LETTER = {"Δ", "Τ", "Τ", "Π", "Π", "Σ", "Κ"};
    static final String[] DAY_FULL = {"Δευτέρα", "Τρίτη", "Τετάρτη", "Πέμπτη", "Παρασκευή", "Σάββατο", "Κυριακή"};

    private Ui() {
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context c, float sp, int color, Typeface tf) {
        TextView tv = new TextView(c);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTextColor(color);
        if (tf != null) tv.setTypeface(tf);
        return tv;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static ImageView iconButton(Context c, int res, String description) {
        ImageView iv = new ImageView(c);
        iv.setImageResource(res);
        iv.setImageTintList(ColorStateList.valueOf(TEXT));
        iv.setScaleType(ImageView.ScaleType.CENTER);
        iv.setBackground(ripple(0, 0, dp(c, 22)));
        iv.setContentDescription(description);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 48), dp(c, 48)));
        return iv;
    }

    static GradientDrawable round(int fill, int stroke, int strokeWidth, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        if (stroke != 0) g.setStroke(strokeWidth, stroke);
        g.setCornerRadius(radius);
        return g;
    }

    /** Pressable background; fill 0 = transparent, stroke 0 = none. */
    static Drawable ripple(int fill, int stroke, float radius) {
        GradientDrawable mask = round(0xFFFFFFFF, 0, 0, radius);
        Drawable content = fill == 0 && stroke == 0 ? new ColorDrawable(0) : round(fill, stroke, 2, radius);
        return new RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), content, mask);
    }

    static AlertDialog.Builder dialog(Context c) {
        return new AlertDialog.Builder(c, R.style.DialogTheme);
    }

    static void toast(Context c, String msg) {
        Toast.makeText(c, msg, Toast.LENGTH_SHORT).show();
    }

    static String schedule(Engine.Sched s) {
        if (s.weekly()) return s.times == 1 ? "1 φορά την εβδομάδα" : s.times + " φορές την εβδομάδα";
        if (s.days == 0x7F) return "Κάθε μέρα";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if ((s.days & (1 << i)) == 0) continue;
            if (b.length() > 0) b.append(", ");
            b.append(DAY_SHORT[i]);
        }
        return b.toString();
    }

    static String xpRule(Engine.Sched s) {
        return "Done +" + s.doneXp() + ", barely +" + s.barelyXp() + ", skip −" + s.doneXp();
    }
}
