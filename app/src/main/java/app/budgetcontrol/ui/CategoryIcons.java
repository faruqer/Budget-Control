package app.budgetcontrol.ui;

import androidx.annotation.DrawableRes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import app.budgetcontrol.R;

/** The icons and colours a category can be given. Categories store the icon's name, not its id. */
public final class CategoryIcons {

    private CategoryIcons() {}

    private static final Map<String, Integer> ICONS = new LinkedHashMap<>();

    static {
        ICONS.put("hamburger", R.drawable.ic_hamburger);
        ICONS.put("utensils", R.drawable.ic_utensils);
        ICONS.put("shopping-basket", R.drawable.ic_shopping_basket);
        ICONS.put("bus", R.drawable.ic_bus);
        ICONS.put("shopping-bag", R.drawable.ic_shopping_bag);
        ICONS.put("receipt-text", R.drawable.ic_receipt_text);
        ICONS.put("clapperboard", R.drawable.ic_clapperboard);
        ICONS.put("sparkles", R.drawable.ic_sparkles);
        ICONS.put("coffee", R.drawable.ic_coffee);
        ICONS.put("pizza", R.drawable.ic_pizza);
        ICONS.put("popcorn", R.drawable.ic_popcorn);
        ICONS.put("beer", R.drawable.ic_beer);
        ICONS.put("cigarette", R.drawable.ic_cigarette);
        ICONS.put("car", R.drawable.ic_car);
        ICONS.put("fuel", R.drawable.ic_fuel);
        ICONS.put("plane", R.drawable.ic_plane);
        ICONS.put("house", R.drawable.ic_house);
        ICONS.put("wifi", R.drawable.ic_wifi);
        ICONS.put("smartphone", R.drawable.ic_smartphone);
        ICONS.put("shirt", R.drawable.ic_shirt);
        ICONS.put("scissors", R.drawable.ic_scissors);
        ICONS.put("gift", R.drawable.ic_gift);
        ICONS.put("heart-pulse", R.drawable.ic_heart_pulse);
        ICONS.put("pill", R.drawable.ic_pill);
        ICONS.put("dumbbell", R.drawable.ic_dumbbell);
        ICONS.put("book-open", R.drawable.ic_book_open);
        ICONS.put("graduation-cap", R.drawable.ic_graduation_cap);
        ICONS.put("music", R.drawable.ic_music);
        ICONS.put("paw-print", R.drawable.ic_paw_print);
        ICONS.put("baby", R.drawable.ic_baby);
        ICONS.put("piggy-bank", R.drawable.ic_piggy_bank);
        ICONS.put("wallet", R.drawable.ic_wallet);
        ICONS.put("hand-coins", R.drawable.ic_hand_coins);
    }

    /** Mid-tones, so each reads on both the white and the black background. */
    public static final int[] COLORS = {
            0xFFE5682E, 0xFFC98500, 0xFF1E9E3A, 0xFF109C6B, 0xFF1397B8,
            0xFF3A82E0, 0xFF7A6BE0, 0xFFD9578C, 0xFFD9534F, 0xFF7D7B75};

    public static List<String> names() {
        return new ArrayList<>(ICONS.keySet());
    }

    /** Unknown names get the generic icon, so a category never ends up without one. */
    @DrawableRes
    public static int res(String name) {
        Integer res = ICONS.get(name);
        return res != null ? res : R.drawable.ic_sparkles;
    }
}
