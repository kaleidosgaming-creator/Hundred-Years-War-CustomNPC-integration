package com.hywbridge.mixin;

import com.hywbridge.util.NPCOwnerHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ydmsama.hundred_years_war.client.freecam.ui.SelectionHUD;
import ydmsama.hundred_years_war.main.entity.entities.BaseCombatEntity;
import ydmsama.hundred_years_war.main.utils.FormationTypes;

import java.util.*;

@Mixin(value = SelectionHUD.class, remap = false)
public class SelectionHUDMixin {

    @Inject(method = "renderSingleEntityInfo", at = @At("TAIL"))
    private static void injectSingleCustomNPC(GuiGraphics guiGraphics, Entity entity, CallbackInfo ci) {
        if (!(entity instanceof EntityNPCInterface npc)) return;
        if (!NPCOwnerHelper.isHywManagedNPC(npc)) return;

        int y = 5;
        Component nameText = Component.literal("NPC: ").append(npc.getName());
        guiGraphics.drawString(Minecraft.getInstance().font, nameText, 5, y, 0xFFFFFF, false);
        y += 10;

        Component healthText = Component.literal(
                String.format("%.1f/%.1f HP", npc.getHealth(), npc.getMaxHealth()));
        guiGraphics.drawString(Minecraft.getInstance().font, healthText, 5, y, 0xFFFFFF, false);
        y += 10;

        if (npc.faction != null && !npc.faction.name.isEmpty()) {
            Component factionText = Component.literal("Faction: " + npc.faction.name);
            guiGraphics.drawString(Minecraft.getInstance().font, factionText, 5, y, 0xAAAAAA, false);
        }
    }

    @Inject(method = "renderMultipleEntitiesInfo", at = @At("TAIL"))
    private static void injectMultipleCustomNPC(GuiGraphics guiGraphics, List<Entity> entities, CallbackInfo ci) {
        int customNPCCount = 0;
        for (Entity e : entities) {
            if (e instanceof EntityNPCInterface && NPCOwnerHelper.isHywManagedNPC(e)) {
                customNPCCount++;
            }
        }
        if (customNPCCount == 0) return;

        // Calcola y dopo le righe già disegnate da HYW
        Set<String> entityTypes = new LinkedHashSet<>();
        Set<UUID> formationGroups = new LinkedHashSet<>();
        for (Entity e : entities) {
            if (e instanceof BaseCombatEntity bce) {
                entityTypes.add(e.getType().getDescriptionId());
                UUID fgId = bce.getFormationGroupId();
                if (fgId != null && FormationTypes.isActive(bce.getFormationType())) {
                    formationGroups.add(fgId);
                }
            }
        }

        int y = 5 + 10; // "Selected units: N"
        y += formationGroups.size() * 10;
        y += entityTypes.size() * 10;

        Component npcText = Component.literal("Custom NPC: " + customNPCCount);
        guiGraphics.drawString(Minecraft.getInstance().font, npcText, 5, y, 0xFFDD44, false);
    }
}