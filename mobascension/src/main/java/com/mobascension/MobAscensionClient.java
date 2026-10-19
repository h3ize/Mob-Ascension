package com.mobascension;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
@Mod(value = MobAscension.MODID, dist = Dist.CLIENT)
public class MobAscensionClient {
    public MobAscensionClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        NeoForge.EVENT_BUS.addListener(MobAscensionClient::onRenderNameTag);
    }
    private static void onRenderNameTag(RenderNameTagEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Mob mob) || !isChampion(mob)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!mc.player.hasLineOfSight(mob)) {
            event.setCanRender(TriState.FALSE);
        }
    }

    private static boolean isChampion(Mob mob) {
        if (mob.getTeam() != null && mob.getTeam().getName().startsWith("ma_")) return true;
        if (!mob.hasCustomName()) return false;
        String name = mob.getCustomName().getString();
        for (Rank r : Rank.values()) {
            if (name.startsWith(r.defaultName + " ")) return true;
        }
        return false;
    }
}
