package org.agmas.noellesroles.mixin.client.echo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.game.modes.echo.anim.EchoRollAnim;
import org.agmas.noellesroles.game.modes.echo.client.EchoRollClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 第一人称收手而不旋转相机，避免快速完整翻身造成眩晕。 */
@Mixin(ItemInHandRenderer.class)
public class EchoRollHandMixin {
    @Inject(method="renderArmWithItem",at=@At("HEAD"))
    private void echo$rollHands(AbstractClientPlayer player,float partial,float pitch,InteractionHand hand,float swing,
                                ItemStack stack,float equip,PoseStack pose,MultiBufferSource buffer,int light,CallbackInfo ci) {
        if(player==null) return;
        float p=EchoRollClient.progress(player.getUUID(),partial); if(p<0) return;
        float curl=EchoRollAnim.curl(p);
        pose.translate(0,-0.35F*curl,0.12F*curl); pose.mulPose(Axis.XP.rotationDegrees(15*curl));
    }
}
