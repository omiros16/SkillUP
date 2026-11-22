package com.omiros.skillup;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.PathInterpolator;

/**
 * Lavender progress bar: fill, a small gap, the remaining track and a stop dot at its end.
 * Animates when XP changes; the system "remove animations" setting jumps straight to the end.
 */
final class XpBar extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float gap, stopRadius;
    private float fraction;

    /** {@code onPlate}: the larger bar on the task page's level plate. */
    XpBar(Context c, boolean onPlate) {
        super(c);
        track.setColor(onPlate ? Ui.TRACK_PLATE : Ui.TRACK);
        fill.setColor(Ui.LAVENDER);
        gap = Ui.dp(c, onPlate ? 4 : 3);
        stopRadius = Ui.dp(c, 2);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** {@code from} < 0: no animation. */
    void set(float to, float from) {
        if (from < 0 || from == to) {
            fraction = to;
            invalidate();
            return;
        }
        ValueAnimator a = ValueAnimator.ofFloat(from, to);
        a.setDuration(600);
        a.setInterpolator(new PathInterpolator(0.05f, 0.7f, 0.1f, 1f));
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator va) {
                fraction = (float) va.getAnimatedValue();
                invalidate();
            }
        });
        a.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight(), r = h / 2;
        float f = Math.max(0, Math.min(1, fraction));
        float trackStart = 0;
        if (f > 0) {
            float end = Math.max(h, w * f);
            rect.set(0, 0, end, h);
            canvas.drawRoundRect(rect, r, r, fill);
            trackStart = end + gap;
        }
        if (w - trackStart >= h) {
            rect.set(trackStart, 0, w, h);
            canvas.drawRoundRect(rect, r, r, track);
            canvas.drawCircle(w - r, r, stopRadius, fill);
        }
    }
}
