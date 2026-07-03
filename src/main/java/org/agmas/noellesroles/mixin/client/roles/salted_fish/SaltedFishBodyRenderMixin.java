package org.agmas.noellesroles.mixin.client.roles.salted_fish;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.wifi.starrailexpress.client.render.entity.PlayerBodyEntityRenderer;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.content.entity.SaltedFishBodyEntity;
import org.agmas.noellesroles.game.roles.innocence.salted_fish.SaltedFishPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerBodyEntityRenderer.class)
public abstract class SaltedFishBodyRenderMixin {
    @Inject(method = "setupRotations(Lio/wifi/starrailexpress/content/entity/PlayerBodyEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void noellesroles$setupSaltedFishRotations(PlayerBodyEntity body, PoseStack poseStack,
            float animationProgress, float bodyYaw, float tickDelta, float scale, CallbackInfo ci) {
        if (!(body instanceof SaltedFishBodyEntity)) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || body.getPlayerUuid() == null) {
            return;
        }
        Player owner = client.level.getPlayerByUUID(body.getPlayerUuid());
        SaltedFishPlayerComponent component = owner == null ? null
                : SaltedFishPlayerComponent.KEY.maybeGet(owner).orElse(null);
        if (component == null || !component.isActive()) {
            return;
        }

        poseStack.mulPose(Axis.YP.rotationDegrees(90.0f - bodyYaw));
        poseStack.translate(1.0f, 0.2f + component.getRenderBounce(tickDelta), 0.0f);
        poseStack.mulPose(Axis.ZP.rotationDegrees(component.getRenderRoll(tickDelta)));
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0f));
        ci.cancel();
    }
}
