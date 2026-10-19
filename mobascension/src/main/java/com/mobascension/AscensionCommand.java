package com.mobascension;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;

public final class AscensionCommand {
    private AscensionCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mobascension")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("summon")
                        .then(Commands.argument("rank", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(Rank.values()).map(r -> r.id), builder))
                                .executes(ctx -> summon(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "rank"),
                                        ResourceLocation.withDefaultNamespace("zombie")))
                                .then(Commands.argument("entity", ResourceLocationArgument.id())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                                BuiltInRegistries.ENTITY_TYPE.keySet(), builder))
                                        .executes(ctx -> summon(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "rank"),
                                                ResourceLocationArgument.getId(ctx, "entity")))))));
    }

    private static int summon(CommandSourceStack src, String rankId, ResourceLocation entityId) {
        Rank rank = Rank.byId(rankId);
        if (rank == null) {
            src.sendFailure(Component.literal("Unknown rank: " + rankId));
            return 0;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(entityId).orElse(null);
        ServerLevel level = src.getLevel();
        Entity entity = type == null ? null : type.create(level);
        if (!(entity instanceof Mob mob)) {
            src.sendFailure(Component.literal("That entity is not a mob."));
            return 0;
        }
        if (!ChampionFactory.isEligible(mob)) {
            src.sendFailure(Component.literal("That mob cannot become a champion (blacklisted or disabled)."));
            return 0;
        }
        Vec3 pos = src.getPosition();
        mob.moveTo(pos.x, pos.y, pos.z, src.getRotation().y, 0.0F);
        mob.getPersistentData().putString(ChampionData.RANK, rank.id);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.COMMAND, null);
        level.addFreshEntityWithPassengers(mob);
        src.sendSuccess(() -> Component.literal("Summoned a " + rank.defaultName + " champion."), true);
        return 1;
    }
}
