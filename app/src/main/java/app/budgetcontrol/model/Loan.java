package app.budgetcontrol.model;

import androidx.annotation.Nullable;

import java.time.LocalDate;

/** Money lent to someone, and how much of it has come back so far. */
public final class Loan {
    public final long id;
    public final String person;
    public final long amount;
    public final LocalDate day;
    @Nullable public final String note;
    public final long repaid;

    public Loan(long id, String person, long amount, LocalDate day, @Nullable String note, long repaid) {
        this.id = id;
        this.person = person;
        this.amount = amount;
        this.day = day;
        this.note = note;
        this.repaid = repaid;
    }

    public long outstanding() {
        return Math.max(0, amount - repaid);
    }

    public boolean isPaid() {
        return repaid >= amount;
    }
}
