package com.mobascension;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
public final class ChampionFactory {
    public static final String GEAR_TAG = "mobascension_gear";
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private ChampionFactory() {}

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MobAscension.MODID, path);
    }

    public static boolean isEligible(Mob mob) {
        if (!AscensionConfig.ENABLED.get()) return false;
        if (mob instanceof Warden || mob instanceof EnderDragon || mob instanceof WitherBoss
                || mob instanceof Creeper || mob instanceof Vex) return false;
        if (ChampionData.isMinion(mob)) return false;

        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        String keyStr = key.toString();
        if (AscensionConfig.BLACKLIST.get().contains(keyStr)) return false;
        List<? extends String> whitelist = AscensionConfig.WHITELIST.get();
        if (!whitelist.isEmpty() && !whitelist.contains(keyStr)) return false;
        if (AscensionConfig.VANILLA_ONLY.get() && !key.getNamespace().equals("minecraft")) return false;
        if (!AscensionConfig.INCLUDE_PASSIVE.get() && !(mob instanceof Enemy)) return false;
        return true;
    }
    public static Rank roll(RandomSource random) {
        double total = AscensionConfig.COMMON_WEIGHT.get();
        for (Rank r : Rank.values()) total += AscensionConfig.rank(r).weight.get();
        if (total <= 0) return null;
        double pick = random.nextDouble() * total;
        if (pick < AscensionConfig.COMMON_WEIGHT.get()) return null;
        pick -= AscensionConfig.COMMON_WEIGHT.get();
        for (Rank r : Rank.values()) {
            pick -= AscensionConfig.rank(r).weight.get();
            if (pick < 0) return r;
        }
        return null;
    }
    public static double statScale(Rank rank) {
        return AscensionConfig.rank(rank).statLevel.get() / 5.0;
    }
    public static double statScale(Mob mob, Rank rank) {
        int level = ChampionData.getLevel(mob);
        if (level <= 0) return statScale(rank);
        return level / 5.0;
    }

    public static double damageMultiplier(Mob mob, Rank rank) {
        return 1.0 + AscensionConfig.rank(rank).damage.get() * statScale(mob, rank);
    }

    public static void apply(Mob mob, ServerLevel level, Rank rank) {
        AscensionConfig.RankSettings cfg = AscensionConfig.rank(rank);
        RandomSource random = mob.getRandom();

        int maxLevel = Math.max(1, cfg.statLevel.get());
        int minLevel = Math.max(1, Math.min(cfg.statLevelMin.get(), maxLevel));
        int rolledLevel = minLevel + random.nextInt(maxLevel - minLevel + 1);
        ChampionData.setLevel(mob, rolledLevel);
        double s = rolledLevel / 5.0;

        List<Ability> abilities = rollAbilities(mob, rank, cfg);
        ChampionData.setAbilities(mob, abilities);

        mod(mob, Attributes.MAX_HEALTH, "health", cfg.health.get() * s, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        mod(mob, Attributes.ARMOR, "armor", cfg.armor.get() * s, AttributeModifier.Operation.ADD_VALUE);
        mod(mob, Attributes.ARMOR_TOUGHNESS, "toughness", cfg.toughness.get() * s, AttributeModifier.Operation.ADD_VALUE);
        double speedBonus = Math.min(1.0, cfg.speed.get() * s);
        mod(mob, Attributes.MOVEMENT_SPEED, "speed", speedBonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        mod(mob, Attributes.FLYING_SPEED, "flying_speed", speedBonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        mod(mob, Attributes.ATTACK_DAMAGE, "damage", cfg.damage.get() * s, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        mod(mob, Attributes.KNOCKBACK_RESISTANCE, "knockback", Math.min(1.0, cfg.knockback.get() * s), AttributeModifier.Operation.ADD_VALUE);
        mod(mob, Attributes.SAFE_FALL_DISTANCE, "fall", cfg.fall.get() * s, AttributeModifier.Operation.ADD_VALUE);
        mod(mob, Attributes.SCALE, "size", cfg.size.get() * s, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        mod(mob, Attributes.FOLLOW_RANGE, "follow", 4.0 * (rank.ordinal() + 1), AttributeModifier.Operation.ADD_VALUE);
        mob.setHealth(mob.getMaxHealth());

        if (cfg.fireResistant.get()) infinite(mob, MobEffects.FIRE_RESISTANCE);
        if (random.nextDouble() < cfg.infested.get()) infinite(mob, MobEffects.INFESTED);
        if (random.nextDouble() < cfg.oozing.get()) infinite(mob, MobEffects.OOZING);
        if (rank == Rank.APEX) {
            infinite(mob, MobEffects.DAMAGE_BOOST, 0);
            List<Holder<net.minecraft.world.effect.MobEffect>> extras = new ArrayList<>(List.of(
                    MobEffects.MOVEMENT_SPEED, MobEffects.JUMP, MobEffects.FIRE_RESISTANCE, MobEffects.WATER_BREATHING,
                    MobEffects.SLOW_FALLING, MobEffects.DIG_SPEED));
            Collections.shuffle(extras, new Random(random.nextLong()));
            int added = 0;
            for (Holder<net.minecraft.world.effect.MobEffect> e : extras) {
                if (added >= 2) break;
                if (mob.hasEffect(e)) continue;
                infinite(mob, e, 0);
                added++;
            }
        }
        int glow = cfg.glowSeconds.get();
        if (glow != 0) {
            int ticks = glow < 0 ? MobEffectInstance.INFINITE_DURATION : glow * 20;
            mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, true, true));
        }

        mob.setHealth(mob.getMaxHealth());

        if (rank == Rank.APEX && (!(mob instanceof Enemy) || random.nextDouble() < cfg.rangedChance.get())) {
            mob.getPersistentData().putBoolean(ChampionData.RANGED, true);
        }

        equip(mob, level, rank, cfg);
        if (rank == Rank.APEX) {
            applyAtmGear(mob, level, rank, cfg, random);
            buffApexBow(mob, level);
        }

        if (AscensionConfig.RANK_IN_NAME.get()) applyName(mob, rank, cfg);

        if (AscensionConfig.GLOW_COLORS.get()) applyTeam(mob, level, rank);

        if (cfg.announce.get()) {
            double r = AscensionConfig.ANNOUNCE_RADIUS.get();
            StringBuilder ab = new StringBuilder();
            for (Ability a : abilities) {
                if (ab.length() > 0) ab.append(", ");
                ab.append(a.displayName);
            }
            String rankName = cfg.displayName.get();
            String article = "AEIOUaeiou".indexOf(rankName.isEmpty() ? 'x' : rankName.charAt(0)) >= 0 ? "An " : "A ";
            Component msg = Component.literal(article + rankName + " ")
                    .append(mob.getType().getDescription().copy())
                    .append(Component.literal(" has ascended nearby!"))
                    .append(ab.length() > 0 ? Component.literal(" [" + ab + "]") : CommonComponents.EMPTY)
                    .withStyle(rank.format);
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(mob) <= r * r) p.sendSystemMessage(msg);
            }
        }
    }
    public static boolean shouldAscendDragon(ServerLevel level) {
        if (!AscensionConfig.ENABLED.get() || !AscensionConfig.DRAGON_ENABLED.get()) return false;
        if (!AscensionConfig.DRAGON_CRYSTAL_ONLY.get()) return true;
        EndDragonFight fight = level.getDragonFight();
        return fight != null && fight.hasPreviouslyKilledDragon();
    }
    public static void applyDragon(EnderDragon dragon, ServerLevel level) {
        Rank rank = Rank.APEX;
        AscensionConfig.RankSettings cfg = AscensionConfig.rank(rank);
        dragon.getPersistentData().putString(ChampionData.RANK, rank.id);
        dragon.getPersistentData().putBoolean(ChampionData.APPLIED, true);
        dragon.getPersistentData().putBoolean(ChampionData.HEALED, true);
        ChampionData.setLevel(dragon, Math.max(1, cfg.statLevel.get()));

        List<Ability> abilities = new ArrayList<>();
        for (Ability a : Ability.values()) {
            if (a == Ability.SPLITTING || a == Ability.BERSERKER) continue;
            if (AscensionConfig.ability(a).enabled.get()) abilities.add(a);
        }
        ChampionData.setAbilities(dragon, abilities);

        AttributeInstance hp = dragon.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(AscensionConfig.DRAGON_HEALTH.get());
        dragon.setHealth(dragon.getMaxHealth());

        int glow = cfg.glowSeconds.get();
        if (glow != 0) {
            int ticks = glow < 0 ? MobEffectInstance.INFINITE_DURATION : glow * 20;
            dragon.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, true, true));
        }
        if (AscensionConfig.RANK_IN_NAME.get()) applyName(dragon, rank, cfg);
        if (AscensionConfig.GLOW_COLORS.get()) applyTeam(dragon, level, rank);

        StringBuilder ab = new StringBuilder();
        for (Ability a : abilities) {
            if (ab.length() > 0) ab.append(", ");
            ab.append(a.displayName);
        }
        Component msg = Component.literal("The Ender Dragon has been empowered by the crystals and has ascended!")
                .append(ab.length() > 0 ? Component.literal(" [" + ab + "]") : CommonComponents.EMPTY)
                .withStyle(rank.format);
        for (ServerPlayer p : level.players()) p.sendSystemMessage(msg);
    }

    private static List<Ability> rollAbilities(Mob mob, Rank rank, AscensionConfig.RankSettings cfg) {
        List<Ability> pool = new ArrayList<>();
        for (Ability a : Ability.values()) {
            AscensionConfig.AbilitySettings as = AscensionConfig.ability(a);
            if (as.enabled.get() && as.minRank.get() <= rank.ordinal() + 1) pool.add(a);
        }
        Collections.shuffle(pool, new Random(mob.getRandom().nextLong()));
        int n = Math.min(cfg.abilityCount.get(), pool.size());
        return new ArrayList<>(pool.subList(0, n));
    }

    private static void infinite(Mob mob, Holder<net.minecraft.world.effect.MobEffect> effect) {
        infinite(mob, effect, 0);
    }

    private static void infinite(Mob mob, Holder<net.minecraft.world.effect.MobEffect> effect, int amplifier) {
        mob.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, amplifier, false, true, true));
    }

    private static void mod(Mob mob, Holder<Attribute> attr, String name, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst == null || amount == 0) return;
        ResourceLocation id = id(name);
        inst.removeModifier(id);
        inst.addPermanentModifier(new AttributeModifier(id, amount, op));
    }

    private static void applyName(Mob mob, Rank rank, AscensionConfig.RankSettings cfg) {
        Component base = mob.hasCustomName() ? mob.getCustomName().copy() : mob.getType().getDescription().copy();
        mob.setCustomName(Component.literal(cfg.displayName.get() + " ").append(base).withStyle(rank.format));
        mob.setCustomNameVisible(AscensionConfig.NAME_ALWAYS_VISIBLE.get());
    }

    private static void applyTeam(Mob mob, ServerLevel level, Rank rank) {
        if (mob.getTeam() != null) return;
        Scoreboard sb = level.getScoreboard();
        String teamName = "ma_" + rank.id;
        PlayerTeam team = sb.getPlayerTeam(teamName);
        if (team == null) {
            team = sb.addPlayerTeam(teamName);
        }
        team.setColor(rank.format);
        sb.addPlayerToTeam(mob.getScoreboardName(), team);
    }

    private static void equip(Mob mob, ServerLevel level, Rank rank, AscensionConfig.RankSettings cfg) {
        if (!(mob instanceof Enemy)) return;
        RandomSource random = mob.getRandom();
        boolean replace = AscensionConfig.REPLACE_VANILLA_GEAR.get();

        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (random.nextDouble() >= cfg.gearChance.get()) continue;
            if (!mob.getItemBySlot(slot).isEmpty() && !replace) continue;
            giveGear(mob, level, rank, cfg, slot, random);
        }


        if (!(mob instanceof RangedAttackMob) && random.nextDouble() < cfg.gearChance.get()) {
            ItemStack current = mob.getMainHandItem();
            boolean ranged = current.getItem() instanceof ProjectileWeaponItem || current.getItem() instanceof TridentItem;
            if (!ranged && (current.isEmpty() || replace)) {
                giveGear(mob, level, rank, cfg, EquipmentSlot.MAINHAND, random);
            }
        }
    }
    private static final int APEX_SKELETON_POWER = 10;

    private static void buffApexBow(Mob mob, ServerLevel level) {
        if (!(mob instanceof net.minecraft.world.entity.monster.AbstractSkeleton)) return;
        ItemStack bow = mob.getMainHandItem();
        if (!(bow.getItem() instanceof net.minecraft.world.item.BowItem)) return;
        level.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolder(net.minecraft.world.item.enchantment.Enchantments.POWER)
                .ifPresent(h -> bow.enchant(h, APEX_SKELETON_POWER));
        mob.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    private static final String ATM_MODID = "allthemodium";
    private static final String[] ATM_METALS = {"allthemodium", "vibranium", "unobtainium"};
    private static void applyAtmGear(Mob mob, ServerLevel level, Rank rank, AscensionConfig.RankSettings cfg, RandomSource random) {
        if (!(mob instanceof Enemy)) return;
        if (!ModList.get().isLoaded(ATM_MODID)) return;
        if (random.nextDouble() >= AscensionConfig.APEX_ATM_GEAR_CHANCE.get()) return;

        String metal = ATM_METALS[random.nextInt(ATM_METALS.length)];
        giveAtmPiece(mob, level, rank, cfg, metal, "helmet", EquipmentSlot.HEAD, random);
        giveAtmPiece(mob, level, rank, cfg, metal, "chestplate", EquipmentSlot.CHEST, random);
        giveAtmPiece(mob, level, rank, cfg, metal, "leggings", EquipmentSlot.LEGS, random);
        giveAtmPiece(mob, level, rank, cfg, metal, "boots", EquipmentSlot.FEET, random);
        if (metal.equals("unobtainium") && !mob.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
            mob.getPersistentData().putBoolean(ChampionData.UNOBTAINIUM, true);
        }

        if (!(mob instanceof RangedAttackMob)) {
            ItemStack current = mob.getMainHandItem();
            boolean ranged = current.getItem() instanceof ProjectileWeaponItem || current.getItem() instanceof TridentItem;
            if (!ranged) giveAtmPiece(mob, level, rank, cfg, metal, "sword", EquipmentSlot.MAINHAND, random);
        }
    }

    private static void giveAtmPiece(Mob mob, ServerLevel level, Rank rank, AscensionConfig.RankSettings cfg,
                                     String metal, String piece, EquipmentSlot slot, RandomSource random) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ATM_MODID, metal + "_" + piece);
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
        if (item == Items.AIR) return;
        ItemStack stack = new ItemStack(item);
        if (random.nextDouble() < cfg.enchantChance.get()) {
            GearEnchanter.enchant(stack, rank, slot == EquipmentSlot.MAINHAND, level.registryAccess(), random);
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(GEAR_TAG, true));
        mob.setItemSlot(slot, stack);
        mob.setDropChance(slot, 0.0F);
    }

    private static void giveGear(Mob mob, ServerLevel level, Rank rank, AscensionConfig.RankSettings cfg,
                                 EquipmentSlot slot, RandomSource random) {
        ItemStack stack = makeGear(level, rank, slot, random);
        if (stack == null) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(GEAR_TAG, true));
        mob.setItemSlot(slot, stack);
        mob.setDropChance(slot, 0.0F);
    }
    public static ItemStack makeGear(ServerLevel level, Rank rank, EquipmentSlot slot, RandomSource random) {
        AscensionConfig.RankSettings cfg = AscensionConfig.rank(rank);
        ItemStack stack = pickItem(cfg, slot, random);
        if (stack == null) return null;
        finishGear(stack, level, rank, slot, random);
        return stack;
    }

    private static void finishGear(ItemStack stack, ServerLevel level, Rank rank, EquipmentSlot slot, RandomSource random) {
        AscensionConfig.RankSettings cfg = AscensionConfig.rank(rank);
        if (slot != EquipmentSlot.MAINHAND && random.nextDouble() < cfg.trim.get()) {
            applyTrim(stack, level, random);
        }
        if (random.nextDouble() < cfg.enchantChance.get()) {
            GearEnchanter.enchant(stack, rank, slot == EquipmentSlot.MAINHAND, level.registryAccess(), random);
        }
    }
    private static void applyTrim(ItemStack stack, ServerLevel level, RandomSource random) {
        var patterns = level.registryAccess().registryOrThrow(Registries.TRIM_PATTERN).holders()
                .filter(h -> ResourceLocation.DEFAULT_NAMESPACE.equals(h.key().location().getNamespace())).toList();
        var materials = level.registryAccess().registryOrThrow(Registries.TRIM_MATERIAL).holders()
                .filter(h -> ResourceLocation.DEFAULT_NAMESPACE.equals(h.key().location().getNamespace())).toList();
        if (patterns.isEmpty() || materials.isEmpty()) return;
        stack.set(DataComponents.TRIM, new ArmorTrim(
                materials.get(random.nextInt(materials.size())),
                patterns.get(random.nextInt(patterns.size()))));
    }
    public static ItemStack pickItem(AscensionConfig.RankSettings cfg, EquipmentSlot slot, RandomSource random) {
        List<? extends String> list = cfg.gear.get(slot).get();
        if (list.isEmpty()) return null;
        ResourceLocation id = ResourceLocation.tryParse(list.get(random.nextInt(list.size())));
        if (id == null) return null;
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
        return item == Items.AIR ? null : new ItemStack(item);
    }
}
