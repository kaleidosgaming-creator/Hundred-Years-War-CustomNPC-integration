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
        // syncSingleNPC chiama già applyHywMarker internamente
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

    /**
     * Calcola la relazione tra il CNPC (ownerUUID = teamUUID bridge) e il player.
     *
     * Valori:
     *   1 = friendly/control (stessa fazione o alleata) → verde
     *   0 = neutral          → giallo
     *   2 = hostile          → rosso
     *
     * MAI restituisce -1 — quello è solo il default della cache client
     * quando nessun packet è ancora arrivato.
     */
    private static int computeRelationToPlayer(UUID npcOwnerUUID, ServerPlayer player) {
        UUID playerUUID = player.getUUID();

        // L'NPC appartiene direttamente al player → friendly
        if (npcOwnerUUID.equals(playerUUID)) return 1;

        try {
            Map<UUID, TeamRelationData> teams = RelationSystem.getAllTeams();
            if (teams == null || teams.isEmpty()) return 0;

            // Trova il team del player (se ce l'ha)
            UUID playerTeamUUID = null;
            for (Map.Entry<UUID, TeamRelationData> entry : teams.entrySet()) {
                if (entry.getValue().isMember(playerUUID)) {
                    playerTeamUUID = entry.getKey();
                    break;
                }
            }

            // npcOwnerUUID IS il teamUUID nel sistema bridge
            UUID npcTeamUUID = npcOwnerUUID;

            // Player e NPC stesso team → friendly
            if (playerTeamUUID != null && playerTeamUUID.equals(npcTeamUUID)) return 1;

            // Controlla relazione team player ↔ team NPC
            if (playerTeamUUID != null) {
                RelationSystem.RelationType r = RelationSystem.getRelation(
                        playerTeamUUID, npcTeamUUID);
                if (r == RelationSystem.RelationType.HOSTILE) return 2;
                if (r == RelationSystem.RelationType.FRIENDLY
                        || r == RelationSystem.RelationType.CONTROL) return 1;

                // Controlla anche direzione opposta
                RelationSystem.RelationType r2 = RelationSystem.getRelation(
                        npcTeamUUID, playerTeamUUID);
                if (r2 == RelationSystem.RelationType.HOSTILE) return 2;
                if (r2 == RelationSystem.RelationType.FRIENDLY
                        || r2 == RelationSystem.RelationType.CONTROL) return 1;
            } else {
                // Player senza team: controlla relazione diretta player ↔ npcTeam
                RelationSystem.RelationType r = RelationSystem.getRelation(
                        playerUUID, npcTeamUUID);
                if (r == RelationSystem.RelationType.HOSTILE) return 2;
                if (r == RelationSystem.RelationType.FRIENDLY
                        || r == RelationSystem.RelationType.CONTROL) return 1;

                RelationSystem.RelationType r2 = RelationSystem.getRelation(
                        npcTeamUUID, playerUUID);
                if (r2 == RelationSystem.RelationType.HOSTILE) return 2;
                if (r2 == RelationSystem.RelationType.FRIENDLY
                        || r2 == RelationSystem.RelationType.CONTROL) return 1;
            }
        } catch (Exception e) {
            com.hywbridge.HYWBridge.LOGGER.error(
                    "Error computing relation for ownerUUID={}", npcOwnerUUID, e);
        }

        return 0; // neutral — mai -1
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