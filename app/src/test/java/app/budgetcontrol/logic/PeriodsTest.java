package app.budgetcontrol.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;

import app.budgetcontrol.logic.Periods.Length;
import app.budgetcontrol.logic.Periods.Period;

public class PeriodsTest {
    private static final LocalDate ANCHOR = LocalDate.of(2026, 10, 8);

    @Test
    public void weekStartsOnTheAnchor() {
        Period p = Periods.containing(Length.WEEK, ANCHOR, ANCHOR);
        assertEquals(ANCHOR, p.start);
        assertEquals(LocalDate.of(2026, 10, 14), p.end);
        assertEquals(7, p.days());
    }

    @Test
    public void lastDayOfAWeekIsStillInIt() {
        Period p = Periods.containing(Length.WEEK, ANCHOR, LocalDate.of(2026, 10, 14));
        assertEquals(ANCHOR, p.start);
    }

    @Test
    public void theDayAfterStartsTheNextWeek() {
        Period p = Periods.containing(Length.WEEK, ANCHOR, LocalDate.of(2026, 10, 15));
        assertEquals(LocalDate.of(2026, 10, 15), p.start);
        assertEquals(LocalDate.of(2026, 10, 21), p.end);
    }

    @Test
    public void fortnightCoversFourteenDays() {
        Period p = Periods.containing(Length.TWO_WEEKS, ANCHOR, LocalDate.of(2026, 10, 20));
        assertEquals(ANCHOR, p.start);
        assertEquals(LocalDate.of(2026, 10, 21), p.end);
        assertEquals(14, p.days());
    }

    @Test
    public void daysBeforeTheAnchorFallInEarlierPeriods() {
        Period p = Periods.containing(Length.TWO_WEEKS, ANCHOR, LocalDate.of(2026, 10, 7));
        assertEquals(LocalDate.of(2026, 9, 24), p.start);
        assertEquals(LocalDate.of(2026, 10, 7), p.end);
    }

    @Test
    public void monthIsTheCalendarMonthWhateverTheAnchor() {
        Period p = Periods.containing(Length.MONTH, ANCHOR, LocalDate.of(2027, 2, 14));
        assertEquals(LocalDate.of(2027, 2, 1), p.start);
        assertEquals(LocalDate.of(2027, 2, 28), p.end);
        assertEquals(28, p.days());
    }

    @Test
    public void shiftingMovesByWholePeriods() {
        Period week = Periods.containing(Length.WEEK, ANCHOR, ANCHOR);
        assertEquals(LocalDate.of(2026, 10, 1), Periods.shift(Length.WEEK, ANCHOR, week, -1).start);
        assertEquals(LocalDate.of(2026, 10, 22), Periods.shift(Length.WEEK, ANCHOR, week, 2).start);

        Period october = Periods.containing(Length.MONTH, ANCHOR, ANCHOR);
        Period september = Periods.shift(Length.MONTH, ANCHOR, october, -1);
        assertEquals(LocalDate.of(2026, 9, 1), september.start);
        assertEquals(LocalDate.of(2026, 9, 30), september.end);
    }

    @Test
    public void containsAndDaysLeft() {
        Period p = Periods.containing(Length.WEEK, ANCHOR, ANCHOR);
        assertTrue(p.contains(LocalDate.of(2026, 10, 14)));
        assertFalse(p.contains(LocalDate.of(2026, 10, 15)));
        assertEquals(6, p.daysLeftAfter(ANCHOR));
        assertEquals(0, p.daysLeftAfter(LocalDate.of(2026, 10, 14)));
    }
}
