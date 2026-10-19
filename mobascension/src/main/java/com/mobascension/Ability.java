package com.mobascension;
public enum Ability {
    VAMPIRIC("vampiric", "Vampiric", 1, 0.35, 0),
    THORNED("thorned", "Thorned", 1, 0.30, 0),
    FROSTBITE("frostbite", "Frostbite", 1, 5.0, 0),
    VENOMOUS("venomous", "Venomous", 1, 4.0, 0),
    BERSERKER("berserker", "Berserker", 2, 0.5, 0),
    SPLITTING("splitting", "Splitting", 3, 2.0, 0),
    STORMCALLER("stormcaller", "Stormcaller", 4, 5.0, 400);

    public final String id;
    public final String displayName;
    public final int defaultMinRank;
    public final double defaultPower;
    public final int defaultCooldown;

    Ability(String id, String displayName, int defaultMinRank, double defaultPower, int defaultCooldown) {
        this.id = id;
        this.displayName = displayName;
        this.defaultMinRank = defaultMinRank;
        this.defaultPower = defaultPower;
        this.defaultCooldown = defaultCooldown;
    }

    public static Ability byId(String id) {
        for (Ability a : values()) {
            if (a.id.equals(id)) return a;
        }
        return null;
    }
}
