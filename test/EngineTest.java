package com.omiros.skillup;

import static com.omiros.skillup.Engine.*;

import java.time.DayOfWeek;
import java.time.LocalDate;

/** Run by build.sh before packaging: java -ea. */
public class EngineTest {
    static final LocalDate MON = LocalDate.of(2026, 11, 16);
    static final int EVERY_DAY = 0b1111111, MWF = 0b0010101;

    static Task task(LocalDate created, int days, int times) {
        Task t = new Task();
        t.name = "t";
        t.scheds.add(new Sched(created, days, times));
        return t;
    }

    static LocalDate d(int offset) {
        return MON.plusDays(offset);
    }

    static void eq(long expected, long actual, String what) {
        if (expected != actual) throw new AssertionError(what + ": expected " + expected + ", got " + actual);
    }

    public static void main(String[] args) {
        eq(DayOfWeek.MONDAY.getValue(), MON.getDayOfWeek().getValue(), "MON is a Monday");

        // Daily: done, done, barely, (missed), today open.
        Task t = task(MON, EVERY_DAY, 0);
        t.marks.put(d(0), DONE);
        t.marks.put(d(1), DONE);
        t.marks.put(d(2), BARELY);
        Result r = compute(t, d(4));
        eq(13, r.xp, "daily xp");
        eq(S_SKIP, r.state(d(3)), "missed day is skipped");
        eq(S_OPEN, r.state(d(4)), "today is open");
        eq(0, r.streak, "streak broken by skip");
        eq(3, r.best, "best streak");
        eq(1, due(t, r, d(4)) ? 1 : 0, "today is due");

        // Explicit skip today counts right away.
        t.marks.put(d(4), SKIP);
        eq(3, compute(t, d(4)).xp, "explicit skip today");

        // XP never goes below zero.
        t = task(MON, EVERY_DAY, 0);
        t.marks.put(d(3), DONE);
        eq(10, compute(t, d(3)).xp, "floor at zero before gains");

        // Levels: 30 to Lv2, then 40 more to Lv3.
        t = task(MON, EVERY_DAY, 0);
        for (int i = 0; i < 7; i++) t.marks.put(d(i), DONE);
        r = compute(t, d(6));
        eq(3, r.level, "70 xp is Lv3");
        eq(0, r.into, "70 xp into Lv3");
        t.marks.remove(d(6));
        r = compute(t, d(6));
        eq(2, r.level, "60 xp is Lv2");
        eq(30, r.into, "60 xp into Lv2");
        eq(40, r.need, "Lv2 needs 40");

        // Mon/Wed/Fri: 23 / 7 / -23, rest days earn nothing.
        t = task(MON, MWF, 0);
        eq(23, t.current().doneXp(), "MWF done xp");
        eq(7, t.current().barelyXp(), "MWF barely xp");
        t.marks.put(d(0), DONE);
        t.marks.put(d(1), DONE); // Tuesday is a rest day
        r = compute(t, d(3));
        eq(0, r.xp, "MWF: +23, rest +0, skip -23");
        eq(S_DONE, r.state(d(1)), "rest-day mark is shown");
        eq(S_SKIP, r.state(d(2)), "Wednesday skipped");
        eq(S_REST, r.state(d(3)), "Thursday rest");

        // Weekly 3x: the best three marks count, the fourth earns nothing.
        t = task(MON, 0, 3);
        t.marks.put(d(0), DONE);
        t.marks.put(d(2), BARELY);
        t.marks.put(d(4), DONE);
        t.marks.put(d(5), DONE);
        r = compute(t, d(7));
        eq(69, r.xp, "weekly best three");
        eq(0, r.skips, "weekly quota met");
        eq(0, r.weekCount, "new week count");
        eq(3, r.weekRequired, "new week quota");
        eq(0, due(t, r, d(7)) ? 1 : 0, "Monday not due yet");

        // Weekly 3x, only once: two skips at the end of the week, painted on the last free days.
        t = task(MON, 0, 3);
        t.marks.put(d(0), DONE);
        r = compute(t, d(8));
        eq(0, r.xp, "weekly deficit floors at zero");
        eq(2, r.skips, "two missing times");
        eq(S_SKIP, r.state(d(6)), "Sunday painted as skip");
        eq(S_SKIP, r.state(d(5)), "Saturday painted as skip");
        eq(S_REST, r.state(d(4)), "Friday left as rest");

        // Still in the week: no penalty yet, due once the remaining days run out.
        t = task(MON, 0, 3);
        r = compute(t, d(4));
        eq(0, r.skips, "running week has no skips");
        eq(1, due(t, r, d(4)) ? 1 : 0, "3 left with 3 days left is due");

        // Created on Thursday: the first week asks for round(3 * 4/7) = 2.
        t = task(d(3), 0, 3);
        t.marks.put(d(3), DONE);
        r = compute(t, d(7));
        eq(1, r.skips, "partial week quota");

        // A schedule change applies from that day on.
        t = task(MON, EVERY_DAY, 0);
        t.setSchedule(d(2), MWF, 0);
        r = compute(t, d(4));
        eq(S_SKIP, r.state(d(1)), "Tuesday was still daily");
        eq(S_REST, r.state(d(3)), "Thursday is rest under the new schedule");
        t.setSchedule(d(2), EVERY_DAY, 0);
        eq(1, t.scheds.size(), "changing back the same day removes the change");

        System.out.println("EngineTest: ok");
    }
}
