package com.mobascension;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.advancements.AdvancementHolder;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class ChampionEvents {
    @SubscribeEvent
    public void onFinalizeSpawn(FinalizeSpawnEvent event) {
        Mob mob = event.getEntity();
        if (mob.level().isClientSide) return;
        if (ChampionData.getPendingRank(mob) != null) return;
        if (AscensionConfig.EXCLUDED_SPAWN_TYPES.get().contains(event.getSpawnType().name())) return;
        if (!ChampionFactory.isEligible(mob)) return;

        Rank rank = ChampionFactory.roll(mob.getRandom());
        if (rank != null) {
            mob.getPersistentData().putString(ChampionData.RANK, rank.id);
        }
    }
    @SubscribeEvent
    public void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Mob mob)) return;
        if (mob instanceof EnderDragon dragon) {
            if (!dragon.getPersistentData().getBoolean(ChampionData.DRAGON_CHECKED)) {
                dragon.getPersistentData().putBoolean(ChampionData.DRAGON_CHECKED, true);
                if (ChampionFactory.shouldAscendDragon(level)) ChampionFactory.applyDragon(dragon, level);
            }
            return;
        }
        Rank rank = ChampionData.getPendingRank(mob);
        if (rank == null || mob.getPersistentData().getBoolean(ChampionData.APPLIED)) return;
        mob.getPersistentData().putBoolean(ChampionData.APPLIED, true);
        ChampionFactory.apply(mob, level, rank);
    }
    @SubscribeEvent
    public void onEnderTeleport(EntityTeleportEvent.EnderEntity event) {
        if (event.getEntity() instanceof Mob mob && ChampionData.getRank(mob) != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (!(mob.level() instanceof ServerLevel level) || !mob.isAlive()) return;
        Rank rank = ChampionData.getRank(mob);
        if (rank == null) return;
        if (!mob.getPersistentData().getBoolean(ChampionData.HEALED)) {
            mob.getPersistentData().putBoolean(ChampionData.HEALED, true);
            mob.setHealth(mob.getMaxHealth());
        }
        AbilityLogic.tick(mob, level, rank, ChampionData.getAbilities(mob));
    }

    @SubscribeEvent
    public void onDamagePre(LivingDamageEvent.Pre event) {
        DamageSource source = event.getSource();
        if (source.getEntity() instanceof EnderDragon dragon && ChampionData.getRank(dragon) != null) {
            double mult = AscensionConfig.DRAGON_DAMAGE_MULT.get();
            if (dragon.getHealth() / dragon.getMaxHealth() <= AscensionConfig.DRAGON_ENRAGE_AT.get()) mult *= 1.5;
            event.setNewDamage((float) (event.getNewDamage() * mult));
            return;
        }
        if (!AscensionConfig.SCALE_RANGED_DAMAGE.get()) return;
        if (!(source.getEntity() instanceof Mob attacker)) return;
        if (source.getDirectEntity() == attacker || !source.is(DamageTypeTags.IS_PROJECTILE)) return;
        Rank rank = ChampionData.getRank(attacker);
        if (rank == null) return;
        event.setNewDamage((float) (event.getNewDamage() * ChampionFactory.damageMultiplier(attacker, rank)));
    }

    @SubscribeEvent
    public void onDamagePost(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();
        float damage = event.getNewDamage();
        if (damage <= 0 || victim.level().isClientSide) return;

        if (source.getEntity() instanceof Mob attacker && ChampionData.getRank(attacker) != null) {
            AbilityLogic.onAttack(attacker, victim, damage, ChampionData.getAbilities(attacker));
        }

        if (victim instanceof Mob champion && ChampionData.getRank(champion) != null
                && source.getEntity() instanceof LivingEntity attacker
                && source.getDirectEntity() == attacker && !source.is(DamageTypes.THORNS)) {
            AbilityLogic.onDefend(champion, attacker, damage, ChampionData.getAbilities(champion));
        }
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) return;
        Rank rank = ChampionData.getRank(mob);
        if (rank == null) return;

        AbilityLogic.onDeath(mob, level, ChampionData.getAbilities(mob));

        if (!ChampionData.isMinion(mob) && mob.getKillCredit() instanceof ServerPlayer killer) {
            int lvMin = AscensionConfig.rank(rank).killLevels.get();
            int lvMax = Math.max(lvMin, AscensionConfig.rank(rank).killLevelsMax.get());
            int levels = lvMin + mob.getRandom().nextInt(lvMax - lvMin + 1);
            if (levels > 0) killer.giveExperienceLevels(levels);
            if (rank == Rank.APEX) {
                AdvancementHolder goal = level.getServer().getAdvancements().get(ChampionFactory.id("apex_slayer"));
                if (goal != null) killer.getAdvancements().award(goal, "apex_kill");
            }
        }

        if (AscensionConfig.rank(rank).fireworks.get() && mob.getKillCredit() instanceof Player) {
            launchFireworks(level, mob, rank);
        }
    }

    private static void launchFireworks(ServerLevel level, Mob mob, Rank rank) {
        for (int i = 0; i < 3; i++) {
            ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
            IntList colors = new IntArrayList(new int[]{rank.color, 0xFFFFFF});
            FireworkExplosion explosion = new FireworkExplosion(
                    i == 1 ? FireworkExplosion.Shape.STAR : FireworkExplosion.Shape.LARGE_BALL,
                    colors, new IntArrayList(), true, true);
            rocket.set(DataComponents.FIREWORKS, new Fireworks(1 + i % 2, List.of(explosion)));
            double dx = (mob.getRandom().nextDouble() - 0.5) * 3;
            double dz = (mob.getRandom().nextDouble() - 0.5) * 3;
            level.addFreshEntity(new FireworkRocketEntity(level, mob.getX() + dx, mob.getY(), mob.getZ() + dz, rocket));
        }
    }

    @SubscribeEvent
    public void onXp(LivingExperienceDropEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        Rank rank = ChampionData.getRank(mob);
        if (ChampionData.isMinion(mob) && !AscensionConfig.MINIONS_DROP_LOOT.get()) {
            event.setDroppedExperience(0);
            return;
        }
        if (rank == null) return;
        event.setDroppedExperience((int) (event.getDroppedExperience() * AscensionConfig.rank(rank).xp.get()));
    }

    @SubscribeEvent
    public void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) return;

        if (ChampionData.isMinion(mob)) {
            if (!AscensionConfig.MINIONS_DROP_LOOT.get()) event.getDrops().clear();
            return;
        }
        Rank rank = ChampionData.getRank(mob);
        if (rank == null) return;
        if (AscensionConfig.REQUIRE_PLAYER_KILL.get() && !(mob.getKillCredit() instanceof Player)) return;

        RandomSource random = mob.getRandom();

        AscensionConfig.RankSettings cfg = AscensionConfig.rank(rank);
        int gearMin = cfg.gearDropsMin.get();
        int gearMax = Math.max(gearMin, cfg.gearDropsMax.get());
        int count = gearMin + random.nextInt(gearMax - gearMin + 1);
        boolean weapon = count > 0 && random.nextBoolean();
        List<EquipmentSlot> armorSlots = new ArrayList<>(List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET));
        Collections.shuffle(armorSlots, new java.util.Random(random.nextLong()));

        if (weapon) {
            ItemStack stack;
            if (rank == Rank.APEX && random.nextBoolean()) {
                stack = ChampionLoot.apexBow(level, random);
            } else {
                stack = mob.getItemBySlot(EquipmentSlot.MAINHAND);
                if (!stack.isEmpty() && isModGear(stack)) {
                    stack = stack.copy();
                } else {
                    stack = ChampionFactory.makeGear(level, rank, EquipmentSlot.MAINHAND, random);
                }
            }
            if (stack != null) drop(event, mob, wear(stack, random));
            count--;
        }
        for (int i = 0; i < count && !armorSlots.isEmpty(); i++) {
            EquipmentSlot slot = armorSlots.remove(0);
            ItemStack stack = mob.getItemBySlot(slot);
            if (!stack.isEmpty() && isModGear(stack)) {
                stack = stack.copy();
            } else {
                stack = ChampionFactory.makeGear(level, rank, slot, random);
            }
            if (stack != null) drop(event, mob, wear(stack, random));
        }

        for (ItemStack reward : ChampionLoot.roll(level, rank, random)) {
            drop(event, mob, reward);
        }

        if (mob.getPersistentData().getBoolean(ChampionData.UNOBTAINIUM)
                && random.nextDouble() < AscensionConfig.UNOBTAINIUM_DROP_CHANCE.get()) {
            ItemStack special = ChampionLoot.rollUnobtainiumDrop(random);
            if (special != null) drop(event, mob, special);
        }
    }
    private static ItemStack wear(ItemStack stack, RandomSource random) {
        stack.remove(DataComponents.CUSTOM_DATA);
        if (stack.isDamageableItem()) {
            stack.setDamageValue((int) (stack.getMaxDamage() * (0.05 + 0.80 * random.nextDouble())));
        }
        return stack;
    }

    private static boolean isModGear(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().contains(ChampionFactory.GEAR_TAG);
    }

    private static void drop(LivingDropsEvent event, Entity mob, ItemStack stack) {
        ItemEntity entity = new ItemEntity(mob.level(), mob.getX(), mob.getY() + 0.5, mob.getZ(), stack);
        RandomSource r = mob.level().random;
        entity.setDeltaMovement((r.nextDouble() - 0.5) * 0.25, 0.2 + r.nextDouble() * 0.15, (r.nextDouble() - 0.5) * 0.25);
        event.getDrops().add(entity);
    }
}
