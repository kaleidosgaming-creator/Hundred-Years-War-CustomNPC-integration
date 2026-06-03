package com.hywbridge.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.entity.EntityNPCInterface;
import ydmsama.hundred_years_war.main.utils.RelationOwnerMarkedEntity;
import ydmsama.hundred_years_war.main.utils.RelationSystem;
import ydmsama.hundred_years_war.main.utils.TeamRelationData;

import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "hywbridge", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FactionSyncHandler {

    private static int tickCounter = 0;
    private static final int SYNC_INTERVAL = 100;

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof EntityNPCInterface npc)) return;
        applyHywMarker(npc);
        ServerLevel level = (ServerLevel) event.getLevel();
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            syncSingleNPC(npc, player);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tickCounter++;
        if (tickCounter < SYNC_INTERVAL) return;
        tickCounter = 0;
        if (event.getServer() == null) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            syncNPCOwners(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncNPCOwners(player);
        }
    }

    /**
     * Applica il marker HYW al CNPC — così HYW lo riconosce nel sistema relazioni
     * senza bisogno di Mixin su metodi privati.
     */
    public static void applyHywMarker(EntityNPCInterface npc) {
        UUID ownerUUID = NPCOwnerHelper.getOwnerUUID(npc);
        if (ownerUUID == null) {
            if (npc.faction != null && !npc.faction.name.isEmpty()) {
                ownerUUID = UUID.nameUUIDFromBytes(
                        ("hywbridge_owner_" + npc.faction.name.toLowerCase()).getBytes());
            }
        }
        if (ownerUUID != null && npc instanceof RelationOwnerMarkedEntity marked) {
            marked.hyw$markRelationOwnerUUID(ownerUUID);
            com.hywbridge.HYWBridge.LOGGER.info(
                    "Applied HYW relation marker to NPC {} ownerUUID={}",
                    npc.getUUID(), ownerUUID);
        }
    }

    private static void syncSingleNPC(EntityNPCInterface npc, ServerPlayer player) {
        try {
            applyHywMarker(npc);

            UUID ownerUUID = NPCOwnerHelper.getOwnerUUID(npc);
            if (ownerUUID == null) {
                String factionName = (npc.faction != null && !npc.faction.name.isEmpty())
                        ? npc.faction.name : "neutral_npc";
                ownerUUID = UUID.nameUUIDFromBytes(
                        ("hywbridge_owner_" + factionName.toLowerCase()).getBytes());
            }

            int relation = computeRelationToPlayer(ownerUUID, player);

            com.hywbridge.HYWBridge.NETWORK.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                    new com.hywbridge.network.OwnerSyncPacket(npc.getUUID(), ownerUUID, relation)
            );
        } catch (Exception e) {
            com.hywbridge.HYWBridge.LOGGER.error("Error syncing NPC {}", npc.getUUID(), e);
        }
    }

    public static void syncNPCOwners(ServerPlayer player) {
        try {
            ServerLevel level = (ServerLevel) player.level();
            level.getAllEntities().forEach(entity -> {
                if (entity instanceof EntityNPCInterface npc) {
                    syncSingleNPC(npc, player);
                }
            });
        } catch (Exception e) {
            com.hywbridge.HYWBridge.LOGGER.error("Error syncing NPC owners", e);
        }
    }

    private static int computeRelationToPlayer(UUID npcOwnerUUID, ServerPlayer player) {
        UUID playerUUID = player.getUUID();
        if (npcOwnerUUID.equals(playerUUID)) return 2;

        RelationSystem.RelationType direct = RelationSystem.getRelation(npcOwnerUUID, playerUUID);
        if (direct == RelationSystem.RelationType.HOSTILE) return -1;
        if (direct == RelationSystem.RelationType.FRIENDLY
                || direct == RelationSystem.RelationType.CONTROL) return 1;

        try {
            Map<UUID, TeamRelationData> teams = RelationSystem.getAllTeams();
            if (teams != null) {
                for (Map.Entry<UUID, TeamRelationData> entry : teams.entrySet()) {
                    if (entry.getValue().isMember(playerUUID)) {
                        UUID playerTeamUUID = entry.getKey();
                        if (npcOwnerUUID.equals(playerTeamUUID)) return 2;
                        RelationSystem.RelationType r1 = RelationSystem.getRelation(npcOwnerUUID, playerTeamUUID);
                        RelationSystem.RelationType r2 = RelationSystem.getRelation(playerTeamUUID, npcOwnerUUID);
                        if (r1 == RelationSystem.RelationType.HOSTILE
                                || r2 == RelationSystem.RelationType.HOSTILE) return -1;
                        if (r1 == RelationSystem.RelationType.FRIENDLY
                                || r1 == RelationSystem.RelationType.CONTROL
                                || r2 == RelationSystem.RelationType.FRIENDLY
                                || r2 == RelationSystem.RelationType.CONTROL) return 1;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            com.hywbridge.HYWBridge.LOGGER.error("Error computing relation", e);
        }
        return 0;
    }

    public static int resolveRelation(EntityNPCInterface npc, ServerPlayer player) {
        UUID ownerUUID = NPCOwnerHelper.getOwnerUUID(npc);
        if (ownerUUID == null && npc.faction != null && !npc.faction.name.isEmpty()) {
            ownerUUID = UUID.nameUUIDFromBytes(
                    ("hywbridge_owner_" + npc.faction.name.toLowerCase()).getBytes());
        }
        return ownerUUID != null ? computeRelationToPlayer(ownerUUID, player) : 0;
    }
}