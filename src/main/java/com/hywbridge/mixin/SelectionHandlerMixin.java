package com.hywbridge.mixin;

import com.hywbridge.util.NPCOwnerHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ydmsama.hundred_years_war.client.freecam.Freecam;
import ydmsama.hundred_years_war.client.freecam.selection.SelectionHandler;
import ydmsama.hundred_years_war.client.utils.ClientCreativeModeSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mixin(value = SelectionHandler.class, remap = false)
public class SelectionHandlerMixin {

    @Shadow List<Entity> selectedEntities;
    @Shadow private boolean isSelecting;
    @Shadow private double startX;
    @Shadow private double startY;
    @Shadow private double endX;
    @Shadow private double endY;

    private final List<Entity> bridgeSelectedNPCs = new ArrayList<>();
    private long bridgeSelectionStartTime = 0;
    // true solo se in questa sessione abbiamo trovato/scelto dei CNPC
    private boolean bridgeUpdatedThisSession = false;

    @Inject(method = "resolveSelectedVehicleEntity", at = @At("HEAD"), cancellable = true)
    private void injectResolveVehicle(Entity entity, CallbackInfoReturnable<Entity> cir) {
        if (entity instanceof EntityNPCInterface) {
            cir.setReturnValue(entity);
        }
    }

    private boolean isYours(EntityNPCInterface npc, Minecraft mc) {
        if (mc.player == null) return false;
        UUID ownerUUID = NPCOwnerHelper.getOwnerUUIDClientSafe(npc);
        int relation = NPCOwnerHelper.getRelationToPlayer(npc.getUUID());
        boolean controlAll = mc.player.isCreative()
                && ClientCreativeModeSettings.canControlAllUnits();
        return controlAll
                || (ownerUUID != null && ownerUUID.equals(mc.player.getUUID()))
                || relation == 1
                || (mc.player.isCreative() && relation != 2);
    }

    @Inject(method = "hasControl", at = @At("HEAD"), cancellable = true)
    private void injectHasControlForNPC(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof EntityNPCInterface npc)) return;
        if (!NPCOwnerHelper.isHywManagedNPC(npc)) { cir.setReturnValue(false); return; }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        cir.setReturnValue(isYours(npc, mc));
    }

    @Inject(method = "isEntityInSelectionBox", at = @At("HEAD"), cancellable = true)
    private void injectIsEntityInSelectionBox(Entity entity, Minecraft mc, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof EntityNPCInterface npc)) return;
        if (!NPCOwnerHelper.isHywManagedNPC(npc)) { cir.setReturnValue(false); return; }
        if (mc.player == null) { cir.setReturnValue(false); return; }
        if (!isYours(npc, mc)) cir.setReturnValue(false);
    }

    @Inject(method = "startSelection", at = @At("HEAD"))
    private void injectStartSelectionTime(double x, double y, CallbackInfo ci) {
        bridgeSelectionStartTime = System.currentTimeMillis();
        bridgeUpdatedThisSession = false;
        bridgeSelectedNPCs.clear();
    }

    @Inject(method = "updateSelectedEntities", at = @At("TAIL"))
    private void injectCustomNPCSelection(CallbackInfo ci) {
        if (!this.isSelecting) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        double boxW = Math.abs(endX - startX);
        double boxH = Math.abs(endY - startY);
        if (boxW < 1.0 && boxH < 1.0) return;

        boolean isSingleClick = boxW < 5.0 && boxH < 5.0;
        if (isSingleClick) return; // gestito in endSelection

        bridgeSelectedNPCs.clear();
        bridgeUpdatedThisSession = true;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof EntityNPCInterface npc)) continue;
            if (!NPCOwnerHelper.isHywManagedNPC(npc)) continue;
            if (!isYours(npc, mc)) continue;
            try {
                java.lang.reflect.Method m = SelectionHandler.class
                        .getDeclaredMethod("isEntityInSelectionBox", Entity.class, Minecraft.class);
                m.setAccessible(true);
                if ((boolean) m.invoke(this, npc, mc)) {
                    bridgeSelectedNPCs.add(npc);
                    if (!selectedEntities.contains(npc)) selectedEntities.add(npc);
                }
            } catch (Exception e) {
                com.hywbridge.HYWBridge.LOGGER.error("Error in drag selection", e);
            }
        }
    }

    @Inject(method = "endSelection", at = @At("HEAD"))
    private void injectEndSelection(CallbackInfo ci) {
        long duration = System.currentTimeMillis() - bridgeSelectionStartTime;
        boolean wasSingleClick = duration < 200L;

        if (selectedEntities.isEmpty()) {
            if (wasSingleClick) {
                trySelectNPCUnderCursor();
            } else {
                bridgeSelectedNPCs.forEach(npc -> {
                    try { SelectionHandler.getInstance().getCombinedTargetMap().remove(npc); }
                    catch (Exception ignored) {}
                });
                bridgeSelectedNPCs.clear();
                bridgeUpdatedThisSession = false;
            }
            return;
        }

        // Aggiungi CNPC solo se erano stati selezionati in questa sessione
        if (bridgeUpdatedThisSession) {
            for (Entity e : bridgeSelectedNPCs) {
                if (!selectedEntities.contains(e)) selectedEntities.add(e);
            }
        } else if (wasSingleClick) {
            // Click su unità HYW — non aggiungere CNPC precedenti
            bridgeSelectedNPCs.clear();
        }
    }

    private void trySelectNPCUnderCursor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Vec3 direction;
        try {
            java.lang.reflect.Method stw = SelectionHandler.class
                    .getDeclaredMethod("screenToWorld", double.class, double.class, Minecraft.class);
            stw.setAccessible(true);
            direction = (Vec3) stw.invoke(SelectionHandler.getInstance(),
                    mc.mouseHandler.xpos(), mc.mouseHandler.ypos(), mc);
        } catch (Exception e) {
            return;
        }

        Vec3 eyePos;
        try {
            eyePos = Freecam.getFreeCamera().getEyePosition(1.0f);
        } catch (Exception e) {
            return;
        }

        Vec3 endPos = eyePos.add(direction.scale(200.0));

        Entity closest = null;
        double minDist = Double.MAX_VALUE;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof EntityNPCInterface npc)) continue;
            if (!NPCOwnerHelper.isHywManagedNPC(npc)) continue;
            if (!isYours(npc, mc)) continue;

            var hit = npc.getBoundingBox().inflate(0.3).clip(eyePos, endPos);
            if (hit.isPresent()) {
                double dist = eyePos.distanceTo(hit.get());
                if (dist < minDist) { minDist = dist; closest = npc; }
            }
        }

        if (closest != null) {
            bridgeSelectedNPCs.add(closest);
            bridgeUpdatedThisSession = true;
            selectedEntities.add(closest);
        }
    }
}