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

package org.agmas.noellesroles.utils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ModNBTUtils {

    public static void writePos(@NotNull CompoundTag tag, @NotNull String tagName, @Nullable Vec3 pos) {
        CompoundTag posTag = new CompoundTag();
        if (pos == null) {
            return;
        }
        posTag.putDouble("x", pos.x);
        posTag.putDouble("y", pos.y);
        posTag.putDouble("z", pos.z);
        tag.put(tagName, posTag);
    }

    public static Vec3 readPos(CompoundTag tag, String name, Vec3 fallback) {
        if (!tag.contains(name))
            return fallback;
        if (tag.getTagType(name) != Tag.TAG_COMPOUND)
            return fallback;
        var postag = tag.getCompound(name);
        if (postag.contains("x") && postag.contains("y") && postag.contains("z")
                && postag.getTagType("x") == Tag.TAG_DOUBLE && postag.getTagType("y") == Tag.TAG_DOUBLE
                && postag.getTagType("z") == Tag.TAG_DOUBLE) {
            return new Vec3(postag.getDouble("x"), postag.getDouble("y"), postag.getDouble("z"));
        } else {
            return fallback;
        }
    }

}
