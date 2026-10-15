package app.budgetcontrol.logic;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/**
 * The budget period: the stretch of days a big shop is meant to last and a report covers.
 * Pure Java, like {@link BudgetEngine}.
 */
public final class Periods {

    private Periods() {}

    public enum Length {
        WEEK("week", 7),
        TWO_WEEKS("two_weeks", 14),
        MONTH("month", 0);

        public final String key;
        /** Days in the period, or 0 for a calendar month. */
        public final int days;

        Length(String key, int days) {
            this.key = key;
            this.days = days;
        }

        public static Length fromKey(String key) {
            for (Length l : values()) {
                if (l.key.equals(key)) return l;
            }
            return MONTH;
        }
    }

    public static final class Period {
        public final LocalDate start;
        /** The last day, included. */
        public final LocalDate end;

        public Period(LocalDate start, LocalDate end) {
            this.start = start;
            this.end = end;
        }

        public int days() {
            return (int) ChronoUnit.DAYS.between(start, end) + 1;
        }

        public boolean contains(LocalDate day) {
            return !day.isBefore(start) && !day.isAfter(end);
        }

        /** Days left after the given day, 0 on the last day. */
        public int daysLeftAfter(LocalDate day) {
            return (int) Math.max(0, ChronoUnit.DAYS.between(day, end));
        }
    }

    /**
     * The period holding {@code day}. Weeks and fortnights are counted from {@code anchor}, in both
     * directions; months are calendar months and ignore it.
     */
    public static Period containing(Length length, LocalDate anchor, LocalDate day) {
        if (length == Length.MONTH) {
            return new Period(day.withDayOfMonth(1), day.with(TemporalAdjusters.lastDayOfMonth()));
        }
        long since = ChronoUnit.DAYS.between(anchor, day);
        long index = Math.floorDiv(since, (long) length.days);
        LocalDate start = anchor.plusDays(index * length.days);
        return new Period(start, start.plusDays(length.days - 1));
    }

    /** The period {@code steps} after this one, or before it when negative. */
    public static Period shift(Length length, LocalDate anchor, Period period, int steps) {
        LocalDate inside = length == Length.MONTH
                ? period.start.plusMonths(steps)
                : period.start.plusDays((long) steps * length.days);
        return containing(length, anchor, inside);
    }
}
