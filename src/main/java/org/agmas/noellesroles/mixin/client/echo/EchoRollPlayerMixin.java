package org.agmas.noellesroles.mixin.client.echo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.agmas.noellesroles.game.modes.echo.anim.EchoRollAnim;
import org.agmas.noellesroles.game.modes.echo.client.EchoRollClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 绕躯干中心沿翻滚方向旋转；仅作用于服务端批准的玩家动作，不改变碰撞箱。 */
@Mixin(LivingEntityRenderer.class)
public class EchoRollPlayerMixin {
    @Inject(method="setupRotations(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",at=@At("TAIL"))
    private void echo$roll(LivingEntity entity, PoseStack pose, float bob, float bodyYaw, float partial, float scale, CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayer)) return;
        var action=EchoRollClient.action(entity.getUUID()); if(action==null) return;
        float progress=EchoRollClient.progress(entity.getUUID(),partial); if(progress<0) return;
        float rollYaw=(float)Math.toDegrees(Math.atan2(-action.x(),action.z()));
        pose.mulPose(Axis.YP.rotationDegrees(bodyYaw-rollYaw));
        pose.translate(0,0.85,0); pose.mulPose(Axis.XP.rotationDegrees(EchoRollAnim.turn(progress))); pose.translate(0,-0.85,0);
    }
}
