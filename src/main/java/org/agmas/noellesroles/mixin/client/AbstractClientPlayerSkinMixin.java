package org.agmas.noellesroles.mixin.client;

import io.wifi.starrailexpress.event.OnGettingPlayerSkin;
import io.wifi.starrailexpress.event.OnGettingPlayerSkin.PlayerSkinResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerSkinMixin {

    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void applySkinSwap(CallbackInfoReturnable<PlayerSkin> cir) {
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null)
            return;
        PlayerSkinResult result = OnGettingPlayerSkin.EVENT.invoker().onGetSkin(self);
        if (result == null || result.type == 0 || result.type == -1) {
            return;
        }
        if (result.type == 2 && result.playerSkin != null) {
            cir.setReturnValue(result.playerSkin);
            return;
        }
        /**
         * 此处为了某些兼容性所以删了 (result.type == 1 时)。但是材质还是会变，在 PlayerEntityRendererMixin 中。
         */
        // PlayerSkin.Model model = result.isSlim ? PlayerSkin.Model.SLIM :
        // PlayerSkin.Model.WIDE;
        // PlayerSkin ret = new PlayerSkin(result.texture, null, null, null, model,
        // true);
        // cir.setReturnValue(ret);
    }
}
