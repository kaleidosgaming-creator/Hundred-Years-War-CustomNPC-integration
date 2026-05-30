package com.hywbridge.mixin;

import com.hywbridge.util.BridgeSelectionStorage;
import com.hywbridge.util.NPCOwnerHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ydmsama.hundred_years_war.main.network.packets.SelectionPacket;
import ydmsama.hundred_years_war.main.selection.SelectionSystem;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraftforge.network.NetworkEvent;

@Mixin(value = SelectionPacket.class, remap = false)
public class SelectionSystemMixin {

    // Inietta in SelectionPacket.handle — viene chiamato SEMPRE,
    // sia con lista piena che con lista vuota (deselect)
    @Inject(method = "handle", at = @At("HEAD"))
    private static void injectHandleHead(
            SelectionPacket packet,
            Supplier<NetworkEvent.Context> ctx,
            CallbackInfo ci) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            // Svuota sempre il bridge — se lista vuota è deselect
            BridgeSelectionStorage.clear(player);
        });
    }

    @Inject(method = "handle", at = @At("TAIL"))
    private static void injectHandleTail(
            SelectionPacket packet,
            Supplier<NetworkEvent.Context> ctx,
            CallbackInfo ci) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            List<UUID> ids;
            try {
                java.lang.reflect.Field f = SelectionPacket.class.getDeclaredField("selectedEntityIds");
                f.setAccessible(true);
                ids = (List<UUID>) f.get(packet);
            } catch (Exception e) {
                com.hywbridge.HYWBridge.LOGGER.error("Could not read selectedEntityIds", e);
                return;
            }

            if (ids == null || ids.isEmpty()) return;

            ServerLevel level = (ServerLevel) player.level();
            for (UUID uuid : ids) {
                Entity entity = level.getEntity(uuid);
                if (entity instanceof EntityNPCInterface npc
                        && NPCOwnerHelper.isHywManagedNPCServer(npc)) {
                    BridgeSelectionStorage.addEntity(player, npc);
                }
            }
        });
    }
}