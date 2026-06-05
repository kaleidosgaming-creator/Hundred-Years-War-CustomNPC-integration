package com.hywbridge.epicfight;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import yesman.epicfight.api.animation.Animator;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.world.capabilities.entitypatch.Factions;
import yesman.epicfight.world.capabilities.entitypatch.HumanoidMobPatch;
import yesman.epicfight.world.damagesource.StunType;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.types.StaticAnimation;

/**
 * Epic Fight patch per tutti i CustomNPC.
 * Fornisce animazioni biped standard e compatibilità piena
 * con il sistema di danno/stun di Epic Fight.
 *
 * Usa Factions.NEUTRAL così EF non interferisce con il
 * sistema fazioni di HYW Bridge.
 */
public class CNPCEpicFightPatch<T extends PathfinderMob> extends HumanoidMobPatch<T> {

    public CNPCEpicFightPatch() {
        super(Factions.NEUTRAL);
    }

    @Override
    public void initAnimator(Animator animator) {
        // Animazioni biped standard — sempre disponibili in EF core
        animator.addLivingAnimation(LivingMotions.IDLE,  Animations.BIPED_IDLE);
        animator.addLivingAnimation(LivingMotions.WALK,  Animations.BIPED_WALK);
        animator.addLivingAnimation(LivingMotions.CHASE, Animations.BIPED_WALK);
        animator.addLivingAnimation(LivingMotions.FALL,  Animations.BIPED_FALL);
        animator.addLivingAnimation(LivingMotions.MOUNT, Animations.BIPED_MOUNT);
        animator.addLivingAnimation(LivingMotions.DEATH, Animations.BIPED_DEATH);
    }

    @Override
    public void updateMotion(boolean considerInaction) {
        super.commonAggressiveMobUpdateMotion(considerInaction);
    }

    @Override
    public AnimationManager.AnimationAccessor<? extends StaticAnimation> getHitAnimation(StunType stunType) {
        // Reazioni ai colpi standard biped
        return switch (stunType) {
            case LONG      -> Animations.BIPED_HIT_LONG;
            case SHORT     -> Animations.BIPED_HIT_SHORT;
            case HOLD      -> Animations.BIPED_HIT_SHORT;
            case KNOCKDOWN -> Animations.BIPED_KNOCKDOWN;
            case NEUTRALIZE -> Animations.BIPED_COMMON_NEUTRALIZED;
            case FALL      -> Animations.BIPED_LANDING;
            default        -> null;
        };
    }
}