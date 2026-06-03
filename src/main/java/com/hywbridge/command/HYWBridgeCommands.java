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

    // L'owner di una fazione bridge = il teamUUID stesso
    // così HYW riconosce l'entità come membro del team
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
            // teamUUID è sia il team che l'"owner" — un solo UUID per tutto
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

            // Il teamUUID è friendly con se stesso
            RelationSystem.setRelation(teamUUID, teamUUID,
                    RelationSystem.RelationType.CONTROL);

            saveNow(ctx.getSource());

            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Created faction '" + name + "'. UUID=" + teamUUID), false);
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

        UUID teamUUID = factionTeamUUID(factionName);
        if (RelationSystem.getTeamRelationData(teamUUID) == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "Faction '" + factionName + "' not found. Create it first with /hywbridge faction create " + factionName));
            return 0;
        }

        // teamUUID = ownerUUID (stesso UUID per semplicità)
        UUID markerUUID = teamUUID;
        int count = 0;

        // Assegna CNPC selezionati
        for (Entity entity : BridgeSelectionStorage.getCustomEntities(player)) {
            if (entity instanceof EntityNPCInterface npc) {
                npc.getPersistentData().putUUID(NPCOwnerHelper.OWNER_TAG, markerUUID);
                if (npc instanceof RelationOwnerMarkedEntity marked) {
                    marked.hyw$markRelationOwnerUUID(markerUUID);
                }
                count++;
                com.hywbridge.HYWBridge.LOGGER.info(
                        "Assigned CNPC {} to faction '{}'", npc.getUUID(), factionName);
            }
        }

        // Assegna unità HYW selezionate
        var selection = SelectionSystem.getSelection(player);
        int hywCount = selection.getEntities().size();
        com.hywbridge.HYWBridge.LOGGER.info(
                "HYW selection has {} entities", hywCount);

        for (BaseCombatEntity unit : selection.getEntities()) {
            unit.m_30586_(markerUUID);
            if (unit instanceof RelationOwnerMarkedEntity marked) {
                marked.hyw$markRelationOwnerUUID(markerUUID);
            }
            count++;
            com.hywbridge.HYWBridge.LOGGER.info(
                    "Assigned HYW unit {} to faction '{}'", unit.getUUID(), factionName);
        }

        if (count == 0) {
            ctx.getSource().sendFailure(Component.literal(
                    "No units selected. Select units with RTS first, then use this command."));
            return 0;
        }

        FactionSyncHandler.syncNPCOwners(player);

        final int finalCount = count;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Assigned " + finalCount + " units (" + hywCount + " HYW, " +
                        (finalCount - hywCount) + " CNPC) to faction '" + factionName + "'"), false);
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