package org.agmas.noellesroles.mixin.client.time_stop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.effects.TimeStopEffect;
import org.agmas.noellesroles.init.ModEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
;

@Mixin(PlayerRenderer.class)
public abstract class PlayerAnimationStopMixin extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public PlayerAnimationStopMixin(EntityRendererProvider.Context context, PlayerModel<AbstractClientPlayer> entityModel, float f) {
        super(context, entityModel, f);
    }

    @Shadow
    protected abstract void setModelProperties(AbstractClientPlayer abstractClientPlayer);

    @Unique
    private static boolean isChangePlayerAnimationStop = false;
    @ModifyVariable(
            method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),

            argsOnly = true
    )
    public AbstractClientPlayer modifyRenderedPlayer(AbstractClientPlayer abstractClientPlayer) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.hasEffect(ModEffects.TIME_STOP)) {
            if (TimeStopEffect.canMovePlayers.contains(abstractClientPlayer.getUUID())&&TimeStopEffect.canMovePlayers.contains(player.getUUID()))return abstractClientPlayer;
            AbstractClientPlayer replacement = NoellesrolesClient.lastTimeStopRenderPlayer.get(abstractClientPlayer.getUUID());
            if (replacement != null) {
                isChangePlayerAnimationStop = true;
                abstractClientPlayer.setPos(replacement.getX(), replacement.getY(), replacement.getZ());
                abstractClientPlayer.setYHeadRot(replacement.getYHeadRot());
                abstractClientPlayer.setXRot(replacement.getXRot());
                abstractClientPlayer.setYBodyRot(replacement.yBodyRot);
                abstractClientPlayer.setYRot(replacement.getYRot());
                abstractClientPlayer.setPose(replacement.getPose());
                abstractClientPlayer.setDeltaMovement(0,0,0);
                return abstractClientPlayer;
            }
        }
        return abstractClientPlayer;
    }

}