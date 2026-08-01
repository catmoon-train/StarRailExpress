/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
