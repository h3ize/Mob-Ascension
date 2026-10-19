package com.mobascension;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;

import java.util.EnumSet;
public final class ChampionData {
    public static final String RANK = "mobascension_rank";
    public static final String APPLIED = "mobascension_applied";
    public static final String ABILITIES = "mobascension_abilities";
    public static final String MINION = "mobascension_minion";
    public static final String RANGED = "mobascension_ranged";
    public static final String LEVEL = "mobascension_level";
    public static final String HEALED = "mobascension_healed";
    public static final String UNOBTAINIUM = "mobascension_unobtainium";
    public static final String DRAGON_CHECKED = "mobascension_dragon_checked";

    private ChampionData() {}
    public static Rank getPendingRank(Entity e) {
        CompoundTag t = e.getPersistentData();
        if (!t.contains(RANK, Tag.TAG_STRING)) return null;
        return Rank.byId(t.getString(RANK));
    }
    public static Rank getRank(Entity e) {
        CompoundTag t = e.getPersistentData();
        if (!t.getBoolean(APPLIED)) return null;
        return getPendingRank(e);
    }
    public static int getLevel(Entity e) {
        return e.getPersistentData().getInt(LEVEL);
    }

    public static void setLevel(Entity e, int level) {
        e.getPersistentData().putInt(LEVEL, level);
    }

    public static boolean isMinion(Entity e) {
        return e.getPersistentData().getBoolean(MINION);
    }

    public static EnumSet<Ability> getAbilities(Entity e) {
        EnumSet<Ability> set = EnumSet.noneOf(Ability.class);
        String raw = e.getPersistentData().getString(ABILITIES);
        if (raw.isEmpty()) return set;
        for (String s : raw.split(",")) {
            Ability a = Ability.byId(s);
            if (a != null) set.add(a);
        }
        return set;
    }

    public static void setAbilities(Entity e, Iterable<Ability> abilities) {
        StringBuilder sb = new StringBuilder();
        for (Ability a : abilities) {
            if (sb.length() > 0) sb.append(',');
            sb.append(a.id);
        }
        e.getPersistentData().putString(ABILITIES, sb.toString());
    }
}
