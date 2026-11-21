package com.omiros.skillup;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Rounded lavender progress bar; animates when XP changes (the system "remove animations" setting skips it). */
final class XpBar extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float fraction;

    XpBar(Context c) {
        super(c);
        track.setColor(Ui.TRACK);
        fill.setColor(Ui.LAVENDER);
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
        a.setDuration(500);
        a.setInterpolator(new DecelerateInterpolator());
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
        float h = getHeight(), r = h / 2;
        rect.set(0, 0, getWidth(), h);
        canvas.drawRoundRect(rect, r, r, track);
        if (fraction <= 0) return;
        rect.right = Math.max(h, getWidth() * Math.min(1, fraction));
        canvas.drawRoundRect(rect, r, r, fill);
    }
}
