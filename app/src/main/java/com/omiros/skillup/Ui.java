package com.omiros.skillup;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Design tokens ("soft tonal night"), the bundled typeface and the view helpers both screens share. */
final class Ui {
    // Surfaces: true black, then soft tonal steps. No outlines on resting controls.
    static final int BG = 0xFF000000;
    static final int CARD = 0xFF17151E;
    static final int CTRL = 0xFF272431;
    static final int SHEET = 0xFF1C1A24;
    static final int PLATE = 0xFF1C1829;
    static final int TRACK = 0xFF2C2839;
    static final int TRACK_PLATE = 0xFF332D48;
    // Text.
    static final int TEXT = 0xFFE6E3EE;
    static final int TEXT2 = 0xFFA29EB0;
    static final int TEXT3 = 0xFF8C8898;
    static final int PLATE_TEXT2 = 0xFFB3ABD6;
    // Lavender family.
    static final int LAVENDER = 0xFFA497D9;
    static final int ON_LAVENDER = 0xFF1A1530;
    static final int ON_LAVENDER_2 = 0xFF2E2650;
    static final int LAV_TEXT = 0xFFC4BAF0;
    static final int LAV_CONTAINER = 0xFF433B66;
    static final int ON_LAV_CONTAINER = 0xFFECE9F5;
    static final int ON_LAV_CONTAINER_2 = 0xFFCFC7F2;
    // Skip and rest.
    static final int WINE = 0xFF6E2F3C;
    static final int ON_WINE = 0xFFF6CCD4;
    static final int ON_WINE_2 = 0xFFEBB3BE;
    static final int WINE_CAPTION = 0xFFD7A2AE;
    static final int REST_SEL = 0xFF403B50;
    // Year grid.
    static final int BARELY_DOT = 0xFF685E96;
    static final int SKIP_DOT = 0xFF8E3A4C;
    static final int REST_RING = 0xFF5A5668;
    static final int EMPTY_DOT = 0xFF1F1D27;
    // Misc.
    static final int DISABLED = 0xFF2B2836;
    static final int ON_DISABLED = 0xFF77738A;
    static final int DESTRUCTIVE_TEXT = 0xFFF0A8B6;
    static final int RIPPLE = 0x1FFFFFFF;
    static final int RIPPLE_ON_LAV = 0x1F1A1530;

    /** Geologica 400 / 500 / 600 / 800; system fallbacks until {@link #init} succeeds. */
    static Typeface REG = Typeface.create("sans-serif", Typeface.NORMAL);
    static Typeface MED = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    static Typeface SEMI = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    static Typeface XBOLD = Typeface.create("sans-serif", Typeface.BOLD);
    static boolean fontLoaded;

    static final String[] DAY_SHORT = {"Δευ", "Τρί", "Τετ", "Πέμ", "Παρ", "Σάβ", "Κυρ"};
    static final String[] DAY_LETTER = {"Δ", "Τ", "Τ", "Π", "Π", "Σ", "Κ"};
    static final String[] DAY_FULL = {"Δευτέρα", "Τρίτη", "Τετάρτη", "Πέμπτη", "Παρασκευή", "Σάββατο", "Κυριακή"};

    private Ui() {
    }

    static void init(Context c) {
        if (fontLoaded) return;
        try {
            REG = c.getResources().getFont(R.font.geologica_regular);
            MED = c.getResources().getFont(R.font.geologica_medium);
            SEMI = c.getResources().getFont(R.font.geologica_semibold);
            XBOLD = c.getResources().getFont(R.font.geologica_extrabold);
            fontLoaded = true;
        } catch (RuntimeException e) {
            // keep the system fallbacks
        }
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static float sp(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, c.getResources().getDisplayMetrics());
    }

    static TextView text(Context c, float sp, int color, Typeface tf) {
        TextView tv = new TextView(c);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTextColor(color);
        tv.setTypeface(tf != null ? tf : REG);
        return tv;
    }

    /** Tabular figures, so numbers keep their width as they change. */
    static void tnum(TextView tv) {
        tv.setFontFeatureSettings("'tnum'");
    }

    static void lineHeight(TextView tv, float sp) {
        int px = Math.round(sp(tv.getContext(), sp));
        if (Build.VERSION.SDK_INT >= 28) {
            tv.setLineHeight(px);
        } else {
            tv.setLineSpacing(px - tv.getPaint().getFontMetricsInt(null), 1f);
        }
    }

    /** Autosize needs an exact box: MATCH_PARENT width and a fixed height of one line. */
    static void autosize(TextView tv, int minSp, int maxSp) {
        tv.setMaxLines(1);
        tv.setAutoSizeTextTypeUniformWithConfiguration(minSp, maxSp, 1, TypedValue.COMPLEX_UNIT_SP);
    }

    static int lineBox(Context c, float sp) {
        return (int) Math.ceil(1.25f * sp(c, sp));
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

    /** Rounded rectangle; radii in dp, either one value or [TL, TR, BR, BL]. */
    static GradientDrawable shape(Context c, int fill, float... radii) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        float[] r = new float[8];
        for (int i = 0; i < 4; i++) {
            float v = dp(c, radii.length == 1 ? radii[0] : radii[i]);
            r[2 * i] = v;
            r[2 * i + 1] = v;
        }
        g.setCornerRadii(r);
        return g;
    }

    /** Pressable rounded shape; the ripple is masked to the same shape so it never bleeds into gaps. */
    static Drawable press(Context c, int fill, int ripple, float... radii) {
        return new RippleDrawable(ColorStateList.valueOf(ripple),
                fill == 0 ? null : shape(c, fill, radii), shape(c, 0xFFFFFFFF, radii));
    }

    static GradientDrawable oval(int fill) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(fill);
        return g;
    }

    static Drawable pressOval(int fill, int ripple) {
        return new RippleDrawable(ColorStateList.valueOf(ripple), fill == 0 ? null : oval(fill), oval(0xFFFFFFFF));
    }

    /** A smaller visual inside a 48dp touch target. */
    static Drawable inset(Context c, Drawable d, float horizontalDp, float verticalDp) {
        int h = dp(c, horizontalDp), v = dp(c, verticalDp);
        return new InsetDrawable(d, h, v, h, v);
    }

    static ImageView iconButton(Context c, int res, String description) {
        ImageView iv = new ImageView(c);
        iv.setImageResource(res);
        iv.setImageTintList(ColorStateList.valueOf(TEXT));
        iv.setScaleType(ImageView.ScaleType.CENTER);
        iv.setBackground(pressOval(0, RIPPLE));
        iv.setContentDescription(description);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 48), dp(c, 48)));
        return iv;
    }

    static float measure(Context c, Typeface tf, float sp, boolean tabular, String s) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(tf);
        p.setTextSize(sp(c, sp));
        if (tabular) p.setFontFeatureSettings("'tnum'");
        return p.measureText(s);
    }

    static AlertDialog.Builder dialog(Context c, String title) {
        TextView t = text(c, 20, TEXT, SEMI);
        t.setText(title);
        t.setPadding(dp(c, 24), dp(c, 24), dp(c, 24), 0);
        // A custom title keeps the weight; the theme font would flatten the framework title to 400.
        return new AlertDialog.Builder(c, R.style.DialogTheme).setCustomTitle(t);
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

    static String avoidRule() {
        return "Καθαρή μέρα +" + Engine.AVOID_CLEAN_XP + " (μπαίνει τα μεσάνυχτα), το έκανα −" + Engine.AVOID_SLIP_XP;
    }

    /**
     * Geologica's accented capitals (Ό, Ή, Έ…) overhang to the left, and TextView clips at its padding, which cuts
     * the tonos of left-aligned text. A transparent shadow widens TextView's clip; with {@code pad} the text also
     * moves right by the same amount. Returns those px, for the caller to take off the start margin so the
     * text stays where it was.
     */
    static int overhang(TextView tv, boolean pad) {
        int o = dp(tv.getContext(), 3);
        if (pad) tv.setPaddingRelative(tv.getPaddingStart() + o, tv.getPaddingTop(), tv.getPaddingEnd(), tv.getPaddingBottom());
        tv.setShadowLayer(o, 0, 0, Color.TRANSPARENT);
        return pad ? o : 0;
    }

    static String xpRule(Engine.Sched s) {
        return "Done +" + s.doneXp() + ", barely +" + s.barelyXp() + ", skip −" + s.doneXp();
    }
}
