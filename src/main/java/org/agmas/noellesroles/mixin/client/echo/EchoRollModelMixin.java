package org.agmas.noellesroles.mixin.client.echo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.agmas.noellesroles.game.modes.echo.anim.EchoRollAnim;
import org.agmas.noellesroles.game.modes.echo.client.EchoRollClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 翻身时收臂屈膝；原版每帧重设基础姿势，动作结束不会留下骨骼偏移。 */
@Mixin(PlayerModel.class)
public class EchoRollModelMixin {
    @Inject(method="setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",at=@At("TAIL"))
    private void echo$curl(LivingEntity entity,float limbSwing,float amount,float age,float yaw,float pitch,CallbackInfo ci) {
        float p=EchoRollClient.progress(entity.getUUID(),Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false)); if(p<0) return;
        float curl=EchoRollAnim.curl(p);
        var model=(PlayerModel<?>)(Object)this;
        model.head.xRot+=0.6F*curl; model.body.xRot+=0.25F*curl;
        model.rightArm.xRot=-1.7F*curl; model.leftArm.xRot=-1.7F*curl;
        model.rightArm.zRot=0.2F*curl; model.leftArm.zRot=-0.2F*curl;
        model.rightLeg.xRot=-1.2F*curl; model.leftLeg.xRot=-1.2F*curl;
        model.hat.copyFrom(model.head); model.jacket.copyFrom(model.body);
        model.rightSleeve.copyFrom(model.rightArm); model.leftSleeve.copyFrom(model.leftArm);
        model.rightPants.copyFrom(model.rightLeg); model.leftPants.copyFrom(model.leftLeg);
    }
}
