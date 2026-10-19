package com.mobascension;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.item.Rarity;
public enum Rank {
    VETERAN("veteran", "Veteran", ChatFormatting.GREEN, 0x55FF55, Rarity.UNCOMMON),
    ELITE("elite", "Elite", ChatFormatting.AQUA, 0x55FFFF, Rarity.RARE),
    MYTHIC("mythic", "Mythic", ChatFormatting.LIGHT_PURPLE, 0xFF55FF, Rarity.EPIC),
    APEX("apex", "Apex", ChatFormatting.GOLD, 0xFFAA00, Rarity.EPIC);

    public final String id;
    public final String defaultName;
    public final ChatFormatting format;
    public final int color;
    public final Rarity rarity;

    Rank(String id, String defaultName, ChatFormatting format, int color, Rarity rarity) {
        this.id = id;
        this.defaultName = defaultName;
        this.format = format;
        this.color = color;
        this.rarity = rarity;
    }
    public SimpleParticleType particle() {
        return switch (this) {
            case VETERAN -> ParticleTypes.HAPPY_VILLAGER;
            case ELITE -> ParticleTypes.SOUL_FIRE_FLAME;
            case MYTHIC -> ParticleTypes.WITCH;
            case APEX -> ParticleTypes.FLAME;
        };
    }

    public static Rank byId(String id) {
        for (Rank r : values()) {
            if (r.id.equalsIgnoreCase(id)) return r;
        }
        return null;
    }
}
