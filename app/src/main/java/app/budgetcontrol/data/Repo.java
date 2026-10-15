package app.budgetcontrol.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import app.budgetcontrol.logic.BudgetEngine;
import app.budgetcontrol.logic.Periods;
import app.budgetcontrol.model.Category;
import app.budgetcontrol.model.Expense;
import app.budgetcontrol.model.Loan;
import app.budgetcontrol.model.RolloverMode;

/**
 * All reads and writes go through here. Queries run on the calling thread:
 * the data is one person's expenses, so they finish well inside a frame.
 */
public final class Repo {

    /** An amount and category the user logs often, offered as a one-tap repeat. */
    public static final class Combo {
        public final Category category;
        public final long amount;
        public final boolean planned;

        Combo(Category category, long amount, boolean planned) {
            this.category = category;
            this.amount = amount;
            this.planned = planned;
        }
    }

    private static final String EXPENSE_COLUMNS = "id, amount, category, planned, day, minute, bulk, loan_id, note";

    private final Db db;
    private final Prefs prefs;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    /** Every category by key, loaded once and dropped whenever one changes. */
    @Nullable private Map<String, Category> categoryCache;

    public Repo(Context context) {
        db = new Db(context);
        prefs = new Prefs(context);
    }

    public Prefs prefs() {
        return prefs;
    }

    /** Listeners run on the thread that made the change, right after it is saved. */
    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private void notifyChanged() {
        for (Runnable r : listeners) r.run();
    }

    // ---- days ----

    /** Adds a row for every day up to today that does not have one, using the current settings. */
    public void ensureDays(LocalDate today) {
        SQLiteDatabase w = db.getWritableDatabase();
        LocalDate last = null;
        try (Cursor c = w.rawQuery("SELECT MAX(day) FROM days", null)) {
            if (c.moveToFirst() && !c.isNull(0)) last = LocalDate.parse(c.getString(0));
        }
        LocalDate next = last == null ? today : last.plusDays(1);
        if (next.isAfter(today)) return;
        w.beginTransaction();
        try {
            for (LocalDate d = next; !d.isAfter(today); d = d.plusDays(1)) {
                ContentValues v = new ContentValues();
                v.put("day", d.toString());
                v.put("budget", prefs.dailyBudget());
                v.put("rollover", prefs.rollover().key);
                w.insertWithOnConflict("days", null, v, SQLiteDatabase.CONFLICT_IGNORE);
            }
            w.setTransactionSuccessful();
        } finally {
            w.endTransaction();
        }
    }

    /** Every tracked day up to today with its budget, carry-over and spending worked out. */
    public List<BudgetEngine.DayResult> ledger(LocalDate today) {
        ensureDays(today);
        List<BudgetEngine.DayConfig> days = new ArrayList<>();
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT day, budget, rollover FROM days WHERE day <= ? ORDER BY day",
                new String[]{today.toString()})) {
            while (c.moveToNext()) {
                days.add(new BudgetEngine.DayConfig(
                        LocalDate.parse(c.getString(0)), c.getLong(1), RolloverMode.fromKey(c.getString(2))));
            }
        }
        Map<LocalDate, Long> spent = new HashMap<>();
        try (Cursor c = db.getReadableDatabase().rawQuery(
                // Big shops belong to the period, so they stay out of the daily numbers.
                "SELECT day, SUM(amount) FROM expenses WHERE bulk = 0 GROUP BY day", null)) {
            while (c.moveToNext()) spent.put(LocalDate.parse(c.getString(0)), c.getLong(1));
        }
        return BudgetEngine.compute(days, spent);
    }

    /** The first tracked day, or today when nothing has been tracked yet. */
    public LocalDate firstDay(LocalDate today) {
        try (Cursor c = db.getReadableDatabase().rawQuery("SELECT MIN(day) FROM days", null)) {
            if (c.moveToFirst() && !c.isNull(0)) return LocalDate.parse(c.getString(0));
        }
        return today;
    }

    // ---- settings that are frozen into the day rows ----

    public void setDailyBudget(long amount, LocalDate today) {
        prefs.setDailyBudget(amount);
        updateToday("budget", amount, today);
    }

    public void setRollover(RolloverMode mode, LocalDate today) {
        prefs.setRollover(mode);
        updateToday("rollover", mode.key, today);
    }

    private void updateToday(String column, Object value, LocalDate today) {
        ensureDays(today);
        ContentValues v = new ContentValues();
        if (value instanceof Long) {
            v.put(column, (Long) value);
        } else {
            v.put(column, String.valueOf(value));
        }
        db.getWritableDatabase().update("days", v, "day = ?", new String[]{today.toString()});
        notifyChanged();
    }

    public void setCurrency(String symbol) {
        prefs.setCurrency(symbol);
        notifyChanged();
    }

    public void setWarnPercent(int percent) {
        prefs.setWarnPercent(percent);
        notifyChanged();
    }

    // ---- categories ----

    private Map<String, Category> loadCategories() {
        if (categoryCache == null) {
            Map<String, Category> all = new LinkedHashMap<>();
            try (Cursor c = db.getReadableDatabase().rawQuery(
                    "SELECT key, name, icon, color, archived FROM categories ORDER BY position, name", null)) {
                while (c.moveToNext()) {
                    all.put(c.getString(0), new Category(c.getString(0), c.getString(1), c.getString(2),
                            c.getInt(3), c.getInt(4) != 0));
                }
            }
            categoryCache = all;
        }
        return categoryCache;
    }

    /** The categories on offer, in the order the user sees them. */
    public List<Category> categories() {
        List<Category> out = new ArrayList<>();
        for (Category c : loadCategories().values()) {
            if (!c.archived && !Category.LOANS_KEY.equals(c.key)) out.add(c);
        }
        return out;
    }

    /** Any category ever used, including removed ones. Unknown keys come back as "Other". */
    public Category category(String key) {
        Map<String, Category> all = loadCategories();
        Category found = all.get(key);
        if (found != null) return found;
        Category other = all.get(Category.OTHER_KEY);
        return other != null ? other : new Category(Category.OTHER_KEY, "Other", "sparkles", 0xFF7D7B75, false);
    }

    /** Adds a category at the end of the list and returns it. */
    public Category addCategory(String name, String icon, int color) {
        SQLiteDatabase w = db.getWritableDatabase();
        int position = 0;
        try (Cursor c = w.rawQuery("SELECT COALESCE(MAX(position), -1) + 1 FROM categories", null)) {
            if (c.moveToFirst()) position = c.getInt(0);
        }
        String key = "c_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        ContentValues v = new ContentValues();
        v.put("key", key);
        v.put("name", name);
        v.put("icon", icon);
        v.put("color", color);
        v.put("position", position);
        w.insertOrThrow("categories", null, v);
        categoryCache = null;
        notifyChanged();
        return category(key);
    }

    public void updateCategory(String key, String name, String icon, int color) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("icon", icon);
        v.put("color", color);
        db.getWritableDatabase().update("categories", v, "key = ?", new String[]{key});
        categoryCache = null;
        notifyChanged();
    }

    /**
     * Takes a category out of the picker. Its past expenses keep it, so history and totals
     * do not change. "Other" stays, because it is where unknown categories land.
     */
    public void removeCategory(String key) {
        if (Category.OTHER_KEY.equals(key)) return;
        ContentValues v = new ContentValues();
        v.put("archived", 1);
        db.getWritableDatabase().update("categories", v, "key = ?", new String[]{key});
        categoryCache = null;
        notifyChanged();
    }

    // ---- budget period ----

    /** The user's budget period that holds the given day. */
    public Periods.Period period(LocalDate day) {
        LocalDate anchor = prefs.periodAnchor();
        return Periods.containing(prefs.periodLength(), anchor != null ? anchor : firstDay(day), day);
    }

    /** What there is to spend in a period: the amount the user set, or the daily budget for each day. */
    public long periodBudget(Periods.Period period) {
        long set = prefs.periodBudget();
        return set > 0 ? set : prefs.dailyBudget() * period.days();
    }

    /** Everything spent in a period, big shops and loans included. */
    public long spentIn(Periods.Period period) {
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE day >= ? AND day <= ?",
                new String[]{period.start.toString(), period.end.toString()})) {
            return c.moveToFirst() ? c.getLong(0) : 0;
        }
    }

    /** Changes how long a period is. Weekly and two-weekly periods are counted from the given day. */
    public void setPeriod(Periods.Length length, LocalDate startingOn) {
        prefs.setPeriodLength(length);
        prefs.setPeriodAnchor(startingOn);
        // The change itself is not a period ending, so there is no report to announce.
        prefs.setAnnouncedPeriod(period(startingOn).start);
        notifyChanged();
    }

    public void setPeriodBudget(long amount) {
        prefs.setPeriodBudget(amount);
        notifyChanged();
    }

    // ---- loans ----

    private static final String LOAN_QUERY =
            "SELECT l.id, l.person, l.amount, l.day, l.note, "
                    + "COALESCE((SELECT -SUM(e.amount) FROM expenses e WHERE e.loan_id = l.id AND e.amount < 0), 0) "
                    + "FROM loans l ";

    private static Loan readLoan(Cursor c) {
        return new Loan(c.getLong(0), c.getString(1), c.getLong(2), LocalDate.parse(c.getString(3)),
                c.getString(4), c.getLong(5));
    }

    /** Every loan, newest first. */
    public List<Loan> loans() {
        List<Loan> out = new ArrayList<>();
        try (Cursor c = db.getReadableDatabase().rawQuery(LOAN_QUERY + "ORDER BY l.day DESC, l.id DESC", null)) {
            while (c.moveToNext()) out.add(readLoan(c));
        }
        return out;
    }

    @Nullable
    public Loan loan(long id) {
        try (Cursor c = db.getReadableDatabase().rawQuery(LOAN_QUERY + "WHERE l.id = ?",
                new String[]{String.valueOf(id)})) {
            return c.moveToFirst() ? readLoan(c) : null;
        }
    }

    /** The repayments received for a loan, newest first. Their amounts are negative. */
    public List<Expense> repayments(long loanId) {
        List<Expense> out = new ArrayList<>();
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT " + EXPENSE_COLUMNS + " FROM expenses WHERE loan_id = ? AND amount < 0 "
                        + "ORDER BY day DESC, id DESC", new String[]{String.valueOf(loanId)})) {
            while (c.moveToNext()) out.add(read(c));
        }
        return out;
    }

    /** What is still owed across all loans. */
    public long owedTotal() {
        long total = 0;
        for (Loan l : loans()) total += l.outstanding();
        return total;
    }

    /** Records a loan, and the money leaving on that day. */
    public long addLoan(String person, long amount, LocalDate day, @Nullable String note, int minuteOfDay) {
        SQLiteDatabase w = db.getWritableDatabase();
        long id;
        w.beginTransaction();
        try {
            ContentValues loan = new ContentValues();
            loan.put("person", person);
            loan.put("amount", amount);
            loan.put("day", day.toString());
            loan.put("note", note);
            loan.put("created_at", System.currentTimeMillis());
            id = w.insertOrThrow("loans", null, loan);
            insertLoanRow(w, id, amount, person, day, minuteOfDay);
            w.setTransactionSuccessful();
        } finally {
            w.endTransaction();
        }
        notifyChanged();
        return id;
    }

    /** Records money coming back. It is given back to the day it arrives on. */
    public void addRepayment(Loan loan, long amount, LocalDate day, int minuteOfDay) {
        SQLiteDatabase w = db.getWritableDatabase();
        insertLoanRow(w, loan.id, -amount, loan.person, day, minuteOfDay);
        notifyChanged();
    }

    /** Removes one repayment, e.g. one entered by mistake. */
    public void deleteRepayment(long expenseId) {
        delete(expenseId);
    }

    /** Removes a loan together with its lending and repayments, as if it never happened. */
    public void deleteLoan(long id) {
        SQLiteDatabase w = db.getWritableDatabase();
        w.beginTransaction();
        try {
            w.delete("expenses", "loan_id = ?", new String[]{String.valueOf(id)});
            w.delete("loans", "id = ?", new String[]{String.valueOf(id)});
            w.setTransactionSuccessful();
        } finally {
            w.endTransaction();
        }
        notifyChanged();
    }

    private void insertLoanRow(SQLiteDatabase w, long loanId, long amount, String person,
                               LocalDate day, int minuteOfDay) {
        ContentValues v = values(amount, category(Category.LOANS_KEY), true, day, minuteOfDay);
        v.put("loan_id", loanId);
        v.put("note", person);
        v.put("created_at", System.currentTimeMillis());
        w.insertOrThrow("expenses", null, v);
    }

    // ---- expenses ----

    public long insert(long amount, Category category, boolean planned, LocalDate day, int minuteOfDay) {
        return insert(amount, category, planned, day, minuteOfDay, false);
    }

    /** @param bulk a big shop for the period, kept out of the day's limit */
    public long insert(long amount, Category category, boolean planned, LocalDate day, int minuteOfDay,
                       boolean bulk) {
        ContentValues v = values(amount, category, planned, day, minuteOfDay);
        v.put("bulk", bulk ? 1 : 0);
        v.put("created_at", System.currentTimeMillis());
        long id = db.getWritableDatabase().insertOrThrow("expenses", null, v);
        notifyChanged();
        return id;
    }

    public void update(Expense e) {
        ContentValues v = values(e.amount, e.category, e.planned, e.day, e.minuteOfDay);
        v.put("bulk", e.bulk ? 1 : 0);
        db.getWritableDatabase().update("expenses", v, "id = ?", new String[]{String.valueOf(e.id)});
        notifyChanged();
    }

    public void delete(long id) {
        db.getWritableDatabase().delete("expenses", "id = ?", new String[]{String.valueOf(id)});
        notifyChanged();
    }

    private static ContentValues values(long amount, Category category, boolean planned,
                                        LocalDate day, int minuteOfDay) {
        ContentValues v = new ContentValues();
        v.put("amount", amount);
        v.put("category", category.key);
        v.put("planned", planned ? 1 : 0);
        v.put("day", day.toString());
        v.put("minute", minuteOfDay);
        return v;
    }

    @Nullable
    public Expense find(long id) {
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT " + EXPENSE_COLUMNS + " FROM expenses WHERE id = ?",
                new String[]{String.valueOf(id)})) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    /** Both ends included, newest first. */
    public List<Expense> expenses(LocalDate from, LocalDate to) {
        List<Expense> out = new ArrayList<>();
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT " + EXPENSE_COLUMNS + " FROM expenses WHERE day >= ? AND day <= ? "
                        + "ORDER BY day DESC, minute DESC, id DESC",
                new String[]{from.toString(), to.toString()})) {
            while (c.moveToNext()) out.add(read(c));
        }
        return out;
    }

    private Expense read(Cursor c) {
        return new Expense(c.getLong(0), c.getLong(1), category(c.getString(2)),
                c.getInt(3) != 0, LocalDate.parse(c.getString(4)), c.getInt(5),
                c.getInt(6) != 0, c.isNull(7) ? 0 : c.getLong(7), c.getString(8));
    }

    /** The amount, category and tag combinations logged most often since the given day. */
    public List<Combo> topCombos(LocalDate since, int limit) {
        List<Combo> out = new ArrayList<>();
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT category, amount, planned, COUNT(*) AS n, MAX(created_at) AS latest "
                        + "FROM expenses WHERE day >= ? AND bulk = 0 AND loan_id IS NULL "
                        + "GROUP BY category, amount, planned "
                        + "ORDER BY n DESC, latest DESC LIMIT " + limit,
                new String[]{since.toString()})) {
            while (c.moveToNext()) {
                Category category = category(c.getString(0));
                // A removed category should not be offered for one-tap logging.
                if (category.archived) continue;
                out.add(new Combo(category, c.getLong(1), c.getInt(2) != 0));
            }
        }
        return out;
    }

    /** The amounts logged most often since the given day. */
    public List<Long> topAmounts(LocalDate since, int limit) {
        List<Long> out = new ArrayList<>();
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT amount, COUNT(*) AS n FROM expenses WHERE day >= ? AND loan_id IS NULL "
                        + "GROUP BY amount HAVING n >= 2 ORDER BY n DESC, amount LIMIT " + limit,
                new String[]{since.toString()})) {
            while (c.moveToNext()) out.add(c.getLong(0));
        }
        return out;
    }

    /** Deletes every expense and day, and all settings. */
    public void eraseAll() {
        SQLiteDatabase w = db.getWritableDatabase();
        w.delete("expenses", null, null);
        w.delete("days", null, null);
        w.delete("categories", null, null);
        w.delete("loans", null, null);
        db.seedCategories(w);
        categoryCache = null;
        prefs.clear();
        notifyChanged();
    }
}
