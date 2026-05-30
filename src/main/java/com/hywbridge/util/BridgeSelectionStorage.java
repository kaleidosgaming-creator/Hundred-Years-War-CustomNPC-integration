package com.hywbridge.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BridgeSelectionStorage {

    private static final Map<UUID, List<Entity>> customSelections = new ConcurrentHashMap<>();

    public static void setCustomEntities(ServerPlayer player, List<Entity> entities) {
        customSelections.put(player.getUUID(), new ArrayList<>(entities));
    }

    public static List<Entity> getCustomEntities(ServerPlayer player) {
        return customSelections.getOrDefault(player.getUUID(), Collections.emptyList());
    }

    public static void addEntity(ServerPlayer player, Entity entity) {
        customSelections.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(entity);
    }

    public static void clear(ServerPlayer player) {
        customSelections.remove(player.getUUID());
    }

    public static boolean isEmpty(ServerPlayer player) {
        List<Entity> list = customSelections.get(player.getUUID());
        return list == null || list.isEmpty();
    }
}
