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

        // Ottieni l'NPC attaccante via reflection (campo privato 'npc')
        EntityNPCInterface attacker;
        try {
            java.lang.reflect.Field f = NPCAttackSelector.class.getDeclaredField("npc");
            f.setAccessible(true);
            attacker = (EntityNPCInterface) f.get(this);
        } catch (Exception e) {
            return;
        }

        if (!(entity instanceof EntityNPCInterface target)) return;

        // Controlla se entrambi hanno fazioni con nomi corrispondenti a team HYW
        if (attacker.faction == null || attacker.faction.name == null
                || attacker.faction.name.isEmpty()) return;
        if (target.faction == null || target.faction.name == null
                || target.faction.name.isEmpty()) return;

        UUID attackerTeam = HYWBridgeCommands.findTeamUUID(attacker.faction.name);
        UUID targetTeam = HYWBridgeCommands.findTeamUUID(target.faction.name);

        if (attackerTeam == null || targetTeam == null) return;
        if (attackerTeam.equals(targetTeam)) {
            // Stessa fazione → non attaccare
            cir.setReturnValue(false);
            return;
        }

        // Controlla relazione HYW tra i due team
        RelationSystem.RelationType rel = RelationSystem.getRelation(attackerTeam, targetTeam);
        if (rel == RelationSystem.RelationType.HOSTILE) {
            // Verifica range e condizioni base prima di confermare l'attacco
            if (attacker.isInRange(entity, attacker.stats.aggroRange)
                    && entity.isAlive() && entity.getHealth() >= 1.0f) {
                cir.setReturnValue(true);
            }
        } else if (rel == RelationSystem.RelationType.FRIENDLY
                || rel == RelationSystem.RelationType.CONTROL) {
            cir.setReturnValue(false);
        }
    }
}