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

package io.wifi.starrailexpress.game;

import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;

import java.util.Set;
import java.util.function.Consumer;

/**
 * 把「原区块追踪视图」与「监控摄像头视野区块」合并后的视图。
 * 服务端据此把摄像头区块也纳入指定玩家的区块追踪集合，从而推送给客户端渲染。
 */
public class SecurityCameraChunkTrackingView implements ChunkTrackingView {

    private final ChunkTrackingView delegate;
    private final Set<ChunkPos> extra;

    public SecurityCameraChunkTrackingView(ChunkTrackingView delegate, Set<ChunkPos> extra) {
        this.delegate = delegate;
        this.extra = extra;
    }

    /** 并入摄像头区块之前的原始视图，用于退出监控时精确还原。 */
    public ChunkTrackingView delegate() {
        return this.delegate;
    }

    @Override
    public boolean contains(int x, int z, boolean boundaryOnly) {
        if (this.delegate.contains(x, z, boundaryOnly)) {
            return true;
        }
        return this.extra.contains(new ChunkPos(x, z));
    }

    @Override
    public void forEach(Consumer<ChunkPos> consumer) {
        this.delegate.forEach(consumer);
        for (ChunkPos chunkPos : this.extra) {
            consumer.accept(chunkPos);
        }
    }
}
