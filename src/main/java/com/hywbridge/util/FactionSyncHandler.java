package com.hywbridge.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.controllers.FactionController;
import noppes.npcs.controllers.data.Faction;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.entity.EntityNPCInterface;
import ydmsama.hundred_years_war.main.utils.RelationSystem;
import ydmsama.hundred_years_war.main.utils.TeamRelationData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "hywbridge", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FactionSyncHandler {

    private static final Map<String, Integer> factionMapping = new HashMap<>();
    private static int tickCounter = 0;
    private static final int SYNC_INTERVAL = 100;

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof EntityNPCInterface npc)) return;
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
            syncPlayerFactions(player);
            syncNPCOwners(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncPlayerFactions(player);
            syncNPCOwners(player);
        }
    }

    private static void syncSingleNPC(EntityNPCInterface npc, ServerPlayer player) {
        try {
            UUID ownerUUID = NPCOwnerHelper.getOwnerUUID(npc);
            int relation = resolveRelation(npc, player);

            if (ownerUUID == null) {
                String factionName = (npc.faction != null
                        && !npc.faction.name.isEmpty())
                        ? npc.faction.name
                        : "neutral_npc";
                ownerUUID = UUID.nameUUIDFromBytes(factionName.getBytes());
            }

            com.hywbridge.HYWBridge.NETWORK.send(
                    net.minecraftforge.network.PacketDistributor
                            .PLAYER.with(() -> player),
                    new com.hywbridge.network.OwnerSyncPacket(
                            npc.getUUID(), ownerUUID, relation)
            );
        } catch (Exception e) {
            com.hywbridge.HYWBridge.LOGGER.error(
                    "Error syncing single NPC {}", npc.getUUID(), e);
        }
    }

    private static void syncPlayerFactions(ServerPlayer player) {
        try {
            String hywTeam = getPlayerTeam(player);
            if (hywTeam == null) return;

            FactionController fc = FactionController.instance;
            if (fc == null) return;

            for (Map.Entry<String, Integer> entry : factionMapping.entrySet()) {
                String teamName = entry.getKey();
                int factionId = entry.getValue();

                Faction faction = fc.factions.get(factionId);
                if (faction == null) continue;

                PlayerData pd = PlayerData.get(player);
                if (pd == null) continue;

                if (hywTeam.equals(teamName)) {
                    pd.factionData.factionData.put(factionId, 2000);
                } else {
                    pd.factionData.factionData.put(factionId, -1000);
                }
            }
        } catch (Exception e) {
            com.hywbridge.HYWBridge.LOGGER.error(
                    "Error syncing factions for player {}",
                    player.getName().getString(), e);
        }
    }

    public static void syncNPCOwners(ServerPlayer player) {
        try {
            ServerLevel level = (ServerLevel) player.level();
            level.getAllEntities().forEach(entity -> {
                if (!(entity instanceof EntityNPCInterface npc)) return;
                syncSingleNPC(npc, player);
            });
        } catch (Exception e) {
            com.hywbridge.HYWBridge.LOGGER.error(
                    "Error syncing NPC owners", e);
        }
    }

    public static int resolveRelation(EntityNPCInterface npc, ServerPlayer player) {
        try {
            if (npc.faction != null && !npc.faction.name.isEmpty()) {
                String factionName = npc.faction.name;

                // Usa getAllTeams() - metodo pubblico in HYW 0.6.1
                Map<UUID, TeamRelationData> teamMap =
                        RelationSystem.getAllTeams();

                if (teamMap != null) {
                    for (Map.Entry<UUID, TeamRelationData> entry
                            : teamMap.entrySet()) {
                        if (entry.getValue().getTeamName()
                                .equalsIgnoreCase(factionName)) {

                            RelationSystem.RelationType rel =
                                    RelationSystem.getRelation(
                                            player.getUUID(), entry.getKey());

                            if (rel == RelationSystem.RelationType.HOSTILE)
                                return 2;
                            if (rel == RelationSystem.RelationType.CONTROL
                                    || rel == RelationSystem.RelationType.FRIENDLY)
                                return 1;
                            return 0;
                        }
                    }
                }
            }
        } catch (Exception e) {
            com.hywbridge.HYWBridge.LOGGER.error(
                    "Error resolving relation for NPC {}", npc.getUUID(), e);
        }
        return 0;
    }

    private static String getPlayerTeam(ServerPlayer player) {
        var team = player.getTeam();
        if (team != null) return team.getName();
        try {
            Class<?> rs = Class.forName(
                    "ydmsama.hundred_years_war.main.utils.RelationSystem");
            java.lang.reflect.Method m = rs.getMethod(
                    "getPlayerTeam", UUID.class);
            Object result = m.invoke(null, player.getUUID());
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static void registerFactionMapping(String hywTeamName, int cnpcFactionId) {
        factionMapping.put(hywTeamName, cnpcFactionId);
        com.hywbridge.HYWBridge.LOGGER.info(
                "Registered faction mapping: {} -> {}",
                hywTeamName, cnpcFactionId);
    }
}