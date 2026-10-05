package org.agmas.noellesroles.mixin.client.roles.chef;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import org.agmas.noellesroles.client.ClientChefTrayManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 阻止服务端的区块更新覆盖掉客户端盘子。
 *
 * <p>盘子是客户端凭空画出来的，服务端那个位置在它眼里一直是空气，
 * 任何覆盖该位置的方块更新包都必须丢掉，否则盘子会莫名消失。
 */
@Mixin(ClientPacketListener.class)
public class ChefTrayBlockUpdateMixin {

    @Inject(method = "handleBlockUpdate", at = @At("HEAD"), cancellable = true)
    private void noe$cancelBlockUpdateForChefTray(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        if (ClientChefTrayManager.isTrayAt(packet.getPos())) {
            ci.cancel();
        }
    }
}
