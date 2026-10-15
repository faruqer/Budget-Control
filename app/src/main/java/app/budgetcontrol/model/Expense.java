package app.budgetcontrol.model;

import androidx.annotation.Nullable;

import java.time.LocalDate;

public final class Expense {
    public final long id;
    /**
     * In hundredths of the currency unit. Negative for a loan repayment, which gives money back
     * to the day it arrives on.
     */
    public final long amount;
    public final Category category;
    /** false means an impulse buy. */
    public final boolean planned;
    /** Local calendar day the money was spent, kept apart from the clock time so a timezone change cannot move it. */
    public final LocalDate day;
    public final int minuteOfDay;
    /** A big shop for the whole period: counted in the period, not against this day's limit. */
    public final boolean bulk;
    /** Set when this row is the lending or a repayment of a loan; such rows are edited through the loan. */
    public final long loanId;
    @Nullable public final String note;

    public Expense(long id, long amount, Category category, boolean planned, LocalDate day, int minuteOfDay,
                   boolean bulk, long loanId, @Nullable String note) {
        this.id = id;
        this.amount = amount;
        this.category = category;
        this.planned = planned;
        this.day = day;
        this.minuteOfDay = minuteOfDay;
        this.bulk = bulk;
        this.loanId = loanId;
        this.note = note;
    }

    /** An ordinary expense: not a big shop, not part of a loan. */
    public Expense(long id, long amount, Category category, boolean planned, LocalDate day, int minuteOfDay) {
        this(id, amount, category, planned, day, minuteOfDay, false, 0, null);
    }

    public boolean isLoan() {
        return loanId != 0;
    }
}
