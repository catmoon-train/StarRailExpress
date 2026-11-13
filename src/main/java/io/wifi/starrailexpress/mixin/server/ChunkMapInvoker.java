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

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 {@link ChunkMap} 私有的 {@code applyChunkTrackingView}。
 *
 * <p>服务端只在 {@code updateChunkTracking} 里调用它，而该方法在「玩家静止、视图中心与视距
 * 都未变」时会提前返回——监控场景下玩家恰好站着不动，因此没法靠 {@code move} 触发下发。
 * 通过这个 invoker 可以直接把「并入摄像头区块」的视图应用到玩家身上，
 * 复用服务端现成的 {@code ChunkTrackingView.difference} 逻辑完成区块的推送与卸载。
 */
@Mixin(ChunkMap.class)
public interface ChunkMapInvoker {

    @Invoker("applyChunkTrackingView")
    void sre$applyChunkTrackingView(ServerPlayer player, ChunkTrackingView view);
}
