package app.budgetcontrol.logic;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import app.budgetcontrol.model.Category;
import app.budgetcontrol.model.Expense;

/** The aggregations behind the Insights screen. Pure Java, like {@link BudgetEngine}. */
public final class Insights {

    private Insights() {}

    public enum TimeBucket {
        MORNING(5 * 60),
        AFTERNOON(11 * 60),
        EVENING(17 * 60),
        NIGHT(22 * 60);

        /** Minute of the day this part of the day starts at. NIGHT wraps past midnight to 5:00. */
        public final int startMinute;

        TimeBucket(int startMinute) {
            this.startMinute = startMinute;
        }

        public static TimeBucket of(int minuteOfDay) {
            if (minuteOfDay >= NIGHT.startMinute || minuteOfDay < MORNING.startMinute) return NIGHT;
            if (minuteOfDay >= EVENING.startMinute) return EVENING;
            if (minuteOfDay >= AFTERNOON.startMinute) return AFTERNOON;
            return MORNING;
        }
    }

    public static final class WeekdayStat {
        public final DayOfWeek day;
        /** Number of tracked days that fell on this weekday. */
        public final int days;
        public final int overDays;
        public final long total;

        WeekdayStat(DayOfWeek day, int days, int overDays, long total) {
            this.day = day;
            this.days = days;
            this.overDays = overDays;
            this.total = total;
        }

        public long average() {
            return days == 0 ? 0 : total / days;
        }
    }

    /** Spending and expense counts for each weekday and part of the day. */
    public static final class TimeGrid {
        /** Indexed [DayOfWeek.ordinal()][TimeBucket.ordinal()]. */
        public final long[][] amount = new long[7][TimeBucket.values().length];
        public final int[][] count = new int[7][TimeBucket.values().length];
        public long total;
        public long max;
        public DayOfWeek peakDay;
        public TimeBucket peakBucket;
    }

    public static long total(List<Expense> expenses) {
        long sum = 0;
        for (Expense e : expenses) sum += e.amount;
        return sum;
    }

    public static long impulseTotal(List<Expense> expenses) {
        long sum = 0;
        for (Expense e : expenses) {
            if (!e.planned) sum += e.amount;
        }
        return sum;
    }

    public static Map<LocalDate, Long> byDay(List<Expense> expenses) {
        Map<LocalDate, Long> out = new LinkedHashMap<>();
        for (Expense e : expenses) out.merge(e.day, e.amount, Long::sum);
        return out;
    }

    /** Largest first; categories with no spending are left out. */
    public static Map<Category, Long> byCategory(List<Expense> expenses, boolean impulseOnly) {
        Map<Category, Long> sums = new HashMap<>();
        for (Expense e : expenses) {
            if (impulseOnly && e.planned) continue;
            sums.merge(e.category, e.amount, Long::sum);
        }
        List<Map.Entry<Category, Long>> entries = new ArrayList<>(sums.entrySet());
        // A loan that has been repaid nets to nothing, and nothing is not a share of spending.
        entries.removeIf(e -> e.getValue() <= 0);
        // Ties go by name so the order does not shuffle between refreshes.
        entries.sort((a, b) -> {
            int byAmount = Long.compare(b.getValue(), a.getValue());
            return byAmount != 0 ? byAmount : a.getKey().name.compareTo(b.getKey().name);
        });
        Map<Category, Long> out = new LinkedHashMap<>();
        for (Map.Entry<Category, Long> e : entries) out.put(e.getKey(), e.getValue());
        return out;
    }

    /** One entry per weekday, starting at {@code firstDay}. */
    public static List<WeekdayStat> weekdayStats(List<BudgetEngine.DayResult> days, DayOfWeek firstDay) {
        int[] count = new int[7];
        int[] over = new int[7];
        long[] total = new long[7];
        for (BudgetEngine.DayResult d : days) {
            int i = d.date.getDayOfWeek().ordinal();
            count[i]++;
            total[i] += d.spent;
            if (!d.underBudget()) over[i]++;
        }
        List<WeekdayStat> out = new ArrayList<>(7);
        for (int n = 0; n < 7; n++) {
            DayOfWeek day = firstDay.plus(n);
            int i = day.ordinal();
            out.add(new WeekdayStat(day, count[i], over[i], total[i]));
        }
        return out;
    }

    public static TimeGrid timeGrid(List<Expense> expenses) {
        TimeGrid g = new TimeGrid();
        for (Expense e : expenses) {
            int d = e.day.getDayOfWeek().ordinal();
            int b = TimeBucket.of(e.minuteOfDay).ordinal();
            g.amount[d][b] += e.amount;
            g.count[d][b]++;
            g.total += e.amount;
        }
        for (DayOfWeek d : DayOfWeek.values()) {
            for (TimeBucket b : TimeBucket.values()) {
                long v = g.amount[d.ordinal()][b.ordinal()];
                if (v > g.max) {
                    g.max = v;
                    g.peakDay = d;
                    g.peakBucket = b;
                }
            }
        }
        return g;
    }

    /** Whole-number percentage, or 0 when there is nothing to divide by. */
    public static int percent(long part, long whole) {
        return whole <= 0 ? 0 : (int) Math.round(part * 100.0 / whole);
    }
}
