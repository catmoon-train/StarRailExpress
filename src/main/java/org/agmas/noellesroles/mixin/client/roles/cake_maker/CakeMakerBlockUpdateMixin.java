package org.agmas.noellesroles.mixin.client.roles.cake_maker;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import org.agmas.noellesroles.client.ClientCakeMakerBlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截服务端方块更新包，防止覆盖蛋糕师的客户端烟熏炉和蛋糕方块。
 */
@Mixin(ClientPacketListener.class)
public class CakeMakerBlockUpdateMixin {

    @Inject(method = "handleBlockUpdate", at = @At("HEAD"), cancellable = true)
    private void noellesroles$cancelBlockUpdateForCakeMaker(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        if (ClientCakeMakerBlocks.isAt(packet.getPos())) {
            ci.cancel();
        }
    }
}
