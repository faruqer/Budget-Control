package app.budgetcontrol.logic;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import app.budgetcontrol.model.RolloverMode;

/**
 * Turns the per-day settings and the per-day spending into the numbers the app shows.
 * Pure Java on purpose: no Android classes, so it runs in plain unit tests.
 */
public final class BudgetEngine {

    private BudgetEngine() {}

    /** The budget and rollover rule that were in force on one day. */
    public static final class DayConfig {
        public final LocalDate date;
        public final long budget;
        public final RolloverMode mode;

        public DayConfig(LocalDate date, long budget, RolloverMode mode) {
            this.date = date;
            this.budget = budget;
            this.mode = mode;
        }
    }

    public static final class DayResult {
        public final LocalDate date;
        public final long budget;
        /** Unspent money rolled in from the day before. */
        public final long carryIn;
        public final long spent;
        public final RolloverMode mode;

        DayResult(LocalDate date, long budget, long carryIn, long spent, RolloverMode mode) {
            this.date = date;
            this.budget = budget;
            this.carryIn = carryIn;
            this.spent = spent;
            this.mode = mode;
        }

        public long available() {
            return budget + carryIn;
        }

        /** Negative when the day is over budget. */
        public long left() {
            return available() - spent;
        }

        public boolean underBudget() {
            return spent <= available();
        }

        private long unspent() {
            return Math.max(0, left());
        }

        /** What this day hands to the next one. Overspending is never carried forward as debt. */
        public long carryOut() {
            return mode == RolloverMode.CARRY ? unspent() : 0;
        }

        /** What this day adds to savings once it is over. */
        public long saved() {
            return mode == RolloverMode.SAVINGS ? unspent() : 0;
        }
    }

    public enum Status { OK, WARNING, OVER }

    public static final class Summary {
        public final DayResult today;
        /** Finished days in a row under budget; 0 as soon as today goes over. */
        public final int streak;
        public final int bestStreak;
        /** Savings from finished days only: today's leftover is not saved until the day ends. */
        public final long totalSaved;
        public final int finishedDays;
        public final int finishedDaysUnderBudget;

        Summary(DayResult today, int streak, int bestStreak, long totalSaved,
                int finishedDays, int finishedDaysUnderBudget) {
            this.today = today;
            this.streak = streak;
            this.bestStreak = bestStreak;
            this.totalSaved = totalSaved;
            this.finishedDays = finishedDays;
            this.finishedDaysUnderBudget = finishedDaysUnderBudget;
        }
    }

    /** @param days one entry per calendar day, oldest first, with no gaps */
    public static List<DayResult> compute(List<DayConfig> days, Map<LocalDate, Long> spentByDay) {
        List<DayResult> out = new ArrayList<>(days.size());
        long carry = 0;
        for (DayConfig d : days) {
            Long spent = spentByDay.get(d.date);
            DayResult r = new DayResult(d.date, d.budget, carry, spent == null ? 0 : spent, d.mode);
            out.add(r);
            carry = r.carryOut();
        }
        return out;
    }

    public static Summary summarize(List<DayResult> results, LocalDate today) {
        DayResult todayResult = null;
        int run = 0;
        int best = 0;
        long saved = 0;
        int finished = 0;
        int finishedUnder = 0;
        for (DayResult r : results) {
            if (r.date.isAfter(today)) break;
            if (r.date.equals(today)) {
                todayResult = r;
                break;
            }
            finished++;
            saved += r.saved();
            if (r.underBudget()) {
                finishedUnder++;
                run++;
                best = Math.max(best, run);
            } else {
                run = 0;
            }
        }
        if (todayResult == null) {
            todayResult = new DayResult(today, 0, 0, 0, RolloverMode.NONE);
        }
        int streak = todayResult.underBudget() ? run : 0;
        return new Summary(todayResult, streak, best, saved, finished, finishedUnder);
    }

    public static Status status(long spent, long available, int warnPercent) {
        if (spent > available) return Status.OVER;
        if (spent > 0 && spent * 100 >= available * warnPercent) return Status.WARNING;
        return Status.OK;
    }
}
