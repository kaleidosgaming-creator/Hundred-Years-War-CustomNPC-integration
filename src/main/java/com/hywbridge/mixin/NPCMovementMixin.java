package com.hywbridge.mixin;

import com.hywbridge.util.NPCOwnerHelper;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityNPCInterface.class, remap = false)
public class NPCMovementMixin {

    @Inject(method = "m_8119_", at = @At("HEAD"))
    private void injectMovementOverride(CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface)(Object)this;
        if (!NPCOwnerHelper.isHywManagedNPCServer(npc)) return;
        if (npc.isClientSide()) return;
        if (npc.ais.returnToStart) {
            npc.ais.returnToStart = false;
        }
        if (npc.ais.getMovingType() != 0 && npc.getNavigation().isDone()) {
            npc.ais.setMovingType(0);
        }
    }

    // Impedisce returnToStart dopo il respawn
    @Inject(method = "reset", at = @At("HEAD"))
    private void injectResetOverride(CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface)(Object)this;
        if (!NPCOwnerHelper.isHywManagedNPCServer(npc)) return;
        npc.ais.returnToStart = false;
    }
}