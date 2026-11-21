package com.omiros.skillup;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.TypedValue;
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
    private final float labelHeight;

    YearGrid(Context c, int year, Engine.Result result) {
        super(c);
        this.year = year;
        this.result = result;
        jan1 = LocalDate.of(year, 1, 1);
        offset = jan1.getDayOfWeek().getValue() - 1;
        cols = (offset + jan1.lengthOfYear() - 1) / 7 + 1;
        label.setColor(Ui.DIM);
        label.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10, getResources().getDisplayMetrics()));
        labelHeight = label.getTextSize() * 1.8f;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(w, Math.round(labelHeight + 7 * (float) w / cols));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cell = (float) getWidth() / cols;
        float radius = cell * 0.36f;

        for (int m = 0; m < 12; m++) {
            int col = (offset + LocalDate.of(year, m + 1, 1).getDayOfYear() - 1) / 7;
            float x = Math.min(col * cell, getWidth() - label.measureText(MONTHS[m]));
            canvas.drawText(MONTHS[m], x, label.getTextSize(), label);
        }

        int days = jan1.lengthOfYear();
        for (int i = 0; i < days; i++) {
            int slot = offset + i;
            float cx = (slot / 7) * cell + cell / 2;
            float cy = labelHeight + (slot % 7) * cell + cell / 2;
            byte state = result.state(jan1.plusDays(i));
            switch (state) {
                case Engine.S_DONE:
                    fillDot(canvas, cx, cy, radius, Ui.LAVENDER);
                    break;
                case Engine.S_BARELY:
                    fillDot(canvas, cx, cy, radius, Ui.LAVENDER_DIM);
                    break;
                case Engine.S_SKIP:
                    fillDot(canvas, cx, cy, radius, Ui.WINE);
                    break;
                case Engine.S_REST:
                    ring(canvas, cx, cy, radius, Ui.REST);
                    break;
                case Engine.S_OPEN:
                    ring(canvas, cx, cy, radius, Ui.LAVENDER);
                    break;
                default: // before the task existed, or still to come
                    fillDot(canvas, cx, cy, radius, Ui.EMPTY);
            }
        }
    }

    private void fillDot(Canvas canvas, float cx, float cy, float r, int color) {
        dot.setStyle(Paint.Style.FILL);
        dot.setColor(color);
        canvas.drawCircle(cx, cy, r, dot);
    }

    private void ring(Canvas canvas, float cx, float cy, float r, int color) {
        dot.setStyle(Paint.Style.STROKE);
        dot.setStrokeWidth(Math.max(1, r * 0.4f));
        dot.setColor(color);
        canvas.drawCircle(cx, cy, r * 0.8f, dot);
    }
}
