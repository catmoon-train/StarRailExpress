package org.agmas.noellesroles.mixin.client.roles.builder;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import org.agmas.noellesroles.client.ClientWallManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 客户端方块更新包拦截 Mixin
 * 防止服务端方块更新覆盖建筑师的客户端墙
 */
@Mixin(ClientPacketListener.class)
public class BuilderBlockUpdateMixin {

    /**
     * 拦截服务端发送的单个方块更新包
     * 如果更新的位置是建筑师的墙方块，则取消该更新
     */
    @Inject(method = "handleBlockUpdate", at = @At("HEAD"), cancellable = true)
    private void noellesroles$cancelBlockUpdateForBuilderWall(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        if (ClientWallManager.isWallAt(packet.getPos())) {
            ci.cancel();
        }
    }
}
