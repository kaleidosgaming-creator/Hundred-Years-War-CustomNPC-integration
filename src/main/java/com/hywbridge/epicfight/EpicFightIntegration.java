package com.hywbridge.epicfight;

import com.hywbridge.HYWBridge;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import yesman.epicfight.api.forgeevent.EntityPatchRegistryEvent;

/**
 * Registra il CNPCEpicFightPatch tramite l'evento ufficiale EF.
 * Deve essere sul MOD bus, non FORGE bus.
 */
@Mod.EventBusSubscriber(modid = HYWBridge.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class EpicFightIntegration {

    @SubscribeEvent
    public static void onEntityPatchRegistry(EntityPatchRegistryEvent event) {
        try {
            // Recupera EntityType dei CNPC tramite registry name
            EntityType<?> cnpcType = net.minecraftforge.registries.ForgeRegistries
                    .ENTITY_TYPES.getValue(
                            new net.minecraft.resources.ResourceLocation(
                                    "customnpcs", "customnpc"));

            if (cnpcType == null) {
                HYWBridge.LOGGER.warn(
                        "[EpicFightIntegration] customnpcs:customnpc EntityType not found — " +
                                "CustomNPCs not loaded?");
                return;
            }

            event.getTypeEntry().put(cnpcType, entity -> CNPCEpicFightPatch::new);

            HYWBridge.LOGGER.info(
                    "[EpicFightIntegration] Registered Epic Fight patch for customnpcs:customnpc");

        } catch (Exception e) {
            HYWBridge.LOGGER.error(
                    "[EpicFightIntegration] Failed to register Epic Fight patch", e);
        }
    }
}