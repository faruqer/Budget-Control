package app.budgetcontrol.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import app.budgetcontrol.R;

final class Db extends SQLiteOpenHelper {
    private final Context context;

    Db(Context context) {
        super(context.getApplicationContext(), "budget.db", null, 3);
        this.context = context.getApplicationContext();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE expenses ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "amount INTEGER NOT NULL, "
                + "category TEXT NOT NULL, "
                + "planned INTEGER NOT NULL, "
                + "day TEXT NOT NULL, "
                + "minute INTEGER NOT NULL, "
                + "created_at INTEGER NOT NULL, "
                + "bulk INTEGER NOT NULL DEFAULT 0, "
                + "loan_id INTEGER, "
                + "note TEXT)");
        db.execSQL("CREATE INDEX idx_expenses_day ON expenses(day)");
        // One row per calendar day, freezing the budget and rollover rule that applied,
        // so changing a setting later never rewrites history.
        db.execSQL("CREATE TABLE days ("
                + "day TEXT PRIMARY KEY, "
                + "budget INTEGER NOT NULL, "
                + "rollover TEXT NOT NULL)");
        createCategories(db);
        createLoans(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version 2 added editable categories. Existing expenses already carry these keys.
        if (oldVersion < 2) createCategories(db);
        // Version 3 added big shops, notes and loans. Every existing expense keeps its meaning:
        // not a big shop, not part of a loan.
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE expenses ADD COLUMN bulk INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE expenses ADD COLUMN loan_id INTEGER");
            db.execSQL("ALTER TABLE expenses ADD COLUMN note TEXT");
            createLoans(db);
            seedCategories(db);
        }
    }

    private void createLoans(SQLiteDatabase db) {
        // What was lent. The lending itself and each repayment are rows in expenses that point
        // here, so they count in the daily numbers like any other money in or out.
        db.execSQL("CREATE TABLE loans ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "person TEXT NOT NULL, "
                + "amount INTEGER NOT NULL, "
                + "day TEXT NOT NULL, "
                + "note TEXT, "
                + "created_at INTEGER NOT NULL)");
    }

    private void createCategories(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE categories ("
                + "key TEXT PRIMARY KEY, "
                + "name TEXT NOT NULL, "
                + "icon TEXT NOT NULL, "
                + "color INTEGER NOT NULL, "
                + "position INTEGER NOT NULL, "
                + "archived INTEGER NOT NULL DEFAULT 0)");
        seedCategories(db);
    }

    /** The categories the app starts with. Rows that already exist are left as the user has them. */
    void seedCategories(SQLiteDatabase db) {
        Object[][] defaults = {
                {"junk_food", R.string.cat_junk_food, "hamburger", 0xFFE5682E},
                {"meals_out", R.string.cat_meals_out, "utensils", 0xFFC98500},
                {"groceries", R.string.cat_groceries, "shopping-basket", 0xFF1E9E3A},
                {"transport", R.string.cat_transport, "bus", 0xFF3A82E0},
                {"shopping", R.string.cat_shopping, "shopping-bag", 0xFFD9578C},
                {"bills", R.string.cat_bills, "receipt-text", 0xFF7A6BE0},
                {"fun", R.string.cat_fun, "clapperboard", 0xFF109C6B},
                {"other", R.string.cat_other, "sparkles", 0xFF7D7B75},
                {"loans", R.string.cat_loans, "hand-coins", 0xFF1397B8}};
        for (int i = 0; i < defaults.length; i++) {
            ContentValues v = new ContentValues();
            v.put("key", (String) defaults[i][0]);
            v.put("name", context.getString((Integer) defaults[i][1]));
            v.put("icon", (String) defaults[i][2]);
            v.put("color", (Integer) defaults[i][3]);
            v.put("position", i);
            db.insertWithOnConflict("categories", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }
}
