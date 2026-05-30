package com.hywbridge.mixin;

import com.hywbridge.util.BridgeSelectionStorage;
import com.hywbridge.util.NPCOwnerHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ydmsama.hundred_years_war.main.network.packets.TargetUpdatePacket;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraftforge.network.NetworkEvent;

@Mixin(value = TargetUpdatePacket.class, remap = false)
public class TargetUpdatePacketMixin {

    @Inject(method = "handle", at = @At("HEAD"))
    private static void injectAttackForCustomNPCs(
            TargetUpdatePacket packet,
            Supplier<NetworkEvent.Context> ctx,
            CallbackInfo ci) {

        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (BridgeSelectionStorage.isEmpty(player)) return;

            ServerLevel world = (ServerLevel) player.level();

            // Leggi il target UUID dal packet tramite reflection
            UUID targetUUID = null;
            try {
                java.lang.reflect.Field field = packet.getClass().getDeclaredField("targetUUID");
                field.setAccessible(true);
                targetUUID = (UUID) field.get(packet);
            } catch (Exception e) {
                try {
                    java.lang.reflect.Field[] fields = packet.getClass().getDeclaredFields();
                    for (java.lang.reflect.Field f : fields) {
                        if (f.getType() == UUID.class) {
                            f.setAccessible(true);
                            targetUUID = (UUID) f.get(packet);
                            break;
                        }
                    }
                } catch (Exception ex) {
                    com.hywbridge.HYWBridge.LOGGER.error("Could not read targetUUID from TargetUpdatePacket", ex);
                    return;
                }
            }

            if (targetUUID == null) return;

            Entity target = world.getEntity(targetUUID);
            if (!(target instanceof LivingEntity livingTarget)) return;

            for (Entity entity : BridgeSelectionStorage.getCustomEntities(player)) {
                if (entity instanceof EntityNPCInterface npc
                        && NPCOwnerHelper.isHywManagedNPC(npc)) {
                    npc.setTarget(livingTarget);
                    com.hywbridge.HYWBridge.LOGGER.debug(
                            "Setting attack target {} for NPC {}",
                            livingTarget.getUUID(), npc.getUUID());
                }
            }
        });
    }
}