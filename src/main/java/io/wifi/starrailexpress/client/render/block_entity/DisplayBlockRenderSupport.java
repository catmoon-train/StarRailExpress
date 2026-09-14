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

package io.wifi.starrailexpress.client.render.block_entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Transformation;
import io.wifi.starrailexpress.content.block_entity.DisplayAnimation;
import io.wifi.starrailexpress.content.block_entity.DisplayBlockEntityBase;
import io.wifi.starrailexpress.content.block_entity.TextDisplayBlockEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

/**
 * 展示方块渲染的公共部分：把原版 {@code DisplayRenderer} 的朝向 / 变换矩阵 / 亮度 /
 * 文本绘制流程原样搬过来，只是把"实体"换成了"方块坐标"。
 *
 * <p>刻意不复用原版渲染器实例（要靠伪造成实体走 {@code EntityRenderDispatcher}，会依赖实体生命周期、
 * 渲染状态初始化和 dispatcher 的相机字段是否已就绪），改成直接复刻代码，行为一致但没有隐性依赖。
 * 文本绘制部分与原版 {@code DisplayRenderer.TextDisplayRenderer.renderInner} 逐行对应。
 */
public final class DisplayBlockRenderSupport {

    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);
    /** 原版文本行高：9 像素字高 + 1 像素行距。 */
    private static final int LINE_HEIGHT = 9 + 1;
    /** GUI 里画内容的满亮光照值，和原版物品模型用的那个一致。 */
    public static final int GUI_LIGHT = 15728880;
    /** 方块实体渲染器至少保留这么远的观察距离，避免 view_range 调得过小时直接消失。 */
    private static final double MIN_RENDER_DISTANCE = 64.0D;

    private DisplayBlockRenderSupport() {
    }

    /**
     * 取的方块实体自己的插值状态（渲染热路径，所以没有全局 Map、没有加锁、没有弱引用开销），
     * 状态跟着方块实体一起被回收。
     */
    public static InterpolationState stateFor(DisplayBlockEntityBase blockEntity) {
        Object existing = blockEntity.clientRenderState();
        if (existing instanceof InterpolationState state) {
            return state;
        }
        InterpolationState created = new InterpolationState();
        blockEntity.setClientRenderState(created);
        return created;
    }

    /**
     * 渲染距离：原版展示实体是 {@code view_range * 64}，这里也照抄，
     * 但夹到 [{@value #MIN_RENDER_DISTANCE}, {@value DisplayBlockEntityBase#MAX_RENDER_DISTANCE}] 之间。
     */
    public static double viewDistance(CompoundTag tag) {
        float range = DisplayBlockEntityBase.readFloat(tag, DisplayBlockEntityBase.TAG_VIEW_RANGE,
                DisplayBlockEntityBase.DEFAULT_VIEW_RANGE);
        return Mth.clamp((double) range * 64.0D, MIN_RENDER_DISTANCE, DisplayBlockEntityBase.MAX_RENDER_DISTANCE);
    }

    /**
     * 视野距离判定。刻意不用 {@code Vec3.atCenterOf}：那个每帧每个方块都会新造一个 Vec3，
     * 而这里只需要一次平方距离比较。
     */
    public static boolean isWithinViewDistance(BlockPos pos, Vec3 cameraPos, CompoundTag tag) {
        double range = viewDistance(tag);
        double dx = cameraPos.x - (pos.getX() + 0.5D);
        double dy = cameraPos.y - (pos.getY() + 0.5D);
        double dz = cameraPos.z - (pos.getZ() + 0.5D);
        return dx * dx + dy * dy + dz * dz < range * range;
    }
    // ───────────────────────── 朝向 / 亮度 / 发光 ─────────────────────────

    /**
     * 复刻原版 {@code DisplayRenderer.calculateOrientation}。
     * 方块没有自身朝向（yaw / pitch 恒为 0），所以 FIXED 就是单位四元数。
     *
     * <p>相机参数写成 yaw/pitch 而不是直接吃 {@link Camera}，是为了让 GUI 里的可旋转预览
     * 能用"等价相机朝向"驱动 billboard：预览传 {@code yaw = 180 - 环绕角}、{@code pitch = 俯仰角}，
     * 代入后正好抵消环绕旋转，于是 VERTICAL/CENTER 真的会面向观察者。
     */
    public static Quaternionf billboardRotation(Display.BillboardConstraints billboard, float cameraYaw,
            float cameraPitch, Quaternionf out) {
        // 原版用的是 camera.getYRot() - 180 与 -camera.getXRot()
        float yaw = cameraYaw - 180.0F;
        float pitch = -cameraPitch;
        return switch (billboard) {
            case FIXED -> out.rotationYXZ(0.0F, 0.0F, 0.0F);
            case VERTICAL -> out.rotationYXZ(DEG_TO_RAD * -yaw, 0.0F, 0.0F);
            case HORIZONTAL -> out.rotationYXZ(0.0F, DEG_TO_RAD * pitch, 0.0F);
            case CENTER -> out.rotationYXZ(DEG_TO_RAD * -yaw, DEG_TO_RAD * pitch, 0.0F);
        };
    }

    /** 世界里用：直接取当前相机。 */
    public static void applyDisplayTransform(PoseStack poseStack, Display.BillboardConstraints billboard,
            Transformation transformation, float cameraYaw, float cameraPitch) {
        poseStack.mulPose(billboardRotation(billboard, cameraYaw, cameraPitch, new Quaternionf()));
        poseStack.mulPose(transformation.getMatrix());
    }

    /** 世界里用：相机取自当前渲染相机。 */
    public static void applyDisplayTransform(PoseStack poseStack, Display.BillboardConstraints billboard,
            Transformation transformation) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        applyDisplayTransform(poseStack, billboard, transformation, camera.getYRot(), camera.getXRot());
    }

    /**
     * 把 pose 原点从方块最小角挪到方块中心。
     *
     * <p>方块实体渲染器的 pose 原点在方块的最小角上（{@code LevelRenderer} 只做了
     * {@code translate(pos.getX(), pos.getY(), pos.getZ())}），而原版展示实体的原点在实体脚下、
     * 也就是内容自己的锚点。不补这一步的话，文本会以方块角为中心、贴在北面的平面上，
     * 旋转/缩放也会绕方块角而不是绕方块转。
     */
    public static void moveToBlockCenter(PoseStack poseStack) {
        poseStack.translate(0.5D, 0.5D, 0.5D);
    }

    /**
     * 方块模型在模型空间里占 (0,0,0)-(1,1,1)，是以最小角为基准的。
     * 绕方块中心做完变换后要抵消掉 {@link #moveToBlockCenter}，模型才会正好落在方块自己身上。
     */
    public static void restoreModelOrigin(PoseStack poseStack) {
        poseStack.translate(-0.5D, -0.5D, -0.5D);
    }

    /** brightness 覆盖了就用覆盖值，否则用方块位置的光照。 */
    public static int resolveLight(int packedBrightnessOverride, int packedLight) {
        return packedBrightnessOverride != DisplayBlockEntityBase.NO_BRIGHTNESS_OVERRIDE ? packedBrightnessOverride
                : packedLight;
    }

    /**
     * glow_color_override 生效时把绘制目标换到轮廓缓冲上（和原版实体发光同一套机制）。
     * 不需要发光时返回 null，调用方自己决定用不用它——这样每帧不会为了"没发光"造临时对象。
     */
    @Nullable
    public static OutlineBufferSource beginGlow(int glowColor) {
        if (glowColor == DisplayBlockEntityBase.NO_GLOW_COLOR_OVERRIDE) {
            return null;
        }
        OutlineBufferSource outline = Minecraft.getInstance().renderBuffers().outlineBufferSource();
        outline.setColor(FastColor.ARGB32.red(glowColor), FastColor.ARGB32.green(glowColor),
                FastColor.ARGB32.blue(glowColor), 255);
        return outline;
    }

    public static MultiBufferSource glowSource(@Nullable OutlineBufferSource outline, MultiBufferSource fallback) {
        return outline != null ? outline : fallback;
    }

    /** 和 {@link #beginGlow} 配对，把轮廓批次刷出去。 */
    public static void endGlow(@Nullable OutlineBufferSource outline) {
        if (outline != null) {
            outline.endOutlineBatch();
        }
    }

    // ───────────────────────── 文本绘制 ─────────────────────────

    /**
     * 与原版 {@code DisplayRenderer.TextDisplayRenderer.renderInner} 一致：
     * 绕 Y 轴翻转 180°、缩放 -0.025、铺背景板，再逐行 {@code Font.drawInBatch}。
     *
     * <p>唯一与原版不同的地方是竖直锚点：原版把文本块的**底边**放在锚点上（所以文本整体在锚点上方），
     * 这里改成让文本块**中心**落在锚点上，配合调用方把锚点放在方块中心，文本才会正好在方块中央。
     *
     * @param cachedInfo  已经按 line_width 分好行的文本
     * @param textOpacity 文本不透明度（-1 表示不透明）
     * @param backgroundColor 背景色（ARGB），default_background 打开时会被原版默认背景覆盖
     * @param flags       原版 {@code Display.TextDisplay} 的 flags 字节
     */
    public static void renderText(Display.TextDisplay.CachedInfo cachedInfo, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight, int textOpacity, int backgroundColor, byte flags) {
        Font font = Minecraft.getInstance().font;
        boolean seeThrough = (flags & Display.TextDisplay.FLAG_SEE_THROUGH) != 0;
        boolean defaultBackground = (flags & Display.TextDisplay.FLAG_USE_DEFAULT_BACKGROUND) != 0;
        boolean shadow = (flags & Display.TextDisplay.FLAG_SHADOW) != 0;
        Display.TextDisplay.Align align = Display.TextDisplay.getAlign(flags);
        byte opacity = (byte) textOpacity;

        int background;
        if (defaultBackground) {
            float f = Minecraft.getInstance().options.getBackgroundOpacity(0.25F);
            background = (int) (f * 255.0F) << 24;
        } else {
            background = backgroundColor;
        }

        float y = 0.0F;
        Matrix4f matrix = poseStack.last().pose();
        matrix.rotate((float) Math.PI, 0.0F, 1.0F, 0.0F);
        matrix.scale(-0.025F, -0.025F, -0.025F);

        int width = cachedInfo.width();
        int height = cachedInfo.lines().size() * LINE_HEIGHT;
        // 竖直居中（原版是 -height，把底边贴在锚点上）。
        // 注意：这只是挪动整套内容的锚点，背景板顶点的坐标仍然和字形用同一套字体空间（[-1, height]），
        // 所以两者会一起平移、保持对齐；给背景板单独加偏移就会和文字错开。
        matrix.translate(1.0F - width / 2.0F, -height / 2.0F, 0.0F);

        if (background != 0) {
            VertexConsumer consumer = buffer
                    .getBuffer(seeThrough ? RenderType.textBackgroundSeeThrough() : RenderType.textBackground());
            consumer.addVertex(matrix, -1.0F, -1.0F, 0.0F).setColor(background).setLight(packedLight);
            consumer.addVertex(matrix, -1.0F, height, 0.0F).setColor(background).setLight(packedLight);
            consumer.addVertex(matrix, width, height, 0.0F).setColor(background).setLight(packedLight);
            consumer.addVertex(matrix, width, -1.0F, 0.0F).setColor(background).setLight(packedLight);
        }

        for (Display.TextDisplay.CachedLine line : cachedInfo.lines()) {
            float x = switch (align) {
                case LEFT -> 0.0F;
                case RIGHT -> width - line.width();
                case CENTER -> width / 2.0F - line.width() / 2.0F;
            };
            font.drawInBatch(line.contents(), x, y, opacity << 24 | 0xFFFFFF, shadow, matrix, buffer,
                    seeThrough ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.POLYGON_OFFSET, 0, packedLight);
            y += LINE_HEIGHT;
        }
    }

    /** 按行宽切分文本，等价于原版 {@code TextDisplayRenderer.splitLines}。 */
    public static Display.TextDisplay.CachedInfo splitLines(Component text, int lineWidth) {
        Font font = Minecraft.getInstance().font;
        List<FormattedCharSequence> split = font.split(text, lineWidth);
        List<Display.TextDisplay.CachedLine> lines = new ArrayList<>(split.size());
        int width = 0;
        for (FormattedCharSequence sequence : split) {
            int lineWidthPx = font.width(sequence);
            width = Math.max(width, lineWidthPx);
            lines.add(new Display.TextDisplay.CachedLine(sequence, lineWidthPx));
        }
        return new Display.TextDisplay.CachedInfo(lines, width);
    }

    public static Display.TextDisplay.CachedInfo emptyTextInfo() {
        return new Display.TextDisplay.CachedInfo(List.of(), 0);
    }

    // ───────────────────────── 插值状态 ─────────────────────────

    /**
     * 每个方块实体一份的客户端插值状态，语义对齐原版 {@code Display}：
     * 数据变化时如果 {@code interpolation_duration} 大于 0，就从"上一次显示的值"线性/球面插值到新值。
     */
    public static final class InterpolationState {

        private int revision = Integer.MIN_VALUE;
        private Transformation fromTransformation = Transformation.identity();
        private Transformation toTransformation = Transformation.identity();
        private float fromShadowRadius = DisplayBlockEntityBase.DEFAULT_SHADOW_RADIUS;
        private float toShadowRadius = DisplayBlockEntityBase.DEFAULT_SHADOW_RADIUS;
        private float fromShadowStrength = DisplayBlockEntityBase.DEFAULT_SHADOW_STRENGTH;
        private float toShadowStrength = DisplayBlockEntityBase.DEFAULT_SHADOW_STRENGTH;
        private int fromTextOpacity = TextDisplayBlockEntity.DEFAULT_TEXT_OPACITY;
        private int toTextOpacity = TextDisplayBlockEntity.DEFAULT_TEXT_OPACITY;
        private int fromBackground = TextDisplayBlockEntity.DEFAULT_BACKGROUND;
        private int toBackground = TextDisplayBlockEntity.DEFAULT_BACKGROUND;
        private int duration;
        private long startTick = Long.MIN_VALUE;
        private float progress = 1.0F;

        // 本地关键帧动画：只在数据变化时解析关键帧，每帧只做一次采样（采样器可复用，不产生垃圾）
        private final DisplayAnimation.Sampler animationSampler = new DisplayAnimation.Sampler();
        private List<DisplayAnimation.Keyframe> keyframes = List.of();
        private int animationLength = DisplayAnimation.DEFAULT_LENGTH;
        private boolean animationLoop = true;
        private long animationStart;
        private boolean animationEnabled;

        // 每帧都要用的解析结果，只在数据变化时重新算一遍（编解码器不便宜）
        private Display.BillboardConstraints billboard = Display.BillboardConstraints.FIXED;
        private int packedBrightnessOverride = DisplayBlockEntityBase.NO_BRIGHTNESS_OVERRIDE;
        private int glowColor = DisplayBlockEntityBase.NO_GLOW_COLOR_OVERRIDE;
        private byte textFlags;

        private int cachedTextRevision = Integer.MIN_VALUE;
        private int cachedLineWidth = Integer.MIN_VALUE;
        private String cachedTextJson = "";
        private Display.TextDisplay.CachedInfo cachedTextInfo = emptyTextInfo();

        /** 每帧渲染前调用一次：拉取新数据、决定是否开始一段新插值，并推进进度。 */
        public void update(CompoundTag tag, int dataRevision, long gameTime, float partialTick) {
            if (dataRevision != this.revision) {
                boolean hadPrevious = this.revision != Integer.MIN_VALUE;
                Transformation newTransformation = DisplayBlockEntityBase.readTransformation(tag);
                float newShadowRadius = DisplayBlockEntityBase.readFloat(tag,
                        DisplayBlockEntityBase.TAG_SHADOW_RADIUS, DisplayBlockEntityBase.DEFAULT_SHADOW_RADIUS);
                float newShadowStrength = DisplayBlockEntityBase.readFloat(tag,
                        DisplayBlockEntityBase.TAG_SHADOW_STRENGTH, DisplayBlockEntityBase.DEFAULT_SHADOW_STRENGTH);
                int newTextOpacity = TextDisplayBlockEntity.readOpacity(tag);
                int newBackground = DisplayBlockEntityBase.readInt(tag, TextDisplayBlockEntity.TAG_BACKGROUND,
                        TextDisplayBlockEntity.DEFAULT_BACKGROUND);
                int newDuration = DisplayBlockEntityBase.readInt(tag,
                        DisplayBlockEntityBase.TAG_INTERPOLATION_DURATION, 0);
                this.billboard = DisplayBlockEntityBase.readBillboard(tag);
                this.packedBrightnessOverride = DisplayBlockEntityBase.readPackedBrightness(tag);
                this.glowColor = DisplayBlockEntityBase.readInt(tag,
                        DisplayBlockEntityBase.TAG_GLOW_COLOR_OVERRIDE,
                        DisplayBlockEntityBase.NO_GLOW_COLOR_OVERRIDE);
                this.textFlags = TextDisplayBlockEntity.readFlags(tag);
                // 关键帧只在数据变化时解析一次
                this.animationEnabled = DisplayAnimation.isEnabled(tag);
                this.keyframes = this.animationEnabled ? DisplayAnimation.readKeyframes(tag) : List.of();
                this.animationLength = DisplayAnimation.length(tag);
                this.animationLoop = DisplayAnimation.isLooping(tag);
                this.animationStart = DisplayBlockEntityBase.readLong(tag, DisplayAnimation.TAG_START, 0L);

                if (hadPrevious && newDuration > 0) {
                    // 从"当前显示值"继续插值，和原版 createInterpolatedRenderState 一致。
                    this.fromTransformation = currentTransformation();
                    this.fromShadowRadius = currentShadowRadius();
                    this.fromShadowStrength = currentShadowStrength();
                    this.fromTextOpacity = currentTextOpacity();
                    this.fromBackground = currentBackground();
                    this.startTick = gameTime + DisplayBlockEntityBase.readInt(tag,
                            DisplayBlockEntityBase.TAG_START_INTERPOLATION, 0);
                    this.progress = 0.0F;
                } else {
                    this.fromTransformation = newTransformation;
                    this.fromShadowRadius = newShadowRadius;
                    this.fromShadowStrength = newShadowStrength;
                    this.fromTextOpacity = newTextOpacity;
                    this.fromBackground = newBackground;
                    this.progress = 1.0F;
                }

                this.toTransformation = newTransformation;
                this.toShadowRadius = newShadowRadius;
                this.toShadowStrength = newShadowStrength;
                this.toTextOpacity = newTextOpacity;
                this.toBackground = newBackground;
                this.duration = newDuration;
                this.revision = dataRevision;
                this.cachedTextRevision = Integer.MIN_VALUE;
            }

            if (this.duration > 0 && this.progress < 1.0F) {
                float elapsed = (gameTime - this.startTick) + partialTick;
                this.progress = Mth.clamp(Mth.inverseLerp(elapsed, 0.0F, this.duration), 0.0F, 1.0F);
            }

            // 启用动画时用采样结果覆盖静态值（采样器复用，只有真正过渡时才 slerp 出新对象）
            if (this.animationEnabled && !this.keyframes.isEmpty()) {
                this.animationSampler.sample(this.keyframes, this.animationLength, this.animationLoop,
                        this.animationStart, gameTime);
            } else {
                this.animationSampler.invalidate();
            }
        }

        public Display.BillboardConstraints billboard() {
            return this.billboard;
        }

        public int packedBrightnessOverride() {
            return this.packedBrightnessOverride;
        }

        public int glowColor() {
            return this.glowColor;
        }

        public byte textFlags() {
            return this.textFlags;
        }

        public Transformation currentTransformation() {
            if (this.animationSampler.valid()) {
                return this.animationSampler.transformation();
            }
            return this.progress >= 1.0F
                    ? this.toTransformation
                    : this.fromTransformation.slerp(this.toTransformation, this.progress);
        }

        public float currentShadowRadius() {
            return Mth.lerp(this.progress, this.fromShadowRadius, this.toShadowRadius);
        }

        public float currentShadowStrength() {
            return Mth.lerp(this.progress, this.fromShadowStrength, this.toShadowStrength);
        }

        public int currentTextOpacity() {
            if (this.animationSampler.valid()) {
                return this.animationSampler.textOpacity();
            }
            return Mth.lerpInt(this.progress, this.fromTextOpacity, this.toTextOpacity);
        }

        public int currentBackground() {
            if (this.animationSampler.valid()) {
                return this.animationSampler.background();
            }
            return FastColor.ARGB32.lerp(this.progress, this.fromBackground, this.toBackground);
        }

        /** 关键帧里带的文本；null 表示这一帧没有覆盖文本，用静态 text 键。 */
        @Nullable
        public String animationText() {
            return this.animationSampler.valid() ? this.animationSampler.text() : null;
        }

        public boolean animationActive() {
            return this.animationSampler.valid();
        }

        /**
         * 文本分行结果按 数据版本 + 行宽 + 文本内容 缓存。
         * 文本内容也要进键：关键帧可以逐帧换文本，光看版本号是认不出来的。
         */
        public Display.TextDisplay.CachedInfo textInfo(TextDisplayBlockEntity blockEntity,
                HolderLookup.Provider registries) {
            int lineWidth = blockEntity.getLineWidth();
            String textJson = animationText();
            if (textJson == null) {
                textJson = blockEntity.getTextJson();
            }
            if (this.cachedTextRevision == this.revision && this.cachedLineWidth == lineWidth
                    && textJson.equals(this.cachedTextJson)) {
                return this.cachedTextInfo;
            }
            Component text = TextDisplayBlockEntity.parseText(textJson, registries);
            this.cachedTextInfo = text == null ? emptyTextInfo() : splitLines(text, Math.max(1, lineWidth));
            this.cachedLineWidth = lineWidth;
            this.cachedTextJson = textJson;
            this.cachedTextRevision = this.revision;
            return this.cachedTextInfo;
        }
    }
}
