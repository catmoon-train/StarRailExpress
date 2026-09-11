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
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * 当前维度里所有已配置的上下循环镜子。
 */
public final class VerticalLoopingMirrorSavedData extends SavedData {
    private static final String DATA_NAME = "starrailexpress_vertical_looping_mirrors";

    private final List<VerticalLoopingMirrorLoop> loops = new ArrayList<>();

    public static VerticalLoopingMirrorSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(VerticalLoopingMirrorSavedData::new,
                        VerticalLoopingMirrorSavedData::load, null),
                DATA_NAME);
    }

    public List<VerticalLoopingMirrorLoop> loops() {
        return loops;
    }

    public void add(VerticalLoopingMirrorLoop loop) {
        loops.removeIf(existing -> existing.controller().equals(loop.controller()) || existing.equals(loop));
        loops.add(loop);
        setDirty();
    }

    public boolean removeContaining(BlockPos pos) {
        boolean removed = loops.removeIf(loop -> loop.contains(pos));
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public static VerticalLoopingMirrorSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        VerticalLoopingMirrorSavedData data = new VerticalLoopingMirrorSavedData();
        for (Tag element : tag.getList("Loops", Tag.TAG_COMPOUND)) {
            VerticalLoopingMirrorLoop loop = VerticalLoopingMirrorLoop.load((CompoundTag) element);
            if (loop != null) {
                data.loops.add(loop);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (VerticalLoopingMirrorLoop loop : loops) {
            list.add(loop.save());
        }
        tag.put("Loops", list);
        return tag;
    }
}
