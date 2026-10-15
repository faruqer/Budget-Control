package app.budgetcontrol.model;

import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;

import app.budgetcontrol.R;

/** A colour theme the user can pick. The colours themselves live in the theme each one names. */
public enum Palette {
    NEON("neon", R.string.palette_neon, R.style.Theme_BudgetControl_Neon),
    ELECTRIC("electric", R.string.palette_electric, R.style.Theme_BudgetControl_Electric),
    BERRY("berry", R.string.palette_berry, R.style.Theme_BudgetControl_Berry),
    LAGOON("lagoon", R.string.palette_lagoon, R.style.Theme_BudgetControl_Lagoon),
    MONO("mono", R.string.palette_mono, R.style.Theme_BudgetControl_Mono);

    /** Stored in the settings, so it must never change. */
    public final String key;
    @StringRes public final int nameRes;
    @StyleRes public final int themeRes;

    Palette(String key, @StringRes int nameRes, @StyleRes int themeRes) {
        this.key = key;
        this.nameRes = nameRes;
        this.themeRes = themeRes;
    }

    public static Palette fromKey(String key) {
        for (Palette p : values()) {
            if (p.key.equals(key)) return p;
        }
        return MONO;
    }
}
