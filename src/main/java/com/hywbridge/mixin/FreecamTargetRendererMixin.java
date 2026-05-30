package com.hywbridge.mixin;

import com.hywbridge.util.NPCOwnerHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;
import org.joml.Matrix3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ydmsama.hundred_years_war.client.freecam.Freecam;
import ydmsama.hundred_years_war.client.freecam.selection.SelectionHandler;
import ydmsama.hundred_years_war.client.freecam.ui.wheel.CommandWheelHandler;
import ydmsama.hundred_years_war.client.renderer.FreecamTargetRenderer;

import java.util.List;

@Mixin(value = FreecamTargetRenderer.class, remap = false)
public class FreecamTargetRendererMixin {

    @Inject(method = "renderEntityTargets", at = @At("TAIL"), remap = false)
    private static void injectCustomNPCTargets(PoseStack poseStack,
                                               Camera camera, Minecraft mc, CallbackInfo ci) {
        if (mc.level == null) return;

        boolean shouldRender = Freecam.isEnabled()
                || CommandWheelHandler.getInstance().shouldRenderCommandEffect();
        if (!shouldRender) return;

        SelectionHandler handler = SelectionHandler.getInstance();
        List<Entity> selected = handler.getSelectedEntities();
        Vec3 cameraPos = camera.getPosition();

        VertexConsumer vc = mc.renderBuffers().bufferSource()
                .getBuffer(RenderType.lines());

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof EntityNPCInterface npc)) continue;
            if (!selected.contains(npc)) continue;
            if (!NPCOwnerHelper.isHywManagedNPC(npc)) continue;

            var targets = handler.getCombinedTargetMap().get(entity);
            if (targets == null || targets.isEmpty()) continue;

            Vec3 entityPos = entity.position().subtract(cameraPos);

            for (var target : targets) {
                String type = target.getType();
                if ("entityTarget".equals(type) || "followTarget".equals(type)) continue;

                BlockPos pos = target.getPosition();
                if (pos == null) continue;

                // Non disegnare se l'NPC è già arrivato (distanza < 2 blocchi)
                double distToTarget = entity.position().distanceTo(
                        new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
                if (distToTarget < 2.0) {
                    // Rimuovi il target dalla mappa — l'NPC è arrivato
                    handler.getCombinedTargetMap().remove(entity);
                    break;
                }

                Vec3 centerPos = new Vec3(
                        pos.getX() + 0.5 - cameraPos.x,
                        pos.getY() + 1.1 - cameraPos.y,
                        pos.getZ() + 0.5 - cameraPos.z
                );

                float r, g, b;
                if ("target".equals(type) || "formTarget".equals(type)) {
                    r = 0.0F; g = 1.0F; b = 0.0F;
                } else {
                    r = 1.0F; g = 0.0F; b = 0.0F;
                }

                double outerMinX = centerPos.x - 0.5;
                double outerMinZ = centerPos.z - 0.5;
                double outerMaxX = centerPos.x + 0.5;
                double outerMaxZ = centerPos.z + 0.5;
                double innerMinX = centerPos.x - 0.3;
                double innerMinZ = centerPos.z - 0.3;
                double innerMaxX = centerPos.x + 0.3;
                double innerMaxZ = centerPos.z + 0.3;

                AABB outerBox = new AABB(outerMinX, centerPos.y, outerMinZ, outerMaxX, centerPos.y, outerMaxZ);
                AABB innerBox = new AABB(innerMinX, centerPos.y, innerMinZ, innerMaxX, centerPos.y, innerMaxZ);

                LevelRenderer.renderLineBox(poseStack, vc,
                        outerBox.minX, outerBox.minY, outerBox.minZ,
                        outerBox.maxX, outerBox.maxY, outerBox.maxZ,
                        r, g, b, 1.0F);
                LevelRenderer.renderLineBox(poseStack, vc,
                        innerBox.minX, innerBox.minY, innerBox.minZ,
                        innerBox.maxX, innerBox.maxY, innerBox.maxZ,
                        r, g, b, 1.0F);

                Matrix3f normalMatrix = poseStack.last().normal();
                vc.vertex(poseStack.last().pose(),
                                (float)entityPos.x, (float)entityPos.y, (float)entityPos.z)
                        .color(r, g, b, 1.0F)
                        .normal(normalMatrix, 0.0F, 1.0F, 0.0F)
                        .endVertex();
                vc.vertex(poseStack.last().pose(),
                                (float)centerPos.x, (float)centerPos.y, (float)centerPos.z)
                        .color(r, g, b, 1.0F)
                        .normal(normalMatrix, 0.0F, 1.0F, 0.0F)
                        .endVertex();
            }
        }
    }
}