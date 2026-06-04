package com.hywbridge.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelResource;
import ydmsama.hundred_years_war.main.entity.entities.BaseCombatEntity;
import ydmsama.hundred_years_war.main.selection.SelectionSystem;
import ydmsama.hundred_years_war.main.utils.RelationOwnerMarkedEntity;
import ydmsama.hundred_years_war.main.utils.RelationSystem;
import ydmsama.hundred_years_war.main.utils.TeamRelationData;
import com.hywbridge.util.BridgeSelectionStorage;
import com.hywbridge.util.FactionSyncHandler;
import com.hywbridge.util.NPCOwnerHelper;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.UUID;

public class HYWBridgeCommands {

    public static UUID factionOwnerUUID(String factionName) {
        return factionTeamUUID(factionName);
    }

    public static UUID factionTeamUUID(String factionName) {
        return UUID.nameUUIDFromBytes(("hywbridge_team_" + factionName.toLowerCase()).getBytes());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("hywbridge")
                .requires(src -> src.hasPermission(2))

                .then(Commands.literal("faction")
                        .then(Commands.literal("create")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> createFaction(ctx))))

                        .then(Commands.literal("delete")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> deleteFaction(ctx))))

                        .then(Commands.literal("hostile")
                                .then(Commands.argument("faction1", StringArgumentType.word())
                                        .then(Commands.argument("faction2", StringArgumentType.word())
                                                .executes(ctx -> setRelation(ctx, RelationSystem.RelationType.HOSTILE)))))

                        .then(Commands.literal("friendly")
                                .then(Commands.argument("faction1", StringArgumentType.word())
                                        .then(Commands.argument("faction2", StringArgumentType.word())
                                                .executes(ctx -> setRelation(ctx, RelationSystem.RelationType.FRIENDLY)))))

                        .then(Commands.literal("neutral")
                                .then(Commands.argument("faction1", StringArgumentType.word())
                                        .then(Commands.argument("faction2", StringArgumentType.word())
                                                .executes(ctx -> setRelation(ctx, RelationSystem.RelationType.NEUTRAL)))))

                        .then(Commands.literal("list")
                                .executes(ctx -> listFactions(ctx))))

                .then(Commands.literal("assign")
                        .then(Commands.argument("faction", StringArgumentType.word())
                                .executes(ctx -> assignSelected(ctx))))
        );
    }

    private static int createFaction(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        UUID teamUUID = factionTeamUUID(name);

        if (RelationSystem.getTeamRelationData(teamUUID) != null) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Faction '" + name + "' already exists."), false);
            return 1;
        }

        try {
            TeamRelationData team = new TeamRelationData(teamUUID, name);
            team.addMember(teamUUID, TeamRelationData.MemberType.OWNER);

            java.lang.reflect.Field teamDataMap = RelationSystem.class
                    .getDeclaredField("TeamDataMap");
            teamDataMap.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<UUID, TeamRelationData> tdm =
                    (java.util.Map<UUID, TeamRelationData>) teamDataMap.get(null);
            tdm.put(teamUUID, team);

            java.lang.reflect.Field relationDataMap = RelationSystem.class
                    .getDeclaredField("RelationDataMap");
            relationDataMap.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<UUID, Object> rdm =
                    (java.util.Map<UUID, Object>) relationDataMap.get(null);
            rdm.put(teamUUID, team);

            RelationSystem.setRelation(teamUUID, teamUUID,
                    RelationSystem.RelationType.CONTROL);

            saveNow(ctx.getSource());

            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Created faction '" + name + "'."), false);
            com.hywbridge.HYWBridge.LOGGER.info(
                    "Created faction '{}' teamUUID={}", name, teamUUID);
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Failed: " + e.getMessage()));
            com.hywbridge.HYWBridge.LOGGER.error("Failed to create faction", e);
        }
        return 1;
    }

    private static int deleteFaction(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");

        UUID teamUUID = findTeamUUID(name);
        if (teamUUID == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "Faction '" + name + "' not found."));
            return 0;
        }

        try {
            // Rimuovi dai map interni di HYW
            java.lang.reflect.Field teamDataMap = RelationSystem.class
                    .getDeclaredField("TeamDataMap");
            teamDataMap.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<UUID, ?> tdm =
                    (java.util.Map<UUID, ?>) teamDataMap.get(null);
            tdm.remove(teamUUID);

            java.lang.reflect.Field relationDataMap = RelationSystem.class
                    .getDeclaredField("RelationDataMap");
            relationDataMap.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<UUID, ?> rdm =
                    (java.util.Map<UUID, ?>) relationDataMap.get(null);
            rdm.remove(teamUUID);

            // Pulisci il marker HYW da tutti i CNPC e unità che avevano questa fazione
            // — senza riavvio
            final UUID deletedUUID = teamUUID;
            ctx.getSource().getServer().getAllLevels().forEach(level -> {
                level.getAllEntities().forEach(entity -> {
                    // Pulisci marker HYW
                    if (entity instanceof RelationOwnerMarkedEntity marked) {
                        if (deletedUUID.equals(marked.hyw$getMarkedRelationOwnerUUID())) {
                            marked.hyw$clearRelationIdentityMarker();
                        }
                    }
                    // Pulisci NBT tag dai CNPC
                    if (entity instanceof EntityNPCInterface npc) {
                        UUID npcOwner = NPCOwnerHelper.getOwnerUUID(npc);
                        if (deletedUUID.equals(npcOwner)) {
                            npc.getPersistentData().remove(NPCOwnerHelper.OWNER_TAG);
                        }
                    }
                    // Resetta owner delle unità HYW
                    if (entity instanceof BaseCombatEntity unit) {
                        try {
                            UUID unitOwner = unit.m_21805_();
                            if (deletedUUID.equals(unitOwner)) {
                                unit.m_30586_(null);
                            }
                        } catch (Exception ignored) {}
                    }
                });
            });

            // Sincronizza i client
            for (ServerPlayer player : ctx.getSource().getServer()
                    .getPlayerList().getPlayers()) {
                FactionSyncHandler.syncNPCOwners(player);
            }

            saveNow(ctx.getSource());

            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Deleted faction '" + name + "'. All units unassigned."), false);
            com.hywbridge.HYWBridge.LOGGER.info("Deleted faction '{}'", name);
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Failed: " + e.getMessage()));
            com.hywbridge.HYWBridge.LOGGER.error("Failed to delete faction", e);
        }
        return 1;
    }

    private static int setRelation(CommandContext<CommandSourceStack> ctx,
                                   RelationSystem.RelationType type) {
        String f1 = StringArgumentType.getString(ctx, "faction1");
        String f2 = StringArgumentType.getString(ctx, "faction2");

        UUID uuid1 = findTeamUUID(f1);
        UUID uuid2 = findTeamUUID(f2);

        if (uuid1 == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "Faction '" + f1 + "' not found."));
            return 0;
        }
        if (uuid2 == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "Faction '" + f2 + "' not found."));
            return 0;
        }

        RelationSystem.setRelation(uuid1, uuid2, type);
        RelationSystem.setRelation(uuid2, uuid1, type);

        saveNow(ctx.getSource());

        ctx.getSource().sendSuccess(() -> Component.literal(
                f1 + " ↔ " + f2 + " = " + type.name().toLowerCase()), false);
        return 1;
    }

    private static int listFactions(CommandContext<CommandSourceStack> ctx) {
        var teams = RelationSystem.getAllTeams();
        if (teams.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No factions."), false);
            return 1;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Factions:"), false);
        teams.forEach((uuid, team) ->
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "  - " + team.getTeamName() + " [" + uuid + "]"), false));
        return 1;
    }

    private static int assignSelected(CommandContext<CommandSourceStack> ctx) {
        String factionName = StringArgumentType.getString(ctx, "faction");

        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("Must be a player"));
            return 0;
        }

        // Cerca prima nei team HYW nativi per nome, poi fallback al UUID bridge
        UUID teamUUID = findTeamUUID(factionName);
        if (teamUUID == null) {
            teamUUID = factionTeamUUID(factionName);
        }
        if (RelationSystem.getTeamRelationData(teamUUID) == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "Faction '" + factionName + "' not found. Create it first with /hywbridge faction create " + factionName));
            return 0;
        }

        UUID markerUUID = teamUUID;
        int cnpcCount = 0;
        int hywCount = 0;

        // Assegna CNPC selezionati
        for (Entity entity : BridgeSelectionStorage.getCustomEntities(player)) {
            if (entity instanceof EntityNPCInterface npc) {
                npc.getPersistentData().putUUID(NPCOwnerHelper.OWNER_TAG, markerUUID);
                if (npc instanceof RelationOwnerMarkedEntity marked) {
                    marked.hyw$markRelationOwnerUUID(markerUUID);
                }
                cnpcCount++;
            }
        }

        // Assegna unità HYW selezionate
        var selection = SelectionSystem.getSelection(player);
        com.hywbridge.HYWBridge.LOGGER.info(
                "HYW selection has {} entities for assign", selection.getEntities().size());
        for (BaseCombatEntity unit : selection.getEntities()) {
            unit.m_30586_(markerUUID);
            if (unit instanceof RelationOwnerMarkedEntity marked) {
                marked.hyw$markRelationOwnerUUID(markerUUID);
            }
            hywCount++;
        }

        if (cnpcCount == 0 && hywCount == 0) {
            ctx.getSource().sendFailure(Component.literal(
                    "No units selected. Select units with RTS first, then use this command."));
            return 0;
        }

        FactionSyncHandler.syncNPCOwners(player);

        final int fc = cnpcCount, fh = hywCount;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Assigned " + (fc + fh) + " units to '" + factionName +
                        "' (" + fh + " HYW, " + fc + " CNPC)"), false);
        return 1;
    }

    private static void saveNow(CommandSourceStack source) {
        try {
            java.nio.file.Path savePath = source.getServer()
                    .getWorldPath(LevelResource.PLAYER_DATA_DIR)
                    .getParent()
                    .resolve("relations.dat");
            RelationSystem.saveRelations(savePath);
            com.hywbridge.HYWBridge.LOGGER.info("Saved relations to {}", savePath);
        } catch (Exception e) {
            com.hywbridge.HYWBridge.LOGGER.error("Failed to save relations", e);
        }
    }

    public static UUID findTeamUUID(String factionName) {
        for (var entry : RelationSystem.getAllTeams().entrySet()) {
            if (entry.getValue().getTeamName().equalsIgnoreCase(factionName)) {
                return entry.getKey();
            }
        }
        return null;
    }
}