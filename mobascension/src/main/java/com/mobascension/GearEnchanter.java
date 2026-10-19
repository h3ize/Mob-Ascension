package com.mobascension;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
public final class GearEnchanter {
    private static final List<ResourceKey<Enchantment>> POOL = List.of(
            Enchantments.PROTECTION, Enchantments.FIRE_PROTECTION, Enchantments.BLAST_PROTECTION,
            Enchantments.PROJECTILE_PROTECTION, Enchantments.THORNS, Enchantments.UNBREAKING,
            Enchantments.RESPIRATION, Enchantments.AQUA_AFFINITY, Enchantments.FEATHER_FALLING,
            Enchantments.DEPTH_STRIDER, Enchantments.SOUL_SPEED, Enchantments.SWIFT_SNEAK,
            Enchantments.SHARPNESS, Enchantments.SMITE, Enchantments.BANE_OF_ARTHROPODS,
            Enchantments.FIRE_ASPECT, Enchantments.KNOCKBACK, Enchantments.LOOTING,
            Enchantments.SWEEPING_EDGE, Enchantments.DENSITY, Enchantments.BREACH, Enchantments.WIND_BURST);

    private static final List<ResourceKey<Enchantment>> PROTECTIONS = List.of(
            Enchantments.PROTECTION, Enchantments.FIRE_PROTECTION, Enchantments.BLAST_PROTECTION,
            Enchantments.PROJECTILE_PROTECTION);
    private static final List<ResourceKey<Enchantment>> DAMAGE = List.of(
            Enchantments.SHARPNESS, Enchantments.SMITE, Enchantments.BANE_OF_ARTHROPODS);

    private GearEnchanter() {}

    public static void enchant(ItemStack stack, Rank rank, boolean weapon, RegistryAccess access, RandomSource random) {
        AscensionConfig.RankSettings cfg = AscensionConfig.rank(rank);
        Registry<Enchantment> registry = access.registryOrThrow(Registries.ENCHANTMENT);

        List<ResourceKey<Enchantment>> pool = new ArrayList<>(POOL);
        Collections.shuffle(pool, new Random(random.nextLong()));

        int wanted = cfg.enchantCount.get();
        int cap = weapon ? cfg.weaponEnchantLevel.get() : cfg.maxEnchantLevel.get();
        List<Holder<Enchantment>> chosen = new ArrayList<>();

        for (ResourceKey<Enchantment> key : pool) {
            if (chosen.size() >= wanted) break;
            Optional<Holder.Reference<Enchantment>> holder = registry.getHolder(key);
            if (holder.isEmpty()) continue;
            Holder<Enchantment> h = holder.get();
            if (!h.value().canEnchant(stack)) continue;
            boolean compatible = true;
            for (Holder<Enchantment> other : chosen) {
                if (!Enchantment.areCompatible(h, other)) { compatible = false; break; }
            }
            if (!compatible) continue;

            int hi = Math.max(1, Math.min(h.value().getMaxLevel(), cap));
            if (!weapon && PROTECTIONS.contains(key)) hi = Math.min(hi, cfg.maxProtectionLevel.get());
            int lo = hi;
            if (weapon && DAMAGE.contains(key) && hi >= 4) lo = hi - 2;
            int level = lo + random.nextInt(hi - lo + 1);
            stack.enchant(h, level);
            chosen.add(h);
        }
    }
}
