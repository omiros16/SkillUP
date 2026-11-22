package com.omiros.skillup;

import static com.omiros.skillup.Engine.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.json.JSONException;

/** Run by build.sh before packaging: java -ea. Covers Engine (the rules) and Store (the data file and backups). */
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

    static void refused(String json, String what) {
        try {
            Store.parse(json);
        } catch (JSONException expected) {
            return;
        }
        throw new AssertionError(what + " must be refused");
    }

    public static void main(String[] args) throws Exception {
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
        eq(0, due(t, compute(t, d(4)), d(4)) ? 1 : 0, "marked today: not due");

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
        eq(0, r.streak, "a missing time breaks the streak");

        // Still in the week: no penalty yet, due once the remaining days run out.
        t = task(MON, 0, 3);
        r = compute(t, d(4));
        eq(0, r.skips, "running week has no skips");
        eq(1, due(t, r, d(4)) ? 1 : 0, "3 left with 3 days left is due");
        t.marks.put(d(6), DONE);
        eq(0, due(t, compute(t, d(6)), d(6)) ? 1 : 0, "marked today: not due, even with times left");

        // Created on Thursday: the first week asks for round(3 * 4/7) = 2.
        t = task(d(3), 0, 3);
        t.marks.put(d(3), DONE);
        r = compute(t, d(7));
        eq(1, r.skips, "partial week quota");

        // Created on Friday: the week asks for round(3 * 3/7) = 1, so one time is enough and Sunday is not due.
        t = task(d(4), 0, 3);
        t.marks.put(d(4), DONE);
        r = compute(t, d(6));
        eq(1, r.weekRequired, "Friday-created week asks once");
        eq(0, due(t, r, d(6)) ? 1 : 0, "quota met: not due on Sunday");
        eq(0, compute(t, d(7)).skips, "no skip on Monday");
        t = task(d(6), 0, 2);
        eq(0, due(t, compute(t, d(6)), d(6)) ? 1 : 0, "a week that starts on Sunday asks nothing of 2x");

        // A schedule change mid-week keeps what the days before it earned.
        t = task(MON, 0, 3);
        for (int i = 0; i < 3; i++) t.marks.put(d(i), DONE);
        t.setSchedule(d(3), 0, 5);
        r = compute(t, d(3));
        eq(69, r.xp, "Mon-Wed keep their XP after a Thursday change");
        eq(3, r.streak, "and their streak");

        // Weekly buttons show XP only for marks that would count.
        t = task(MON, 0, 3);
        for (int i = 0; i < 3; i++) t.marks.put(d(i), DONE);
        eq(0, earns(t, d(5), DONE, d(6)) ? 1 : 0, "a fourth Done earns nothing");
        eq(1, earns(t, d(1), BARELY, d(6)) ? 1 : 0, "Barely in place of a counted Done still counts");
        eq(DONE, t.marks.get(d(1)), "earns() puts back the mark it replaced");
        for (int i = 0; i < 3; i++) t.marks.put(d(i), BARELY);
        eq(1, earns(t, d(6), DONE, d(6)) ? 1 : 0, "a Done beats a Barely");
        eq(0, earns(t, d(6), BARELY, d(6)) ? 1 : 0, "a fourth Barely earns nothing");
        eq(3, t.marks.size(), "earns() leaves the marks as they were");

        // A schedule change applies from that day on.
        t = task(MON, EVERY_DAY, 0);
        t.setSchedule(d(2), MWF, 0);
        r = compute(t, d(4));
        eq(S_SKIP, r.state(d(1)), "Tuesday was still daily");
        eq(S_REST, r.state(d(3)), "Thursday is rest under the new schedule");
        t.setSchedule(d(2), EVERY_DAY, 0);
        eq(1, t.scheds.size(), "changing back the same day removes the change");

        // The phone's date moved back: a schedule change never lands before the last one.
        t = task(MON, EVERY_DAY, 0);
        t.setSchedule(d(6), MWF, 0);
        t.setSchedule(d(7), 0, 3);
        t.setSchedule(d(6), 0, 4); // the phone reads Sunday again
        for (int i = 1; i < t.scheds.size(); i++) {
            eq(1, t.scheds.get(i).from.isAfter(t.scheds.get(i - 1).from) ? 1 : 0, "schedules stay in date order");
        }
        eq(4, t.current().times, "the latest change wins");

        // Avoid task: past days are clean (+10) unless marked; today stays open until midnight.
        t = task(MON, EVERY_DAY, 0);
        t.avoid = true;
        r = compute(t, d(3));
        eq(30, r.xp, "three clean days");
        eq(2, r.level, "30 xp is Lv2");
        eq(S_DONE, r.state(d(2)), "ended day is clean");
        eq(S_OPEN, r.state(d(3)), "today is open");
        eq(3, r.streak, "clean streak");
        eq(0, due(t, r, d(3)) ? 1 : 0, "avoid tasks are never due");

        // A slip costs 30 at once (floored at zero) and breaks the streak.
        t.marks.put(d(1), SKIP);
        r = compute(t, d(3));
        eq(10, r.xp, "+10, -30 floors to 0, +10");
        eq(S_SKIP, r.state(d(1)), "slip shown");
        eq(1, r.streak, "streak restarts after a slip");
        eq(1, r.best, "best streak");
        eq(2, r.done, "clean days");
        eq(1, r.skips, "slips");
        t.marks.put(d(3), SKIP);
        r = compute(t, d(3));
        eq(0, r.xp, "slip today counts immediately");
        eq(S_SKIP, r.state(d(3)), "today shows the slip");

        // Any other mark on an avoid task is just a clean day.
        t = task(MON, EVERY_DAY, 0);
        t.avoid = true;
        t.marks.put(d(0), DONE);
        eq(20, compute(t, d(2)).xp, "non-slip marks are clean");

        // Store: what toJson writes, parse reads back; a 1.2 backup still loads; far-past dates are refused.
        Task a = task(MON, EVERY_DAY, 0);
        a.id = 1;
        a.name = "Διάβασμα";
        a.minimum = "5 λεπτά";
        a.setSchedule(d(2), 0, 3);
        a.marks.put(d(0), DONE);
        a.marks.put(d(1), BARELY);
        a.marks.put(d(3), SKIP);
        Task b = task(MON, EVERY_DAY, 0);
        b.id = 2;
        b.name = "Όχι shorts";
        b.minimum = "";
        b.avoid = true;
        b.marks.put(d(1), SKIP);
        List<Task> both = Arrays.asList(a, b);
        List<Task> back = Store.parse(Store.toJson(both));
        eq(2, back.size(), "round trip keeps both tasks");
        eq(1, Store.toJson(back).equals(Store.toJson(both)) ? 1 : 0, "round trip changes nothing");
        eq(compute(a, d(6)).xp, compute(back.get(0), d(6)).xp, "same XP after a round trip");
        eq(1, back.get(1).avoid ? 1 : 0, "the avoid flag survives");
        String old = "{\"app\":\"SkillUP\",\"version\":1,\"tasks\":[{\"id\":5,\"name\":\"Gym\",\"min\":\"\","
                + "\"scheds\":[{\"from\":\"2026-11-16\",\"days\":21,\"times\":0}],\"marks\":{\"2026-11-16\":\"D\"}}]}";
        eq(1, Store.parse(old).size(), "a 1.2 backup, without avoid, still loads");
        refused(old.replace("\"from\":\"2026-11-16\"", "\"from\":\"-999999999-01-01\""), "a far-past date");
        refused(old.replace("}],\"marks\"", "},{\"from\":\"2026-11-16\",\"days\":127,\"times\":0}],\"marks\""),
                "two schedules on one date");

        System.out.println("EngineTest: ok");
    }
}
