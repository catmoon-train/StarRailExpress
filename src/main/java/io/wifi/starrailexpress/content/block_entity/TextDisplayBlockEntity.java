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

package io.wifi.starrailexpress.content.block_entity;

import io.wifi.starrailexpress.index.SREDisplayBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 文本展示方块的方块实体：NBT 与原版 {@code minecraft:text_display} 一致。
 *
 * <p>原版键：text（JSON 聊天文本字符串）/ line_width / background / text_opacity /
 * shadow / see_through / default_background / alignment。
 */
public class TextDisplayBlockEntity extends DisplayBlockEntityBase {

    public static final String TAG_TEXT = "text";
    public static final String TAG_LINE_WIDTH = "line_width";
    public static final String TAG_BACKGROUND = "background";
    public static final String TAG_TEXT_OPACITY = "text_opacity";
    public static final String TAG_SHADOW = "shadow";
    public static final String TAG_SEE_THROUGH = "see_through";
    public static final String TAG_DEFAULT_BACKGROUND = "default_background";
    public static final String TAG_ALIGNMENT = "alignment";

    public static final int DEFAULT_LINE_WIDTH = 200;
    /** 与原版一致的默认背景色：25% 黑。 */
    public static final int DEFAULT_BACKGROUND = 0x40000000;
    public static final byte DEFAULT_TEXT_OPACITY = -1;

    /** 没有 text 键时渲染出来的占位文本，保证刚放下的方块可见、可被右键。 */
    public static final String DEFAULT_TEXT = "{\"text\":\"Text Display\",\"color\":\"gold\"}";

    public TextDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(SREDisplayBlocks.TEXT_DISPLAY_BLOCK_ENTITY, pos, state);
    }

    @Override
    protected List<String> contentKeys() {
        return List.of(TAG_TEXT, TAG_LINE_WIDTH, TAG_BACKGROUND, TAG_TEXT_OPACITY,
                TAG_SHADOW, TAG_SEE_THROUGH, TAG_DEFAULT_BACKGROUND, TAG_ALIGNMENT);
    }

    @Override
    protected void contentSanitize() {
        clampInt(TAG_LINE_WIDTH, 1, 2000);
        // text_opacity 原版就是按 byte 存的，0..255 都合法（-1 与 255 等价，都是不透明），
        // 所以这里不做夹取，免得分不清"无符号 200"和"有符号 -56"。
    }

    @Override
    protected void contentFillDefaults(CompoundTag tag) {
        if (!tag.contains(TAG_TEXT)) {
            tag.putString(TAG_TEXT, DEFAULT_TEXT);
        }
        if (!tag.contains(TAG_LINE_WIDTH)) {
            tag.putInt(TAG_LINE_WIDTH, DEFAULT_LINE_WIDTH);
        }
        if (!tag.contains(TAG_BACKGROUND)) {
            tag.putInt(TAG_BACKGROUND, DEFAULT_BACKGROUND);
        }
        if (!tag.contains(TAG_TEXT_OPACITY)) {
            tag.putByte(TAG_TEXT_OPACITY, DEFAULT_TEXT_OPACITY);
        }
        if (!tag.contains(TAG_ALIGNMENT)) {
            tag.putString(TAG_ALIGNMENT, Display.TextDisplay.Align.CENTER.getSerializedName());
        }
    }

    // ───────────────────────── 渲染器 / 界面读取 ─────────────────────────

    /** 存下来的原始 JSON 文本，缺失时给占位文本。 */
    public String getTextJson() {
        return readString(getTag(), TAG_TEXT, DEFAULT_TEXT);
    }

    public int getLineWidth() {
        return readInt(getTag(), TAG_LINE_WIDTH, DEFAULT_LINE_WIDTH);
    }

    public int getBackgroundColor() {
        return readInt(getTag(), TAG_BACKGROUND, DEFAULT_BACKGROUND);
    }

    public byte getTextOpacity() {
        CompoundTag tag = getTag();
        return tag.contains(TAG_TEXT_OPACITY, Tag.TAG_ANY_NUMERIC)
                ? tag.getByte(TAG_TEXT_OPACITY)
                : DEFAULT_TEXT_OPACITY;
    }

    public boolean isShadow() {
        return readBoolean(getTag(), TAG_SHADOW, false);
    }

    public boolean isSeeThrough() {
        return readBoolean(getTag(), TAG_SEE_THROUGH, false);
    }

    public boolean isDefaultBackground() {
        return readBoolean(getTag(), TAG_DEFAULT_BACKGROUND, false);
    }

    public Display.TextDisplay.Align getAlignment() {
        return readAlignment(getTag());
    }

    /** 按原版规则把各个开关和在一起，得到 {@code Display.TextDisplay} 的 flags 字节。 */
    public byte getFlags() {
        return readFlags(getTag());
    }

    /** 渲染器每帧调用，所以做成静态的、直接吃标签，避免依赖方块实体实例。 */
    public static byte readFlags(CompoundTag tag) {
        byte flags = 0;
        if (readBoolean(tag, TAG_SHADOW, false)) {
            flags |= Display.TextDisplay.FLAG_SHADOW;
        }
        if (readBoolean(tag, TAG_SEE_THROUGH, false)) {
            flags |= Display.TextDisplay.FLAG_SEE_THROUGH;
        }
        if (readBoolean(tag, TAG_DEFAULT_BACKGROUND, false)) {
            flags |= Display.TextDisplay.FLAG_USE_DEFAULT_BACKGROUND;
        }
        flags |= switch (readAlignment(tag)) {
            case CENTER -> 0;
            case LEFT -> Display.TextDisplay.FLAG_ALIGN_LEFT;
            case RIGHT -> Display.TextDisplay.FLAG_ALIGN_RIGHT;
        };
        return flags;
    }

    /** 供界面读取文本不透明度：按无符号 0..255 解释，缺省是 255（不透明）。 */
    public static int effectiveTextOpacity(CompoundTag tag) {
        return tag.contains(TAG_TEXT_OPACITY, Tag.TAG_ANY_NUMERIC) ? tag.getByte(TAG_TEXT_OPACITY) & 0xFF : 0xFF;
    }

    /**
     * 渲染器用的文本不透明度：直接给 {@code (byte) 值 << 24} 当 alpha 用，
     * 所以存成有符号的 -1（= 无符号 255）和存成 255 效果一样，保持原版语义。
     */
    public static int readOpacity(CompoundTag tag) {
        return tag.contains(TAG_TEXT_OPACITY, Tag.TAG_ANY_NUMERIC) ? tag.getByte(TAG_TEXT_OPACITY)
                : DEFAULT_TEXT_OPACITY;
    }

    public static void setTextOpacity(CompoundTag tag, int value) {
        tag.putByte(TAG_TEXT_OPACITY, (byte) Math.max(0, Math.min(255, value)));
    }

    public static Display.TextDisplay.Align readAlignment(CompoundTag tag) {
        if (!tag.contains(TAG_ALIGNMENT, Tag.TAG_STRING)) {
            return Display.TextDisplay.Align.CENTER;
        }
        return Display.TextDisplay.Align.CODEC.parse(NbtOps.INSTANCE, tag.get(TAG_ALIGNMENT))
                .resultOrPartial()
                .orElse(Display.TextDisplay.Align.CENTER);
    }

    /**
     * 解析 text 里的 JSON 聊天文本。解析失败返回 null，由调用方决定怎么显示错误。
     * 原版会在此基础上再跑一次 {@code ComponentUtils.updateForEntity} 解析 @e 选择器，
     * 但方块没有关联实体，这里直接跳过。
     */
    @Nullable
    public Component parseText(HolderLookup.Provider registries) {
        return parseText(getTextJson(), registries);
    }

    /** 解析任意一段展示用的 JSON 文本（关键帧里也能带自己的文本）。 */
    @Nullable
    public static Component parseText(String json, HolderLookup.Provider registries) {
        try {
            return Component.Serializer.fromJson(json, registries);
        } catch (Exception exception) {
            return null;
        }
    }
}
