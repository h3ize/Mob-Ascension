package com.mobascension;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
public final class AbilityLogic {
    private static final net.minecraft.resources.ResourceLocation BERSERK_SPEED = ChampionFactory.id("berserk_speed");
    private static final net.minecraft.resources.ResourceLocation BERSERK_DAMAGE = ChampionFactory.id("berserk_damage");

    private AbilityLogic() {}

    private static double power(Ability a) { return AscensionConfig.ability(a).power.get(); }

    private static int cooldown(Ability a) { return Math.max(1, AscensionConfig.ability(a).cooldown.get()); }

    private static boolean ready(Mob mob, Ability a) {
        return (mob.tickCount + mob.getId()) % cooldown(a) == 0;
    }

    public static void tick(Mob mob, ServerLevel level, Rank rank, EnumSet<Ability> abilities) {
        AscensionConfig.RankSettings cfg = AscensionConfig.rank(rank);

        if (AscensionConfig.PARTICLES.get() && mob.tickCount % 10 == 0) {
            double w = mob.getBbWidth();
            level.sendParticles(rank.particle(), mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(),
                    2 + rank.ordinal(), w * 0.5, mob.getBbHeight() * 0.4, w * 0.5, 0.02);
        }

        if (mob instanceof EnderDragon dragon) dragonTick(dragon, level);

        if (rank == Rank.APEX && mob.tickCount % 20 == 0) {
            int radius = AscensionConfig.APEX_AGGRO_RADIUS.get();
            if (radius > 0) aggro(mob, level, radius);
        }

        if (rank == Rank.APEX && mob.getPersistentData().getBoolean(ChampionData.RANGED)
                && (mob.tickCount + mob.getId()) % Math.max(1, cfg.rangedCooldown.get()) == 0) {
            LivingEntity t = mob.getTarget();
            if (t != null && t.isAlive()) {
                double d = mob.distanceTo(t);
                if (d >= 4 && d <= 32 && mob.hasLineOfSight(t)) shoot(mob, level, rank, t);
            }
        }

        if (abilities.isEmpty()) return;

        if (abilities.contains(Ability.BERSERKER) && mob.tickCount % 10 == 0) {
            berserk(mob, level);
        }
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return;

        if (abilities.contains(Ability.STORMCALLER) && target instanceof Player && ready(mob, Ability.STORMCALLER)) {
            if (mob.distanceTo(target) <= 24 && mob.hasLineOfSight(target)) {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(target.getX(), target.getY(), target.getZ());
                    bolt.setDamage((float) power(Ability.STORMCALLER));
                    level.addFreshEntity(bolt);
                }
            }
        }
    }
    private static final String DRAGON_WAVES = "mobascension_dragon_waves";
    private static final double[] DRAGON_WAVE_AT = {0.75, 0.5, 0.25};
    private static void dragonTick(EnderDragon dragon, ServerLevel level) {
        double ratio = dragon.getHealth() / dragon.getMaxHealth();
        boolean enraged = ratio <= AscensionConfig.DRAGON_ENRAGE_AT.get();
        double radius = AscensionConfig.DRAGON_POWER_RADIUS.get();

        List<ServerPlayer> targets = new ArrayList<>();
        ServerPlayer nearest = null;
        double best = Double.MAX_VALUE;
        for (ServerPlayer p : level.players()) {
            if (!p.isAlive() || p.isCreative() || p.isSpectator()) continue;
            double d = dragon.distanceToSqr(p);
            if (d > radius * radius) continue;
            targets.add(p);
            if (d < best) {
                best = d;
                nearest = p;
            }
        }
        if (nearest == null) return;
        if (dragon.getTarget() != nearest) dragon.setTarget(nearest);
        int volleyCd = AscensionConfig.DRAGON_VOLLEY_COOLDOWN.get();
        if (enraged) volleyCd = Math.max(10, volleyCd / 2);
        if (dragon.tickCount % volleyCd == 0) {
            Vec3 dir = new Vec3(nearest.getX() - dragon.getX(), nearest.getY(0.5) - dragon.getY(0.5),
                    nearest.getZ() - dragon.getZ());
            double dist = dir.length();
            if (dist > 1.0) {
                Vec3 base = dir.normalize();
                double offset = Math.min(10.0, dist * 0.6);
                int skulls = AscensionConfig.DRAGON_VOLLEY_SKULLS.get();
                for (int i = 0; i < skulls; i++) {
                    Vec3 d = base.add(dragon.getRandom().nextGaussian() * 0.06,
                            dragon.getRandom().nextGaussian() * 0.06,
                            dragon.getRandom().nextGaussian() * 0.06).normalize();
                    WitherSkull skull = new WitherSkull(level, dragon, d);
                    skull.setPos(dragon.getX() + base.x * offset, dragon.getY(0.5) + base.y * offset,
                            dragon.getZ() + base.z * offset);
                    level.addFreshEntity(skull);
                }
                level.playSound(null, dragon.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 3.0F, 0.7F);
            }
        }
        int stormCd = AscensionConfig.DRAGON_STORM_COOLDOWN.get();
        if (stormCd > 0) {
            if (enraged) stormCd = Math.max(20, stormCd / 2);
            if (dragon.tickCount % stormCd == 0) {
                for (ServerPlayer p : targets) {
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                    if (bolt == null) continue;
                    bolt.moveTo(p.getX(), p.getY(), p.getZ());
                    bolt.setDamage((float) power(Ability.STORMCALLER));
                    level.addFreshEntity(bolt);
                }
            }
        }
        int count = AscensionConfig.DRAGON_REINFORCEMENTS.get();
        int waves = dragon.getPersistentData().getInt(DRAGON_WAVES);
        if (count > 0 && waves < DRAGON_WAVE_AT.length && ratio <= DRAGON_WAVE_AT[waves]) {
            dragon.getPersistentData().putInt(DRAGON_WAVES, waves + 1);
            for (int i = 0; i < count; i++) {
                EnderMan e = EntityType.ENDERMAN.create(level);
                if (e == null) continue;
                e.moveTo(nearest.getX() + (dragon.getRandom().nextDouble() - 0.5) * 10.0, nearest.getY(),
                        nearest.getZ() + (dragon.getRandom().nextDouble() - 0.5) * 10.0,
                        dragon.getRandom().nextFloat() * 360F, 0F);
                e.setTarget(nearest);
                level.addFreshEntity(e);
            }
            Component msg = Component.literal("The Ender Dragon calls its brood!").withStyle(Rank.APEX.format);
            for (ServerPlayer p : targets) p.sendSystemMessage(msg);
        }
    }

    private static void aggro(Mob mob, ServerLevel level, int radius) {
        LivingEntity current = mob.getTarget();
        if (current instanceof Player cp && cp.isAlive() && mob.distanceTo(cp) <= radius) return;
        Player best = null;
        double bestDist = (double) radius * radius;
        for (Player p : level.players()) {
            if (!p.isAlive() || p.isCreative() || p.isSpectator()) continue;
            double d = mob.distanceToSqr(p);
            if (d <= bestDist) {
                bestDist = d;
                best = p;
            }
        }
        if (best != null) mob.setTarget(best);
    }

    private static void shoot(Mob mob, ServerLevel level, Rank rank, LivingEntity target) {
        Vec3 direction = new Vec3(
                target.getX() - mob.getX(),
                target.getY(0.5) - mob.getY(0.5),
                target.getZ() - mob.getZ()).normalize();
        if (rank != Rank.APEX) return;
        AbstractHurtingProjectile projectile = new WitherSkull(level, mob, direction);
        projectile.setPos(mob.getX(), mob.getY(0.5) + 0.3, mob.getZ());
        level.addFreshEntity(projectile);
        level.playSound(null, mob.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    private static void berserk(Mob mob, ServerLevel level) {
        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance dmg = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        boolean enraged = mob.getHealth() / mob.getMaxHealth() <= power(Ability.BERSERKER);
        boolean active = speed != null && speed.hasModifier(BERSERK_SPEED);
        if (enraged && !active) {
            if (speed != null) speed.addTransientModifier(new AttributeModifier(BERSERK_SPEED, 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            if (dmg != null) dmg.addTransientModifier(new AttributeModifier(BERSERK_DAMAGE, 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, mob.getX(), mob.getEyeY(), mob.getZ(), 8, 0.4, 0.3, 0.4, 0.0);
            level.playSound(null, mob.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.0F, 1.2F);
        } else if (!enraged && active) {
            speed.removeModifier(BERSERK_SPEED);
            if (dmg != null) dmg.removeModifier(BERSERK_DAMAGE);
        }
    }

    public static void onAttack(Mob attacker, LivingEntity victim, float damage, EnumSet<Ability> abilities) {
        if (abilities.contains(Ability.VAMPIRIC)) {
            attacker.heal(damage * (float) power(Ability.VAMPIRIC));
        }
        if (abilities.contains(Ability.FROSTBITE)) {
            int ticks = (int) (power(Ability.FROSTBITE) * 20);
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 1, false, true, true));
            if (victim.canFreeze()) victim.setTicksFrozen(victim.getTicksRequiredToFreeze() + 20);
        }
        if (abilities.contains(Ability.VENOMOUS)) {
            victim.addEffect(new MobEffectInstance(MobEffects.POISON, (int) (power(Ability.VENOMOUS) * 20), 1, false, true, true));
        }
    }

    public static void onDefend(Mob champion, LivingEntity attacker, float damage, EnumSet<Ability> abilities) {
        if (abilities.contains(Ability.THORNED)) {
            attacker.hurt(champion.damageSources().thorns(champion), damage * (float) power(Ability.THORNED));
        }
    }

    public static void onDeath(Mob mob, ServerLevel level, EnumSet<Ability> abilities) {
        if (abilities.contains(Ability.SPLITTING) && !(mob instanceof Slime)) {
            int count = (int) Math.round(power(Ability.SPLITTING));
            for (int i = 0; i < count; i++) {
                Entity e = mob.getType().create(level);
                if (!(e instanceof Mob minion)) continue;
                minion.moveTo(mob.getX() + (mob.getRandom().nextDouble() - 0.5) * 1.5, mob.getY(),
                        mob.getZ() + (mob.getRandom().nextDouble() - 0.5) * 1.5,
                        mob.getRandom().nextFloat() * 360F, 0F);
                minion.getPersistentData().putBoolean(ChampionData.MINION, true);
                minion.finalizeSpawn(level, level.getCurrentDifficultyAt(minion.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
                level.addFreshEntity(minion);
            }
        }
    }
}
