/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pro.fazeclan.river.stupid_express.mixin.client.modifier.twin_children;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.fazeclan.river.stupid_express.modifier.twin_children.TwinChildrenHandler;

/**
 * Makes the stacked twins' models match their collision boxes.
 *
 * <p>
 * A twin's model is only halved by the SCALE attribute, so the lower twin would
 * be drawn 0.9 tall while its gap-blocking box is 1.3, and the upper twin would
 * be drawn 0.9 tall while its box is only 0.5. Each render matrix is scaled so
 * the model fills its own box; the upper twin keeps riding on the lower twin's
 * collision top, so its model lands inside {@code 1.3..1.8} as well.
 *
 * <p>
 * A passenger is drawn at its own entity position (only
 * {@code Entity#positionRider} reads {@code getPassengerAttachmentPoint}), so
 * this adjusts rendering only: collision, physics and synced positions stay put.
 * No positional offset is applied.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class TwinChildrenRenderMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void stupidExpress$scaleStackedTwinModel(LivingEntity entity, float yaw, float tickDelta,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
        if (!(entity instanceof Player player) || !TwinChildrenHandler.stackedTwin(player)) {
            return;
        }
        float factor = TwinChildrenHandler.isStackedUpper(player)
                ? TwinChildrenHandler.upperModelScaleFactor()
                : TwinChildrenHandler.lowerModelScaleFactor();
        poseStack.scale(factor, factor, factor);
    }
}
