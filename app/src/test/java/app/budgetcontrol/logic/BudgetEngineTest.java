package app.budgetcontrol.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.budgetcontrol.logic.BudgetEngine.DayConfig;
import app.budgetcontrol.logic.BudgetEngine.DayResult;
import app.budgetcontrol.logic.BudgetEngine.Status;
import app.budgetcontrol.logic.BudgetEngine.Summary;
import app.budgetcontrol.model.RolloverMode;

public class BudgetEngineTest {
    private static final LocalDate DAY1 = LocalDate.of(2026, 3, 1);

    /** Consecutive days from DAY1, all with the same budget and rule, spending the given amounts. */
    private static List<DayResult> run(RolloverMode mode, long budget, long... spent) {
        List<DayConfig> days = new ArrayList<>();
        Map<LocalDate, Long> spentByDay = new HashMap<>();
        for (int i = 0; i < spent.length; i++) {
            LocalDate d = DAY1.plusDays(i);
            days.add(new DayConfig(d, budget, mode));
            if (spent[i] > 0) spentByDay.put(d, spent[i]);
        }
        return BudgetEngine.compute(days, spentByDay);
    }

    private static LocalDate day(int n) {
        return DAY1.plusDays(n - 1);
    }

    @Test
    public void carryAddsYesterdaysLeftoverToToday() {
        List<DayResult> r = run(RolloverMode.CARRY, 500, 300, 0);
        assertEquals(0, r.get(0).carryIn);
        assertEquals(200, r.get(0).left());
        assertEquals(200, r.get(1).carryIn);
        assertEquals(700, r.get(1).available());
    }

    @Test
    public void carryKeepsRollingUnspentMoneyForward() {
        List<DayResult> r = run(RolloverMode.CARRY, 500, 300, 100, 0);
        // Day 2 has 500 + 200 and spends 100, so 600 moves on.
        assertEquals(600, r.get(2).carryIn);
        assertEquals(1100, r.get(2).available());
    }

    @Test
    public void overspendingIsNotCarriedForwardAsDebt() {
        List<DayResult> r = run(RolloverMode.CARRY, 500, 800, 0);
        assertEquals(-300, r.get(0).left());
        assertFalse(r.get(0).underBudget());
        assertEquals(0, r.get(1).carryIn);
        assertEquals(500, r.get(1).available());
    }

    @Test
    public void carriedMoneyCountsTowardsStayingUnderBudget() {
        List<DayResult> r = run(RolloverMode.CARRY, 500, 300, 650);
        // 650 is over the base budget but inside the 700 available that day.
        assertTrue(r.get(1).underBudget());
        assertEquals(50, r.get(1).left());
    }

    @Test
    public void savingsModeBanksLeftoversInsteadOfCarryingThem() {
        List<DayResult> r = run(RolloverMode.SAVINGS, 500, 300, 450, 100);
        assertEquals(0, r.get(1).carryIn);
        assertEquals(0, r.get(2).carryIn);
        assertEquals(200, r.get(0).saved());
        assertEquals(50, r.get(1).saved());
    }

    @Test
    public void savingsCountFinishedDaysOnly() {
        List<DayResult> r = run(RolloverMode.SAVINGS, 500, 300, 450, 100);
        Summary s = BudgetEngine.summarize(r, day(3));
        // Today still has 400 unspent, but the day is not over yet.
        assertEquals(250, s.totalSaved);
        assertEquals(400, s.today.left());
    }

    @Test
    public void overspendingSavesNothing() {
        List<DayResult> r = run(RolloverMode.SAVINGS, 500, 900, 0);
        assertEquals(0, r.get(0).saved());
        assertEquals(0, BudgetEngine.summarize(r, day(2)).totalSaved);
    }

    @Test
    public void noRolloverLeavesEveryDayAtItsBudget() {
        List<DayResult> r = run(RolloverMode.NONE, 500, 100, 100);
        assertEquals(500, r.get(1).available());
        assertEquals(0, r.get(0).saved());
        assertEquals(0, BudgetEngine.summarize(r, day(2)).totalSaved);
    }

    @Test
    public void eachDayUsesTheRuleItHadAtTheTime() {
        List<DayConfig> days = new ArrayList<>();
        days.add(new DayConfig(day(1), 500, RolloverMode.CARRY));
        days.add(new DayConfig(day(2), 800, RolloverMode.SAVINGS));
        days.add(new DayConfig(day(3), 800, RolloverMode.SAVINGS));
        Map<LocalDate, Long> spent = new HashMap<>();
        spent.put(day(1), 400L);
        List<DayResult> r = BudgetEngine.compute(days, spent);

        assertEquals(100, r.get(1).carryIn);
        assertEquals(900, r.get(1).available());
        // Day 2 saves its whole 900 rather than passing it on.
        assertEquals(900, r.get(1).saved());
        assertEquals(0, r.get(2).carryIn);
    }

    @Test
    public void streakCountsFinishedDaysUnderBudget() {
        List<DayResult> r = run(RolloverMode.NONE, 500, 100, 200, 300, 50);
        Summary s = BudgetEngine.summarize(r, day(4));
        assertEquals(3, s.streak);
        assertEquals(3, s.bestStreak);
        assertEquals(3, s.finishedDays);
        assertEquals(3, s.finishedDaysUnderBudget);
    }

    @Test
    public void anOverBudgetDayResetsTheStreak() {
        List<DayResult> r = run(RolloverMode.NONE, 500, 100, 100, 900, 100, 0);
        Summary s = BudgetEngine.summarize(r, day(5));
        assertEquals(1, s.streak);
        assertEquals(2, s.bestStreak);
        assertEquals(3, s.finishedDaysUnderBudget);
    }

    @Test
    public void goingOverTodayBreaksTheStreakImmediately() {
        List<DayResult> r = run(RolloverMode.NONE, 500, 100, 100, 600);
        Summary s = BudgetEngine.summarize(r, day(3));
        assertEquals(0, s.streak);
        assertEquals(2, s.bestStreak);
    }

    @Test
    public void spendingExactlyTheBudgetStillCountsAsUnder() {
        List<DayResult> r = run(RolloverMode.NONE, 500, 500, 0);
        assertTrue(r.get(0).underBudget());
        assertEquals(1, BudgetEngine.summarize(r, day(2)).streak);
    }

    @Test
    public void firstDayHasNoStreakYet() {
        Summary s = BudgetEngine.summarize(run(RolloverMode.CARRY, 500, 100), day(1));
        assertEquals(0, s.streak);
        assertEquals(0, s.finishedDays);
        assertEquals(400, s.today.left());
    }

    @Test
    public void daysAfterTodayAreIgnored() {
        List<DayResult> r = run(RolloverMode.NONE, 500, 100, 100, 900);
        Summary s = BudgetEngine.summarize(r, day(2));
        assertEquals(1, s.streak);
        assertEquals(day(2), s.today.date);
    }

    @Test
    public void statusFollowsTheWarningThreshold() {
        assertEquals(Status.OK, BudgetEngine.status(0, 1000, 80));
        assertEquals(Status.OK, BudgetEngine.status(799, 1000, 80));
        assertEquals(Status.WARNING, BudgetEngine.status(800, 1000, 80));
        assertEquals(Status.WARNING, BudgetEngine.status(1000, 1000, 80));
        assertEquals(Status.OVER, BudgetEngine.status(1001, 1000, 80));
        assertEquals(Status.WARNING, BudgetEngine.status(600, 1000, 60));
    }

    @Test
    public void statusWithNothingAvailable() {
        assertEquals(Status.OK, BudgetEngine.status(0, 0, 80));
        assertEquals(Status.OVER, BudgetEngine.status(1, 0, 80));
    }
}
