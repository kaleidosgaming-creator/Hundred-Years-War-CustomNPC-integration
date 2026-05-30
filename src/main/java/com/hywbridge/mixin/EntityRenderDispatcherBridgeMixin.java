package com.hywbridge.mixin;

import com.hywbridge.util.NPCOwnerHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ydmsama.hundred_years_war.client.freecam.Freecam;
import ydmsama.hundred_years_war.client.freecam.config.keys.HotKeyManager;
import ydmsama.hundred_years_war.client.freecam.selection.SelectionHandler;
import ydmsama.hundred_years_war.client.freecam.ui.wheel.CommandWheelHandler;

import java.util.UUID;

@Mixin(value = EntityRenderDispatcher.class, remap = true)
public class EntityRenderDispatcherBridgeMixin {

    @Inject(
            method = "render",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V",
                    shift = At.Shift.BEFORE),
            remap = true
    )
    private <E extends Entity> void onRender(
            E entity, double x, double y, double z,
            float yaw, float tickDelta,
            PoseStack poseStack, MultiBufferSource multiBufferSource,
            int light, CallbackInfo ci) {

        if (!(entity instanceof EntityNPCInterface npc)) return;
        if (!entity.isAlive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (!NPCOwnerHelper.isHywManagedNPC(npc)) return;

        boolean isSelected = SelectionHandler.getInstance()
                .getSelectedEntities().contains(entity);
        boolean teamColorMode = HotKeyManager.getTeamColorMode();
        boolean freecamActive = Freecam.isEnabled()
                || CommandWheelHandler.getInstance().shouldRenderCommandEffect();

        if (!isSelected && !teamColorMode) return;

        float r, g, b;
        UUID myUUID = mc.player.getUUID();
        UUID ownerUUID = NPCOwnerHelper.getOwnerUUIDClientSafe(npc);
        int relation = NPCOwnerHelper.getRelationToPlayer(npc.getUUID());

        if (ownerUUID != null && ownerUUID.equals(myUUID)) {
            r = 0.0F; g = 1.0F; b = 0.0F; // verde = tuo
        } else if (relation == 2) {
            r = 1.0F; g = 0.0F; b = 0.0F; // rosso = ostile
        } else if (relation == 1) {
            r = 0.0F; g = 1.0F; b = 0.0F; // verde = amico
        } else if (relation == 0) {
            r = 1.0F; g = 1.0F; b = 0.0F; // giallo = neutrale
        } else {
            return;
        }

        AABB box = entity.getBoundingBox().move(
                -entity.getX(), -entity.getY(), -entity.getZ());

        double xOff = 0.2, zOff = 0.2;
        double minX = box.minX - xOff;
        double topY = box.minY + 0.2;
        double minZ = box.minZ - zOff;
        double maxX = box.maxX + xOff;
        double maxZ = box.maxZ + zOff;

        VertexConsumer vc = multiBufferSource.getBuffer(RenderType.lines());

        if (teamColorMode) {
            float scale = isSelected ? 1.0F : 0.5F;
            LevelRenderer.renderLineBox(poseStack, vc,
                    minX, topY - 0.18, minZ,
                    maxX, topY, maxZ,
                    r * scale, g * scale, b * scale, 1.0F);
        }

        if (isSelected && freecamActive && !teamColorMode) {
            LevelRenderer.renderLineBox(poseStack, vc,
                    minX, topY, minZ,
                    maxX, topY, maxZ,
                    r, g, b, 1.0F);
        }
    }
}