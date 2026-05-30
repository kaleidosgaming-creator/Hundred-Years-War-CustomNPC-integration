package com.hywbridge.network;

import com.hywbridge.util.NPCOwnerHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class OwnerSyncPacket {

    private final UUID npcUUID;
    private final UUID ownerUUID;
    // 0 = neutral, 1 = friendly, 2 = hostile
    private final int relationToPlayer;

    public OwnerSyncPacket(UUID npcUUID, UUID ownerUUID, int relationToPlayer) {
        this.npcUUID = npcUUID;
        this.ownerUUID = ownerUUID;
        this.relationToPlayer = relationToPlayer;
    }

    public static OwnerSyncPacket decode(FriendlyByteBuf buf) {
        UUID npcUUID = buf.readUUID();
        boolean hasOwner = buf.readBoolean();
        UUID ownerUUID = hasOwner ? buf.readUUID() : null;
        int relation = buf.readInt();
        return new OwnerSyncPacket(npcUUID, ownerUUID, relation);
    }

    public static void encode(OwnerSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.npcUUID);
        buf.writeBoolean(packet.ownerUUID != null);
        if (packet.ownerUUID != null) buf.writeUUID(packet.ownerUUID);
        buf.writeInt(packet.relationToPlayer);
    }

    public static void handle(OwnerSyncPacket packet,
                              Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (packet.ownerUUID != null) {
                NPCOwnerHelper.cacheOwner(
                        packet.npcUUID, packet.ownerUUID, packet.relationToPlayer);
                com.hywbridge.HYWBridge.LOGGER.info(
                        "Cached owner {} relation={} for NPC {}",
                        packet.ownerUUID, packet.relationToPlayer, packet.npcUUID);
            } else {
                NPCOwnerHelper.removeFromCache(packet.npcUUID);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}