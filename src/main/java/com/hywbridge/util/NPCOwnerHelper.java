package com.hywbridge.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NPCOwnerHelper {

    public static final String OWNER_TAG = "hyw_owner";

    private static final Map<UUID, UUID> clientOwnerCache = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> clientRelationCache = new ConcurrentHashMap<>();

    public static void setOwner(EntityNPCInterface npc, Player player) {
        npc.getPersistentData().putUUID(OWNER_TAG, player.getUUID());
        clientOwnerCache.put(npc.getUUID(), player.getUUID());
    }

    public static UUID getOwnerUUID(EntityNPCInterface npc) {
        // Legge prima dal tag NBT — non dipende da advanced/scenes
        CompoundTag data = npc.getPersistentData();
        if (data.hasUUID(OWNER_TAG)) return data.getUUID(OWNER_TAG);
        if (data.contains(OWNER_TAG, 11)) {
            int[] arr = data.getIntArray(OWNER_TAG);
            if (arr.length == 4) {
                long most = ((long) arr[0] << 32) | (arr[1] & 0xFFFFFFFFL);
                long least = ((long) arr[2] << 32) | (arr[3] & 0xFFFFFFFFL);
                return new UUID(most, least);
            }
        }
        // Fallback: getOwner() nativo — solo se advanced è inizializzato
        try {
            if (npc.advanced == null || npc.advanced.scenes == null) return null;
            LivingEntity owner = npc.getOwner();
            if (owner instanceof Player p) return p.getUUID();
        } catch (Exception e) {
            // advanced non ancora inizializzato durante il caricamento
        }
        return null;
    }

    public static boolean isHywManagedNPCServer(EntityNPCInterface npc) {
        if (getOwnerUUID(npc) != null) return true;
        if (npc.faction != null && !npc.faction.name.isEmpty()) return true;
        return false;
    }

    public static boolean isHywManagedNPC(net.minecraft.world.entity.Entity entity) {
        if (!(entity instanceof EntityNPCInterface npc)) return false;
        if (clientOwnerCache.containsKey(npc.getUUID())) return true;
        if (clientRelationCache.containsKey(npc.getUUID())) return true;
        if (getOwnerUUID(npc) != null) return true;
        return false;
    }

    public static UUID getOwnerUUIDClientSafe(EntityNPCInterface npc) {
        UUID cached = clientOwnerCache.get(npc.getUUID());
        if (cached != null) return cached;
        return getOwnerUUID(npc);
    }

    public static int getRelationToPlayer(UUID npcUUID) {
        return clientRelationCache.getOrDefault(npcUUID, -1);
    }

    public static void cacheOwner(UUID npcUUID, UUID ownerUUID, int relation) {
        if (ownerUUID != null) clientOwnerCache.put(npcUUID, ownerUUID);
        clientRelationCache.put(npcUUID, relation);
    }

    public static void removeFromCache(UUID npcUUID) {
        clientOwnerCache.remove(npcUUID);
        clientRelationCache.remove(npcUUID);
    }

    public static Player getOwnerPlayer(EntityNPCInterface npc, ServerLevel level) {
        UUID ownerUUID = getOwnerUUID(npc);
        if (ownerUUID == null) return null;
        return level.getServer().getPlayerList().getPlayer(ownerUUID);
    }
}