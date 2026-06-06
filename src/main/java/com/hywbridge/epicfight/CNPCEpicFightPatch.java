package com.hywbridge.epicfight;

import net.minecraft.world.entity.PathfinderMob;
import yesman.epicfight.api.animation.Animator;
import yesman.epicfight.world.capabilities.entitypatch.Factions;
import yesman.epicfight.world.capabilities.entitypatch.HumanoidMobPatch;
import yesman.epicfight.world.damagesource.StunType;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.gameasset.Animations;

public class CNPCEpicFightPatch<T extends PathfinderMob> extends HumanoidMobPatch<T> {

    public CNPCEpicFightPatch() {
        super(Factions.NEUTRAL);
    }

    @Override
    protected void initAnimator(Animator animator) {
        // Chiama prima il super per registrare le variabili base (ATTACK_TRIED_ENTITIES ecc.)
        super.initAnimator(animator);
        // Poi aggiungi le animazioni biped standard tramite l'helper ufficiale
        this.commonAggresiveMobAnimatorInit(animator);
    }

    @Override
    public void updateMotion(boolean considerInaction) {
        super.commonAggressiveMobUpdateMotion(considerInaction);
    }

    @Override
    public AnimationManager.AnimationAccessor<? extends StaticAnimation> getHitAnimation(StunType stunType) {
        // Delega alla superclasse — ha già tutta la logica di hit animation biped
        return super.getHitAnimation(stunType);
    }
}