package app.budgetcontrol.model;

import androidx.annotation.ColorInt;

/** A spending category. The built-in ones and the user's own are the same kind of thing. */
public final class Category {
    /** The category expenses fall back to. It can be renamed but never removed. */
    public static final String OTHER_KEY = "other";
    /** Lending and repayments are filed here. It is not offered in the picker. */
    public static final String LOANS_KEY = "loans";

    /** Stored on every expense, so it never changes once the category exists. */
    public final String key;
    public final String name;
    /** Names an icon in {@code CategoryIcons}. */
    public final String icon;
    @ColorInt public final int color;
    /** Removed by the user: hidden from the picker, kept so old expenses still show properly. */
    public final boolean archived;

    public Category(String key, String name, String icon, @ColorInt int color, boolean archived) {
        this.key = key;
        this.name = name;
        this.icon = icon;
        this.color = color;
        this.archived = archived;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Category && ((Category) o).key.equals(key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }
}
