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
import ydmsama.hundred_years_war.main.utils.RelationSystem;
import ydmsama.hundred_years_war.main.utils.TeamRelationData;
import com.hywbridge.util.BridgeSelectionStorage;
import com.hywbridge.util.FactionSyncHandler;
import com.hywbridge.util.NPCOwnerHelper;
import noppes.npcs.entity.EntityNPCInterface;
import ydmsama.hundred_years_war.main.entity.entities.BaseCombatEntity;
import ydmsama.hundred_years_war.main.selection.SelectionSystem;

import java.util.UUID;

public class HYWBridgeCommands {

    public static UUID factionOwnerUUID(String factionName) {
        return UUID.nameUUIDFromBytes(("hywbridge_owner_" + factionName.toLowerCase()).getBytes());
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
        UUID ownerUUID = factionOwnerUUID(name);

        if (RelationSystem.getTeamRelationData(teamUUID) != null) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Faction '" + name + "' already exists."), false);
            return 1;
        }

        try {
            TeamRelationData team = new TeamRelationData(teamUUID, name);
            team.addMember(ownerUUID, TeamRelationData.MemberType.OWNER);

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

            RelationSystem.setRelation(ownerUUID, teamUUID,
                    RelationSystem.RelationType.FRIENDLY);

            // Salva immediatamente su disco
            saveNow(ctx.getSource());

            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Created faction '" + name + "'. Use /hywbridge faction hostile/friendly <f1> <f2> to set relations."), false);
            com.hywbridge.HYWBridge.LOGGER.info(
                    "Created faction '{}' teamUUID={}", name, teamUUID);
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Failed: " + e.getMessage()));
            com.hywbridge.HYWBridge.LOGGER.error("Failed to create faction", e);
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
                    "Faction '" + f1 + "' not found. Use /hywbridge faction create " + f1));
            return 0;
        }
        if (uuid2 == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "Faction '" + f2 + "' not found. Use /hywbridge faction create " + f2));
            return 0;
        }

        RelationSystem.setRelation(uuid1, uuid2, type);
        RelationSystem.setRelation(uuid2, uuid1, type);

        // Salva immediatamente su disco
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
                        "  - " + team.getTeamName()), false));
        return 1;
    }

    private static int assignSelected(CommandContext<CommandSourceStack> ctx) {
        String factionName = StringArgumentType.getString(ctx, "faction");

        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("Must be a player"));
            return 0;
        }

        UUID teamUUID = factionTeamUUID(factionName);
        if (RelationSystem.getTeamRelationData(teamUUID) == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "Faction '" + factionName + "' not found. Create it with /hywbridge faction create " + factionName));
            return 0;
        }

        UUID fakeOwnerUUID = factionOwnerUUID(factionName);
        int count = 0;

        // Assegna CustomNPC nel bridge storage
        for (Entity entity : BridgeSelectionStorage.getCustomEntities(player)) {
            if (entity instanceof EntityNPCInterface npc) {
                npc.getPersistentData().putUUID(NPCOwnerHelper.OWNER_TAG, fakeOwnerUUID);
                count++;
            }
        }

        // Assegna unità HYW native nella selezione
        try {
            var selection = SelectionSystem.getSelection(player);
            if (selection != null) {
                for (BaseCombatEntity unit : selection.getEntities()) {
                    unit.m_30586_(fakeOwnerUUID);
                    count++;
                }
            }
        } catch (Exception e) {
            com.hywbridge.HYWBridge.LOGGER.error("Error assigning HYW units", e);
        }

        // Sincronizza immediatamente al client
        FactionSyncHandler.syncNPCOwners(player);

        final int finalCount = count;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Assigned " + finalCount + " units to faction '" + factionName + "'"), false);
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