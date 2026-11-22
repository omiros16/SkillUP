package com.omiros.skillup;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import java.time.LocalDate;

/** One year as dots: a column per week (Monday on top), coloured by the day's state. */
final class YearGrid extends View {
    private static final String[] MONTHS = {"Ιαν", "Φεβ", "Μαρ", "Απρ", "Μαΐ", "Ιουν", "Ιουλ", "Αυγ", "Σεπ", "Οκτ", "Νοε", "Δεκ"};

    private final int year;
    private final Engine.Result result;
    private final LocalDate jan1;
    private final int offset; // Monday-based weekday of 1 January
    private final int cols;
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float labelHeight;

    YearGrid(Context c, int year, Engine.Result result) {
        super(c);
        this.year = year;
        this.result = result;
        jan1 = LocalDate.of(year, 1, 1);
        offset = jan1.getDayOfWeek().getValue() - 1;
        cols = (offset + jan1.lengthOfYear() - 1) / 7 + 1;
        label.setColor(Ui.TEXT3);
        label.setTypeface(Ui.MED);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        float cell = (float) w / cols;
        // Month names shrink on narrow screens / large font so they never collide.
        label.setTextSize(Math.min(Ui.sp(getContext(), 11), 1.55f * cell));
        labelHeight = label.getTextSize() * 1.9f;
        setMeasuredDimension(w, Math.round(labelHeight + 7 * cell));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cell = (float) getWidth() / cols;
        float r = cell * 0.41f;

        for (int m = 0; m < 12; m++) {
            int col = (offset + LocalDate.of(year, m + 1, 1).getDayOfYear() - 1) / 7;
            float x = Math.min(col * cell + (cell - 2 * r) / 2, getWidth() - label.measureText(MONTHS[m]));
            canvas.drawText(MONTHS[m], x, label.getTextSize(), label);
        }

        float minStroke = Ui.dp(getContext(), 1), openStroke = Ui.dp(getContext(), 1.5f);
        int days = jan1.lengthOfYear();
        for (int i = 0; i < days; i++) {
            int slot = offset + i;
            float cx = (slot / 7) * cell + cell / 2;
            float cy = labelHeight + (slot % 7) * cell + cell / 2;
            switch (result.state(jan1.plusDays(i))) {
                case Engine.S_DONE:
                    fillDot(canvas, cx, cy, r, Ui.LAVENDER);
                    break;
                case Engine.S_BARELY:
                    fillDot(canvas, cx, cy, r, Ui.BARELY_DOT);
                    break;
                case Engine.S_SKIP:
                    fillDot(canvas, cx, cy, r, Ui.SKIP_DOT);
                    break;
                case Engine.S_REST:
                    ring(canvas, cx, cy, r, Math.max(minStroke, 0.34f * r), Ui.REST_RING);
                    break;
                case Engine.S_OPEN:
                    ring(canvas, cx, cy, r, Math.max(openStroke, 0.42f * r), Ui.LAVENDER);
                    break;
                default: // before the task existed, or still to come
                    fillDot(canvas, cx, cy, r, Ui.EMPTY_DOT);
            }
        }
    }

    private void fillDot(Canvas canvas, float cx, float cy, float r, int color) {
        dot.setStyle(Paint.Style.FILL);
        dot.setColor(color);
        canvas.drawCircle(cx, cy, r, dot);
    }

    /** Ring whose outer edge matches a full dot. */
    private void ring(Canvas canvas, float cx, float cy, float r, float stroke, int color) {
        dot.setStyle(Paint.Style.STROKE);
        dot.setStrokeWidth(stroke);
        dot.setColor(color);
        canvas.drawCircle(cx, cy, r - stroke / 2, dot);
    }
}
