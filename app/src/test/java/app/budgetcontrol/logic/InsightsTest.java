package app.budgetcontrol.logic;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.budgetcontrol.logic.Insights.TimeBucket;
import app.budgetcontrol.model.Category;
import app.budgetcontrol.model.Expense;
import app.budgetcontrol.model.RolloverMode;

public class InsightsTest {
    private static final Category GROCERIES = category("groceries");
    private static final Category JUNK_FOOD = category("junk_food");
    private static final Category TRANSPORT = category("transport");
    private static final Category MEALS_OUT = category("meals_out");
    private static final Category OTHER = category("other");
    private static final Category FUN = category("fun");

    private static Category category(String key) {
        return new Category(key, key, "sparkles", 0xFF000000, false);
    }

    /** A Monday. */
    private static final LocalDate MON = LocalDate.of(2026, 3, 2);

    private static Expense expense(long amount, Category c, boolean planned, LocalDate day, int hour) {
        return new Expense(0, amount, c, planned, day, hour * 60);
    }

    @Test
    public void timeBucketBoundaries() {
        assertEquals(TimeBucket.NIGHT, TimeBucket.of(0));
        assertEquals(TimeBucket.NIGHT, TimeBucket.of(4 * 60 + 59));
        assertEquals(TimeBucket.MORNING, TimeBucket.of(5 * 60));
        assertEquals(TimeBucket.MORNING, TimeBucket.of(10 * 60 + 59));
        assertEquals(TimeBucket.AFTERNOON, TimeBucket.of(11 * 60));
        assertEquals(TimeBucket.AFTERNOON, TimeBucket.of(16 * 60 + 59));
        assertEquals(TimeBucket.EVENING, TimeBucket.of(17 * 60));
        assertEquals(TimeBucket.EVENING, TimeBucket.of(21 * 60 + 59));
        assertEquals(TimeBucket.NIGHT, TimeBucket.of(22 * 60));
        assertEquals(TimeBucket.NIGHT, TimeBucket.of(23 * 60 + 59));
    }

    @Test
    public void totalsAndImpulseShare() {
        List<Expense> list = Arrays.asList(
                expense(300, GROCERIES, true, MON, 10),
                expense(150, JUNK_FOOD, false, MON, 15),
                expense(50, JUNK_FOOD, false, MON, 21));
        assertEquals(500, Insights.total(list));
        assertEquals(200, Insights.impulseTotal(list));
        assertEquals(40, Insights.percent(200, 500));
    }

    @Test
    public void percentOfNothingIsZero() {
        assertEquals(0, Insights.percent(50, 0));
    }

    @Test
    public void categoriesAreRankedLargestFirst() {
        List<Expense> list = Arrays.asList(
                expense(100, TRANSPORT, true, MON, 9),
                expense(400, MEALS_OUT, false, MON, 13),
                expense(250, GROCERIES, true, MON, 18),
                expense(50, MEALS_OUT, true, MON, 20));
        Map<Category, Long> ranked = Insights.byCategory(list, false);
        assertArrayEquals(new Category[]{MEALS_OUT, GROCERIES, TRANSPORT},
                ranked.keySet().toArray(new Category[0]));
        assertEquals(Long.valueOf(450), ranked.get(MEALS_OUT));
    }

    @Test
    public void impulseOnlyLeavesOutPlannedSpending() {
        List<Expense> list = Arrays.asList(
                expense(400, MEALS_OUT, false, MON, 13),
                expense(50, MEALS_OUT, true, MON, 20),
                expense(250, GROCERIES, true, MON, 18));
        Map<Category, Long> impulse = Insights.byCategory(list, true);
        assertEquals(1, impulse.size());
        assertEquals(Long.valueOf(400), impulse.get(MEALS_OUT));
    }

    @Test
    public void byDaySumsEachDay() {
        List<Expense> list = Arrays.asList(
                expense(100, OTHER, true, MON, 9),
                expense(40, OTHER, true, MON, 12),
                expense(70, OTHER, true, MON.plusDays(2), 9));
        Map<LocalDate, Long> byDay = Insights.byDay(list);
        assertEquals(Long.valueOf(140), byDay.get(MON));
        assertEquals(Long.valueOf(70), byDay.get(MON.plusDays(2)));
        assertNull(byDay.get(MON.plusDays(1)));
    }

    @Test
    public void weekdayStatsAverageOverEveryTrackedDay() {
        // Two full weeks from a Monday; both Saturdays are over budget, one Monday has no spending.
        List<BudgetEngine.DayConfig> days = new ArrayList<>();
        Map<LocalDate, Long> spent = new HashMap<>();
        for (int i = 0; i < 14; i++) {
            days.add(new BudgetEngine.DayConfig(MON.plusDays(i), 500, RolloverMode.NONE));
        }
        spent.put(MON, 200L);
        spent.put(MON.plusDays(5), 900L);
        spent.put(MON.plusDays(12), 700L);
        List<Insights.WeekdayStat> stats =
                Insights.weekdayStats(BudgetEngine.compute(days, spent), DayOfWeek.MONDAY);

        assertEquals(7, stats.size());
        Insights.WeekdayStat monday = stats.get(0);
        assertEquals(DayOfWeek.MONDAY, monday.day);
        assertEquals(2, monday.days);
        // 200 over two Mondays, including the one with nothing spent.
        assertEquals(100, monday.average());
        assertEquals(0, monday.overDays);

        Insights.WeekdayStat saturday = stats.get(5);
        assertEquals(DayOfWeek.SATURDAY, saturday.day);
        assertEquals(800, saturday.average());
        assertEquals(2, saturday.overDays);
    }

    @Test
    public void weekdayStatsStartOnTheGivenDay() {
        List<Insights.WeekdayStat> stats =
                Insights.weekdayStats(Collections.emptyList(), DayOfWeek.SUNDAY);
        assertEquals(DayOfWeek.SUNDAY, stats.get(0).day);
        assertEquals(DayOfWeek.SATURDAY, stats.get(6).day);
        assertEquals(0, stats.get(0).average());
    }

    @Test
    public void timeGridFindsThePeak() {
        LocalDate saturday = MON.plusDays(5);
        List<Expense> list = Arrays.asList(
                expense(100, TRANSPORT, true, MON, 8),
                expense(300, MEALS_OUT, false, saturday, 19),
                expense(250, FUN, false, saturday.plusDays(7), 20),
                expense(120, JUNK_FOOD, false, saturday, 23));
        Insights.TimeGrid g = Insights.timeGrid(list);

        assertEquals(770, g.total);
        assertEquals(550, g.max);
        assertEquals(DayOfWeek.SATURDAY, g.peakDay);
        assertEquals(TimeBucket.EVENING, g.peakBucket);
        assertEquals(2, g.count[DayOfWeek.SATURDAY.ordinal()][TimeBucket.EVENING.ordinal()]);
        assertEquals(120, g.amount[DayOfWeek.SATURDAY.ordinal()][TimeBucket.NIGHT.ordinal()]);
        assertEquals(100, g.amount[DayOfWeek.MONDAY.ordinal()][TimeBucket.MORNING.ordinal()]);
    }

    @Test
    public void emptyTimeGridHasNoPeak() {
        Insights.TimeGrid g = Insights.timeGrid(Collections.emptyList());
        assertEquals(0, g.max);
        assertNull(g.peakDay);
    }
}
