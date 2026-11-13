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

package io.wifi.starrailexpress.mixin.server;

import io.wifi.starrailexpress.game.SecurityCameraChunkTickets;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.Set;

/**
 * 把「正在被某玩家观看的监控摄像头」视野区块并入该玩家的区块追踪视图。
 *
 * <p>监控画面是客户端把相机放到摄像头位置渲染的，而客户端只会加载「玩家本体视距内」的区块。
 * 服务端计算某个玩家的区块追踪视图时（{@code applyChunkTrackingView}），这里把摄像头视野区块
 * 一并算进去，于是服务端会像对待普通视距区块那样把这些区块推送给该客户端，远端摄像头所在的
 * 区块不再是全透明空洞。玩家停止观看后 SecurityCameraChunkTickets 会移除记录并重算视图，区块随即卸载。
 */
@Mixin(net.minecraft.server.level.ChunkMap.class)
public abstract class ChunkMapSecurityCameraMixin {

    @Shadow
    @Final
    private ServerLevel level;

    @ModifyArgs(method = "applyChunkTrackingView", at = @At("HEAD"))
    private void expandViewForSecurityCamera(Args args) {
        ServerPlayer player = args.get(0);
        if (player == null) {
            return;
        }
        BlockPos camera = SecurityCameraChunkTickets.getViewingCamera(player.getUUID());
        if (camera == null) {
            return;
        }
        Set<ChunkPos> extra = SecurityCameraChunkTickets.collectCameraChunks(level, camera);
        if (extra.isEmpty()) {
            return;
        }
        ChunkTrackingView original = args.get(1);
        args.set(1, new SecurityCameraChunkTrackingView(original, extra));
    }
}
