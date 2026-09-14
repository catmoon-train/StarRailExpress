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

import com.mojang.math.Transformation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 展示方块的本地关键帧动画。
 *
 * <p>动画数据跟着展示 NBT 一起走（自定义键，原版不认识但也不会冲突），
 * 采样完全由客户端按 {@code level.getGameTime()} 算出来——**不发包、不占服务端 tick**，
 * 所有客户端看到同一帧（游戏时间本来就是服务端同步下来的）。
 *
 * <p>每个关键帧只在 {@code start}（相对动画起点的 tick）处给出"从这一刻开始生效"的值；
 * 与下一个关键帧之间按原版展示实体的插值语义线性/球面过渡。
 * 某个字段在关键帧里缺席时，沿用上一个关键帧的值；第一个关键帧缺席则沿用静态值。
 */
public final class DisplayAnimation {

    public static final String TAG_ENABLED = "animation_enabled";
    public static final String TAG_LENGTH = "animation_length";
    public static final String TAG_LOOP = "animation_loop";
    public static final String TAG_START = "animation_start";
    public static final String TAG_KEYFRAMES = "animation_keyframes";

    /** 关键帧自己的键；{@code transformation} 复用原版同名键。 */
    public static final String TAG_FRAME_START = "start";
    public static final String TAG_FRAME_OPACITY = "text_opacity";
    public static final String TAG_FRAME_BACKGROUND = "background";
    public static final String TAG_FRAME_TEXT = "text";

    /** 关键帧数量上限：既限制 NBT 体积（保存包有 32KB 上限），也限制每帧的采样开销。 */
    public static final int MAX_KEYFRAMES = 32;
    public static final int DEFAULT_LENGTH = 100;
    /** 一小时，够用且防止填出离谱的值。 */
    public static final int MAX_LENGTH = 72000;

    private DisplayAnimation() {
    }

    /** 一个关键帧：从 {@code start} tick 开始生效的一组值。 */
    public record Keyframe(int start, Transformation transformation, int textOpacity, int background,
            @Nullable String text) {
    }

    /**
     * 可复用的采样结果容器。渲染器每帧都要采样，用可变对象避免每帧每方块造一堆小对象。
     */
    public static final class Sampler {

        private Transformation transformation = Transformation.identity();
        private int textOpacity = TextDisplayBlockEntity.DEFAULT_TEXT_OPACITY;
        private int background = TextDisplayBlockEntity.DEFAULT_BACKGROUND;
        @Nullable
        private String text;
        private boolean valid;

        /**
         * 采样某一时刻的动画值。
         *
         * @param gameTime 当前游戏时间。循环动画取模定位；单次动画从 {@code start} 起算，播完停在最后一帧。
         * @return 是否采样成功（没有关键帧时不改动已有值并返回 false）
         */
        public boolean sample(List<Keyframe> frames, int length, boolean loop, long start, long gameTime) {
            if (frames.isEmpty()) {
                this.valid = false;
                return false;
            }
            int safeLength = Math.max(1, length);
            int local;
            if (loop) {
                local = (int) Math.floorMod(gameTime, (long) safeLength);
            } else {
                long elapsed = gameTime - start;
                if (elapsed < 0L) {
                    elapsed = 0L;
                }
                local = (int) Math.min(elapsed, (long) safeLength);
            }

            // 找"最后一个 start <= local"的关键帧；关键帧上限只有 32，线性扫足够便宜。
            int index = 0;
            for (int i = 1; i < frames.size(); i++) {
                if (frames.get(i).start() <= local) {
                    index = i;
                } else {
                    break;
                }
            }

            Keyframe current = frames.get(index);
            Keyframe next = index + 1 < frames.size() ? frames.get(index + 1) : null;
            float progress = 1.0F;
            if (next != null) {
                int span = next.start() - current.start();
                if (span > 0) {
                    progress = Mth.clamp((local - current.start()) / (float) span, 0.0F, 1.0F);
                }
            }

            // 停在关键帧上时直接复用它的 Transformation 对象，避免每帧 slerp 出新对象。
            this.transformation = next == null || progress >= 1.0F
                    ? current.transformation()
                    : current.transformation().slerp(next.transformation(), progress);
            this.textOpacity = next == null ? current.textOpacity()
                    : Mth.lerpInt(progress, current.textOpacity(), next.textOpacity());
            this.background = next == null ? current.background()
                    : FastColor.ARGB32.lerp(progress, current.background(), next.background());
            this.text = current.text();
            this.valid = true;
            return true;
        }

        public boolean valid() {
            return this.valid;
        }

        public void invalidate() {
            this.valid = false;
        }

        public Transformation transformation() {
            return this.transformation;
        }

        public int textOpacity() {
            return this.textOpacity;
        }

        public int background() {
            return this.background;
        }

        /** 关键帧里指定的文本；null 表示沿用静态文本。 */
        @Nullable
        public String text() {
            return this.text;
        }
    }

    public static boolean isEnabled(CompoundTag displayTag) {
        return DisplayBlockEntityBase.readBoolean(displayTag, TAG_ENABLED, false);
    }

    public static int length(CompoundTag displayTag) {
        return DisplayBlockEntityBase.readInt(displayTag, TAG_LENGTH, DEFAULT_LENGTH);
    }

    public static boolean isLooping(CompoundTag displayTag) {
        return DisplayBlockEntityBase.readBoolean(displayTag, TAG_LOOP, true);
    }

    public static boolean hasFrames(CompoundTag displayTag) {
        return displayTag.contains(TAG_KEYFRAMES, Tag.TAG_LIST)
                && !displayTag.getList(TAG_KEYFRAMES, Tag.TAG_COMPOUND).isEmpty();
    }

    /**
     * 读关键帧列表并按 start 排序。缺席字段在这里就解析成实际值（沿用上一个关键帧或静态值），
     * 这样采样时不用再做回退判断。结果只在数据版本变化时解析一次。
     */
    public static List<Keyframe> readKeyframes(CompoundTag displayTag) {
        List<Keyframe> frames = new ArrayList<>();
        if (!displayTag.contains(TAG_KEYFRAMES, Tag.TAG_LIST)) {
            return frames;
        }

        Transformation fallbackTransformation = DisplayBlockEntityBase.readTransformation(displayTag);
        int fallbackOpacity = TextDisplayBlockEntity.readOpacity(displayTag);
        int fallbackBackground = DisplayBlockEntityBase.readInt(displayTag, TextDisplayBlockEntity.TAG_BACKGROUND,
                TextDisplayBlockEntity.DEFAULT_BACKGROUND);

        ListTag list = displayTag.getList(TAG_KEYFRAMES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && frames.size() < MAX_KEYFRAMES; i++) {
            CompoundTag frameTag = list.getCompound(i);
            Transformation previousTransformation = frames.isEmpty() ? fallbackTransformation
                    : frames.get(frames.size() - 1).transformation();
            int previousOpacity = frames.isEmpty() ? fallbackOpacity : frames.get(frames.size() - 1).textOpacity();
            int previousBackground = frames.isEmpty() ? fallbackBackground
                    : frames.get(frames.size() - 1).background();

            frames.add(new Keyframe(
                    Math.max(0, frameTag.getInt(TAG_FRAME_START)),
                    frameTag.contains(DisplayBlockEntityBase.TAG_TRANSFORMATION)
                            ? DisplayBlockEntityBase.readTransformation(frameTag)
                            : previousTransformation,
                    frameTag.contains(TAG_FRAME_OPACITY, Tag.TAG_ANY_NUMERIC)
                            ? frameTag.getByte(TAG_FRAME_OPACITY)
                            : previousOpacity,
                    frameTag.contains(TAG_FRAME_BACKGROUND, Tag.TAG_ANY_NUMERIC)
                            ? frameTag.getInt(TAG_FRAME_BACKGROUND)
                            : previousBackground,
                    frameTag.contains(TAG_FRAME_TEXT, Tag.TAG_STRING) ? frameTag.getString(TAG_FRAME_TEXT) : null));
        }

        frames.sort(Comparator.comparingInt(Keyframe::start));
        return frames;
    }

    /** 把关键帧列表写回展示 NBT。 */
    public static void writeKeyframes(CompoundTag displayTag, List<Keyframe> frames) {
        ListTag list = new ListTag();
        int limit = Math.min(frames.size(), MAX_KEYFRAMES);
        for (int i = 0; i < limit; i++) {
            Keyframe frame = frames.get(i);
            CompoundTag frameTag = new CompoundTag();
            frameTag.putInt(TAG_FRAME_START, Math.max(0, frame.start()));
            DisplayBlockEntityBase.writeTransformation(frameTag, frame.transformation());
            frameTag.putByte(TAG_FRAME_OPACITY, (byte) frame.textOpacity());
            frameTag.putInt(TAG_FRAME_BACKGROUND, frame.background());
            if (frame.text() != null) {
                frameTag.putString(TAG_FRAME_TEXT, frame.text());
            }
            list.add(frameTag);
        }
        displayTag.put(TAG_KEYFRAMES, list);
    }

    /**
     * 校正动画字段：夹时长、排序并夹关键帧起点、截断过多的关键帧。
     */
    public static void sanitize(CompoundTag displayTag) {
        if (displayTag.contains(TAG_LENGTH, Tag.TAG_ANY_NUMERIC)) {
            displayTag.putInt(TAG_LENGTH,
                    Mth.clamp(displayTag.getInt(TAG_LENGTH), 1, MAX_LENGTH));
        }
        if (!displayTag.contains(TAG_KEYFRAMES, Tag.TAG_LIST)) {
            return;
        }
        ListTag list = displayTag.getList(TAG_KEYFRAMES, Tag.TAG_COMPOUND);
        while (list.size() > MAX_KEYFRAMES) {
            list.remove(list.size() - 1);
        }
        int length = length(displayTag);
        // 用解析后的列表重写一遍：顺手完成排序与起点夹取，并让缺席字段显式化。
        writeKeyframes(displayTag, readKeyframes(displayTag));
        ListTag rewritten = displayTag.getList(TAG_KEYFRAMES, Tag.TAG_COMPOUND);
        for (int i = 0; i < rewritten.size(); i++) {
            CompoundTag frameTag = rewritten.getCompound(i);
            frameTag.putInt(TAG_FRAME_START, Mth.clamp(frameTag.getInt(TAG_FRAME_START), 0, length));
        }
    }
}
