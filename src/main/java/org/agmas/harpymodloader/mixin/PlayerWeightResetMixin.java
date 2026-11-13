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

package org.agmas.harpymodloader.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public class PlayerWeightResetMixin {

    @Inject(method = "remove", at = @At("HEAD"))
    public void onPlayerDisconnect(ServerPlayer player,
            CallbackInfo ci) {
        // 玩家阵营权重是跨对局的历史数据，退出 / 重连不能清空，
        // 否则轮选、海选这类较长流程中掉线重连的玩家权重会直接归零，无法正确升降。
        // 这里只清掉本局的临时强制阵营请求；历史数据随服务器进程一起释放。
        PlayerRoleWeightManager.ForcePlayerTeam.remove(player.getUUID());
    }
}
