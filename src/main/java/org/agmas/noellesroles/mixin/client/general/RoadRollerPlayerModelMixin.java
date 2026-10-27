package org.agmas.noellesroles.mixin.client.general;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.client.RoadRollerClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DIO 俯身双手向下压制；只改每帧重置的旋转，不污染模型后续姿势。 */
@Mixin(PlayerModel.class)
public abstract class RoadRollerPlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("RETURN"))
    private void noellesroles$pressRoller(LivingEntity entity, float swing, float amount, float age,
            float yaw, float pitch, CallbackInfo ci) {
        if (!(entity instanceof Player player) || !RoadRollerClient.isPressing(player.getUUID())) return;
        PlayerModel<?> model = (PlayerModel<?>) (Object) this;
        float pulse = (float) Math.sin(age * 1.8) * 0.12F;
        model.body.xRot = 0.5F;
        model.head.xRot = 0.65F;
        model.rightArm.xRot = -0.75F + pulse;
        model.leftArm.xRot = -0.75F + pulse;
        model.rightArm.zRot = 0.18F;
        model.leftArm.zRot = -0.18F;
        model.rightLeg.xRot = -0.45F;
        model.leftLeg.xRot = -0.45F;
        model.jacket.copyFrom(model.body);
        model.hat.copyFrom(model.head);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightPants.copyFrom(model.rightLeg);
        model.leftPants.copyFrom(model.leftLeg);
    }
}
