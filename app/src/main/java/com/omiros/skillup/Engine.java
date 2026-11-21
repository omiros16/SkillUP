package com.omiros.skillup;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

/**
 * Game rules. Pure Java (no Android) so it can be tested on the JVM.
 * Everything (XP, level, streak, the grid) is recomputed from the marks, so editing yesterday just works.
 */
final class Engine {
    static final char DONE = 'D', BARELY = 'B', SKIP = 'S';

    /** Grid states, one per day from the task's creation to today. */
    static final byte S_REST = 0, S_OPEN = 1, S_SKIP = 2, S_BARELY = 3, S_DONE = 4;

    /** A perfect week is worth this much in every task, whatever its frequency. */
    static final int WEEK_XP = 70;

    /** Schedule from a given day on. Either specific weekdays (days bitmask, Monday = bit 0) or N times a week. */
    static final class Sched {
        final LocalDate from;
        final int days;
        final int times;

        Sched(LocalDate from, int days, int times) {
            this.from = from;
            this.days = times > 0 ? 0 : days;
            this.times = times;
        }

        boolean weekly() {
            return times > 0;
        }

        boolean on(LocalDate d) {
            return (days & (1 << (d.getDayOfWeek().getValue() - 1))) != 0;
        }

        int perWeek() {
            return weekly() ? times : Integer.bitCount(days);
        }

        int doneXp() {
            return Math.round((float) WEEK_XP / perWeek());
        }

        int barelyXp() {
            return Math.round(doneXp() * 0.3f);
        }

        int xp(char mark) {
            return mark == DONE ? doneXp() : mark == BARELY ? barelyXp() : 0;
        }

        boolean sameRule(Sched o) {
            return days == o.days && times == o.times;
        }
    }

    static final class Task {
        long id;
        String name;
        String minimum;
        /** Sorted by {@code from}; the first one's date is the day the task was created. */
        final List<Sched> scheds = new ArrayList<>();
        final TreeMap<LocalDate, Character> marks = new TreeMap<>();

        LocalDate created() {
            return scheds.get(0).from;
        }

        Sched sched(LocalDate d) {
            Sched s = null;
            for (Sched x : scheds) {
                if (x.from.isAfter(d)) break;
                s = x;
            }
            return s;
        }

        Sched current() {
            return scheds.get(scheds.size() - 1);
        }

        /** A schedule change applies from today on; past days keep the rule they had. */
        void setSchedule(LocalDate today, int days, int times) {
            Sched next = new Sched(today, days, times);
            if (current().sameRule(next)) return;
            if (!current().from.isBefore(today)) scheds.remove(scheds.size() - 1);
            if (scheds.isEmpty() || !current().sameRule(next)) scheds.add(next);
        }
    }

    static final class Result {
        int xp, level, into, need;
        int streak, best;
        int done, barely, skips;
        /** Weekly tasks: marks so far this week and how many the week asks for. */
        int weekCount, weekRequired;
        LocalDate start;
        byte[] states;

        byte state(LocalDate d) {
            long i = ChronoUnit.DAYS.between(start, d);
            return i < 0 || i >= states.length ? -1 : states[(int) i];
        }
    }

    private Engine() {
    }

    /** XP needed to go from {@code level} to the next one: 30, 40, 50, ... */
    static int need(int level) {
        return 30 + 10 * (level - 1);
    }

    static Result compute(Task t, LocalDate today) {
        Result r = new Result();
        r.start = t.created();
        int n = (int) Math.max(0, ChronoUnit.DAYS.between(r.start, today) + 1);
        r.states = new byte[n];
        int xp = 0;
        int bucketStart = -1; // first day index of the current run of weekly days in one calendar week

        for (int i = 0; i < n; i++) {
            LocalDate d = r.start.plusDays(i);
            Sched s = t.sched(d);
            Character m = t.marks.get(d);

            if (s.weekly()) {
                if (bucketStart < 0) bucketStart = i;
                r.states[i] = m == null || m == SKIP ? S_REST : m == DONE ? S_DONE : S_BARELY;
                boolean last = i == n - 1; // today: its week is still running
                if (last || d.getDayOfWeek() == DayOfWeek.SUNDAY || t.sched(d.plusDays(1)) != s) {
                    xp = settleWeek(t, r, s, bucketStart, i, !last, xp);
                    bucketStart = -1;
                }
                continue;
            }

            if (m != null && m != SKIP) {
                r.states[i] = m == DONE ? S_DONE : S_BARELY;
                if (s.on(d)) {
                    xp += s.xp(m);
                    if (m == DONE) r.done++; else r.barely++;
                    r.best = Math.max(r.best, ++r.streak);
                } // a mark on a rest day is shown but earns nothing
            } else if (!s.on(d)) {
                r.states[i] = S_REST;
            } else if (m != null || d.isBefore(today)) {
                r.states[i] = S_SKIP;
                xp = Math.max(0, xp - s.doneXp());
                r.skips++;
                r.streak = 0;
            } else {
                r.states[i] = S_OPEN;
            }
        }

        r.xp = xp;
        r.level = 1;
        r.into = xp;
        while (r.into >= need(r.level)) {
            r.into -= need(r.level);
            r.level++;
        }
        r.need = need(r.level);
        return r;
    }

    /**
     * Weekly task, days [from..to] of one calendar week under schedule s. The best marks up to the week's
     * quota earn XP; extras earn nothing. Once the week is over, each missing time costs a skip.
     */
    private static int settleWeek(Task t, Result r, Sched s, int from, int to, boolean over, int xp) {
        LocalDate first = r.start.plusDays(from);
        // A week that starts mid-week (new task, schedule change) asks for a share of the quota.
        int span = over ? to - from + 1 : 8 - first.getDayOfWeek().getValue();
        int required = Math.round(s.times * span / 7f);
        int cap = (int) Math.ceil(s.times * span / 7f); // a partial week can still earn what you do in it

        List<Integer> gains = new ArrayList<>();
        for (int i = from; i <= to; i++) {
            Character m = t.marks.get(r.start.plusDays(i));
            if (m != null && m != SKIP) gains.add(s.xp(m));
        }
        Collections.sort(gains, Collections.reverseOrder());
        int counted = Math.min(cap, gains.size());
        for (int k = 0; k < counted; k++) {
            xp += gains.get(k);
            if (gains.get(k) == s.doneXp()) r.done++; else r.barely++;
            r.best = Math.max(r.best, ++r.streak);
        }

        if (!over) {
            r.weekCount = gains.size();
            r.weekRequired = cap;
            return xp;
        }
        int missing = Math.max(0, required - counted);
        if (missing > 0) {
            xp = Math.max(0, xp - missing * s.doneXp());
            r.skips += missing;
            r.streak = 0;
            // Paint the missing times on the week's last unmarked days.
            for (int i = to; i >= from && missing > 0; i--) {
                if (r.states[i] == S_REST) {
                    r.states[i] = S_SKIP;
                    missing--;
                }
            }
        }
        return xp;
    }

    /** Still to do today: scheduled and unmarked, or a weekly quota that can no longer wait. */
    static boolean due(Task t, Result r, LocalDate today) {
        if (t.created().isAfter(today) || t.marks.containsKey(today)) return false;
        Sched s = t.sched(today);
        if (!s.weekly()) return s.on(today);
        int left = r.weekRequired - r.weekCount;
        int daysLeft = 8 - today.getDayOfWeek().getValue();
        return left > 0 && left >= daysLeft;
    }
}
