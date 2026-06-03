package com.hywbridge.mixin;

import com.hywbridge.util.NPCOwnerHelper;
import net.minecraft.world.entity.Entity;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ydmsama.hundred_years_war.main.utils.RelationOwnerMarkedEntity;
import ydmsama.hundred_years_war.main.utils.ServerRelationHelper;

import java.util.UUID;

@Mixin(value = ServerRelationHelper.class, remap = false)
public class ServerRelationHelperMixin {

    @Inject(method = "getRelationUUID", at = @At("HEAD"), cancellable = true)
    private static void injectGetRelationUUID(Entity entity,
                                              CallbackInfoReturnable<UUID> cir) {
        if (!(entity instanceof EntityNPCInterface npc)) return;
        UUID ownerUUID = NPCOwnerHelper.getOwnerUUID(npc);
        if (ownerUUID != null) {
            // Applica il marker HYW così viene riconosciuto nel sistema relazioni
            if (entity instanceof RelationOwnerMarkedEntity marked) {
                if (!marked.hyw$hasRelationIdentityMarker()) {
                    marked.hyw$markRelationOwnerUUID(ownerUUID);
                }
            }
            cir.setReturnValue(ownerUUID);
        }
    }

    @Inject(method = "canParticipateInRelation", at = @At("HEAD"), cancellable = true)
    private static void injectCanParticipate(Entity entity,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof EntityNPCInterface npc)) return;
        UUID ownerUUID = NPCOwnerHelper.getOwnerUUID(npc);
        if (ownerUUID != null) {
            if (entity instanceof RelationOwnerMarkedEntity marked) {
                if (!marked.hyw$hasRelationIdentityMarker()) {
                    marked.hyw$markRelationOwnerUUID(ownerUUID);
                }
            }
            cir.setReturnValue(true);
        }
    }
}