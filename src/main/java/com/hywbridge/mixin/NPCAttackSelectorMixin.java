package com.hywbridge.mixin;

import com.hywbridge.command.HYWBridgeCommands;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.ai.selector.NPCAttackSelector;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ydmsama.hundred_years_war.main.utils.RelationSystem;

import java.util.UUID;

@Mixin(value = NPCAttackSelector.class, remap = false)
public class NPCAttackSelectorMixin {

    @Inject(method = "isEntityApplicable", at = @At("HEAD"), cancellable = true)
    private void injectHYWFactionCheck(LivingEntity entity,
                                       CallbackInfoReturnable<Boolean> cir) {

        EntityNPCInterface attacker;
        try {
            java.lang.reflect.Field f = NPCAttackSelector.class.getDeclaredField("npc");
            f.setAccessible(true);
            attacker = (EntityNPCInterface) f.get(this);
        } catch (Exception e) {
            return;
        }

        if (!(entity instanceof EntityNPCInterface target)) return;

        // Entrambi devono avere fazione con team HYW corrispondente
        if (attacker.faction == null || attacker.faction.name == null
                || attacker.faction.name.isEmpty()) return;
        if (target.faction == null || target.faction.name == null
                || target.faction.name.isEmpty()) return;

        UUID attackerTeam = HYWBridgeCommands.findTeamUUID(attacker.faction.name);
        UUID targetTeam = HYWBridgeCommands.findTeamUUID(target.faction.name);

        // Se uno dei due non ha team HYW, lascia decidere la logica CNPC nativa
        if (attackerTeam == null || targetTeam == null) return;

        // Stessa fazione → non attaccare mai
        if (attackerTeam.equals(targetTeam)) {
            cir.setReturnValue(false);
            return;
        }

        // Controlla relazione HYW
        RelationSystem.RelationType rel = RelationSystem.getRelation(attackerTeam, targetTeam);

        if (rel == RelationSystem.RelationType.HOSTILE) {
            // Hostile: attacca se in range e vivo
            // Le condizioni di range e vita sono già verificate dal selector nativo,
            // qui ci limitiamo ad autorizzare — lasciamo fare al selector originale
            // NON settiamo true qui: permettiamo alla logica CNPC di completare il check
            return; // lascia passare al selector originale con autorizzazione implicita
        } else if (rel == RelationSystem.RelationType.FRIENDLY
                || rel == RelationSystem.RelationType.CONTROL) {
            // Alleato → blocca sempre l'attacco
            cir.setReturnValue(false);
        }
        // NEUTRAL: lascia decidere la logica CNPC nativa
    }
}