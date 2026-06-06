package com.hywbridge.epicfight;

import com.hywbridge.HYWBridge;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import yesman.epicfight.api.forgeevent.EntityPatchRegistryEvent;
import yesman.epicfight.gameasset.Armatures;

@Mod.EventBusSubscriber(modid = HYWBridge.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class EpicFightIntegration {

    @SubscribeEvent
    public static void onEntityPatchRegistry(EntityPatchRegistryEvent event) {
        try {
            EntityType<?> cnpcType = net.minecraftforge.registries.ForgeRegistries
                    .ENTITY_TYPES.getValue(
                            new net.minecraft.resources.ResourceLocation(
                                    "customnpcs", "customnpc"));

            if (cnpcType == null) {
                HYWBridge.LOGGER.warn(
                        "[EpicFightIntegration] customnpcs:customnpc not found");
                return;
            }

            // CRITICO: registra l'armatura BIPED per il tipo CNPC
            // senza questo, onConstructed va in loop infinito cercando l'armatura
            Armatures.registerEntityTypeArmature(cnpcType, Armatures.BIPED);

            // Registra il patch
            event.getTypeEntry().put(cnpcType, entity -> CNPCEpicFightPatch::new);

            HYWBridge.LOGGER.info(
                    "[EpicFightIntegration] Registered EF patch + BIPED armature for customnpcs:customnpc");

        } catch (Exception e) {
            HYWBridge.LOGGER.error(
                    "[EpicFightIntegration] Failed to register EF integration", e);
        }
    }
}