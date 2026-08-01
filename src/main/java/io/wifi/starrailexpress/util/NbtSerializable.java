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

package io.wifi.starrailexpress.util;

import net.minecraft.nbt.CompoundTag;

/**
 * 实现此接口的类可以自定义 NBT 序列化和反序列化行为。
 * 优先于默认的反射遍历，但低于通过 Builder 注册的适配器。
 */
public interface NbtSerializable {
    /**
     * 将对象的数据写入到给定的 CompoundTag 中。
     * @param tag 写入目标
     */
    void writeNbt(CompoundTag tag);

    /**
     * 从给定的 CompoundTag 中读取数据并填充到当前对象。
     * @param tag 数据来源
     */
    void readNbt(CompoundTag tag);
}