package app.budgetcontrol.model;

/** What happens to money left unspent at the end of a day. */
public enum RolloverMode {
    NONE("none"),
    CARRY("carry"),
    SAVINGS("savings");

    public final String key;

    RolloverMode(String key) {
        this.key = key;
    }

    public static RolloverMode fromKey(String key) {
        for (RolloverMode m : values()) {
            if (m.key.equals(key)) return m;
        }
        return CARRY;
    }
}
