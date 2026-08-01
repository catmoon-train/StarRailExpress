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

package org.agmas.noellesroles.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import io.wifi.starrailexpress.game.GameUtils;
import org.agmas.noellesroles.content.block.scene.ReactorBlock;
import org.agmas.noellesroles.content.block_entity.scene.ReactorBlockEntity;

import java.util.*;

/**
 * 反应堆位置登记表（按维度，瞬态）。用于判断“场上所有反应堆是否都已关闭”。
 */
public final class ReactorRegistry {
    private ReactorRegistry() {
    }

    private static final Map<ResourceKey<Level>, Set<BlockPos>> REACTORS = new HashMap<>();

    public static void add(ServerLevel level, BlockPos pos) {
        REACTORS.computeIfAbsent(level.dimension(), k -> new HashSet<>()).add(pos.immutable());
    }

    public static void remove(ServerLevel level, BlockPos pos) {
        Set<BlockPos> set = REACTORS.get(level.dimension());
        if (set != null) {
            set.remove(pos);
        }
    }

    public static int count(ServerLevel level) {
        Set<BlockPos> set = REACTORS.get(level.dimension());
        return set == null ? 0 : set.size();
    }

    /** 场上是否存在反应堆且全部已关闭。 */
    public static boolean allClosed(ServerLevel level) {
        if (GameUtils.taskBlocks != null && !GameUtils.taskBlocks.isEmpty()) {
            return allScannedClosed(level);
        }
        Set<BlockPos> set = REACTORS.get(level.dimension());
        if (set == null || set.isEmpty()) {
            return false;
        }
        boolean anyValid = false;
        Iterator<BlockPos> it = set.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ReactorBlockEntity reactor) {
                anyValid = true;
                if (!reactor.isClosed()) {
                    return false;
                }
            } else {
                it.remove();
            }
        }
        return anyValid;
    }

    private static boolean allScannedClosed(ServerLevel level) {
        boolean anyValid = false;
        for (BlockPos pos : GameUtils.taskBlocks.keySet()) {
            var state = level.getBlockState(pos);
            if (state.getBlock() instanceof ReactorBlock) {
                anyValid = true;
                if (!state.hasProperty(ReactorBlock.CLOSED)
                        || !state.getValue(ReactorBlock.CLOSED)) {
                    return false;
                }
            }
        }
        return anyValid;
    }
}
