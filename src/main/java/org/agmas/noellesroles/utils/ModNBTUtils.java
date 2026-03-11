package org.agmas.noellesroles.utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

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
