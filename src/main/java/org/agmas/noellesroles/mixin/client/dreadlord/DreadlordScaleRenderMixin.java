package org.agmas.noellesroles.mixin.client.dreadlord;

import com.mojang.blaze3d.vertex.PoseStack;

import org.agmas.noellesroles.game.command_block.DreadlordRole;
import org.agmas.noellesroles.role_data.killer.DreadlordRoleData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/** 杀戮形态的碰撞箱被压回原尺寸后，模型仍按缩放画大。 */
@Mixin(PlayerRenderer.class)
public class DreadlordScaleRenderMixin {
    @Inject(method = "scale(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;F)V", at = @At("RETURN"))
    private void noellesroles$dreadlordScale(AbstractClientPlayer player, PoseStack poseStack, float partialTick,
            CallbackInfo ci) {
        if (!DreadlordRole.isDreadlord(player)) {
            return;
        }
        DreadlordRoleData data = DreadlordRole.dataOf(player);
        if (data == null || !data.isKilling()) {
            return;
        }
        float scale = (float) DreadlordRole.KILL_SCALE;
        poseStack.scale(scale, scale, scale);
    }
}
