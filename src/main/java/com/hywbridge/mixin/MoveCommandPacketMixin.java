package com.hywbridge.mixin;

import com.hywbridge.util.BridgeSelectionStorage;
import com.hywbridge.util.NPCOwnerHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ydmsama.hundred_years_war.main.network.packets.MoveCommandPacket;

import java.util.function.Supplier;
import net.minecraftforge.network.NetworkEvent;

@Mixin(value = MoveCommandPacket.class, remap = false)
public class MoveCommandPacketMixin {

    @Inject(method = "handle", at = @At("HEAD"))
    private static void injectMoveForCustomNPCs(
            MoveCommandPacket packet,
            Supplier<NetworkEvent.Context> ctx,
            CallbackInfo ci) {

        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (BridgeSelectionStorage.isEmpty(player)) return;

            Vec3 targetVec = null;
            try {
                java.lang.reflect.Field hitResultField =
                        packet.getClass().getDeclaredField("hitResult");
                hitResultField.setAccessible(true);
                HitResult hitResult = (HitResult) hitResultField.get(packet);

                if (hitResult != null) {
                    targetVec = hitResult.getLocation();
                    com.hywbridge.HYWBridge.LOGGER.info(
                            "Got targetVec from hitResult: {}", targetVec);
                }
            } catch (Exception e) {
                com.hywbridge.HYWBridge.LOGGER.error(
                        "Error reading hitResult from MoveCommandPacket", e);
                return;
            }

            if (targetVec == null) {
                com.hywbridge.HYWBridge.LOGGER.info("targetVec is null!");
                return;
            }

            final Vec3 finalTarget = targetVec;

            for (Entity entity : BridgeSelectionStorage.getCustomEntities(player)) {
                if (entity instanceof EntityNPCInterface npc
                        && NPCOwnerHelper.isHywManagedNPC(npc)) {
                    npc.getNavigation().moveTo(
                            finalTarget.x, finalTarget.y, finalTarget.z, 1.0);
                    com.hywbridge.HYWBridge.LOGGER.info(
                            "Moving NPC {} to {}", npc.getUUID(), finalTarget);
                }
            }
        });
    }
}