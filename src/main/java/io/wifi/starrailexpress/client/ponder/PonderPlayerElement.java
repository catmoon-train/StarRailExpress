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

package io.wifi.starrailexpress.client.ponder;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.AnimatedSceneElementBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

/**
 * 思索场景里的玩家模型：可指定皮肤贴图，并绘制本能透视边框。
 *
 * <p>皮肤覆盖通过 {@link PonderSkinAccess} 登记，并在
 * {@code PlayerEntityRendererMixin#getTextureLocation} 的最前面生效，
 * 因此优先于职业皮肤与疯魔皮肤逻辑，不会被它们改写。
 *
 * <p>边框按玩家模型本体渲染：把同一套模型顶点外扩成纯色描边（正面剔除，内圈被模型本体挡掉），
 * 形状跟着皮肤走，颜色语义与游戏内本能透视完全一致。
 */
public class PonderPlayerElement extends AnimatedSceneElementBase {

    private final Vec3 location;
    private float bodyYaw = 180.0F;
    private float headYaw = 180.0F;
    private float pitch = 0.0F;
    /** 模型姿态：站立、蹲下、趴下等，决定模型是站着、蹲着还是趴着。 */
    private Pose pose = Pose.STANDING;
    /** 模型缩放：兔兔 / 侏儒这类「小人」被抱到头顶时需要缩小。 */
    private float scale = 1.0F;

    @Nullable
    private GameProfile profile;
    @Nullable
    private RemotePlayer puppet;

    @Nullable
    private ResourceLocation skin;
    @Nullable
    private ItemStack heldItem;
    private int outlineColor = PonderPlayerPreset.NO_OUTLINE;
    private float outlineLineWidth = 1.0F;
    private float outlineExpand = 0.12F;

    /** 动画位移：走动与传送都写在这里，相对 {@link #location}，场景重播时归零。 */
    private Vec3 offset = Vec3.ZERO;
    /** 走动起点偏移；{@code walkDelta == null} 表示当前没有走动动画。 */
    private Vec3 walkBase = Vec3.ZERO;
    @Nullable
    private Vec3 walkDelta;
    private int walkTicks;
    /** 击退模式：用快速外抛 + 回落代替平滑走动，模拟被冲击波推开。 */
    private boolean knockbackMode = false;
    private float knockbackArc = 0.45F;

    /** 所属场景的 Outliner，在加入场景时绑定，用于绘制与保活描边。 */
    @Nullable
    private Outliner outliner;

    public PonderPlayerElement(Vec3 location) {
        this.location = location;
    }

    public PonderPlayerElement profile(@Nullable GameProfile profile) {
        this.profile = profile;
        return this;
    }

    /** 指定渲染用皮肤贴图；{@code null} 表示使用身份皮肤。 */
    public PonderPlayerElement skin(@Nullable ResourceLocation skin) {
        this.skin = skin;
        registerSkinOverride();
        return this;
    }

    /** 设置模型主手物品；{@code null} 表示空手。 */
    public PonderPlayerElement heldItem(@Nullable ItemStack heldItem) {
        this.heldItem = heldItem == null || heldItem.isEmpty() ? null : heldItem.copy();
        applyHeldItem();
        return this;
    }

    /** 配置本能透视边框；{@code argb == NO_OUTLINE} 表示不绘制。 */
    public PonderPlayerElement outline(int argb, float lineWidth, float expand) {
        this.outlineColor = argb;
        this.outlineLineWidth = Math.max(0.1F, lineWidth);
        this.outlineExpand = Math.max(0.0F, expand);
        return this;
    }

    public PonderPlayerElement facing(float bodyYaw, float headYaw, float pitch) {
        this.bodyYaw = bodyYaw;
        this.headYaw = headYaw;
        this.pitch = pitch;
        applyFacingToPuppet();
        return this;
    }

    /** 设置模型姿态（站立 / 蹲下 / 趴下 / 睡眠等）；{@code null} 按站立处理。 */
    public PonderPlayerElement pose(@Nullable Pose pose) {
        this.pose = pose == null ? Pose.STANDING : pose;
        applyPoseToPuppet();
        return this;
    }

    /** 设置模型缩放；{@code <= 0} 视为 1，避免把顶点矩阵压成零。 */
    public PonderPlayerElement scale(float scale) {
        this.scale = scale > 0.0F ? scale : 1.0F;
        return this;
    }

    /** 绑定场景 Outliner；由 {@link PonderPlayerPreset} 在加入场景时调用。 */
    public PonderPlayerElement bindOutliner(@Nullable Outliner outliner) {
        this.outliner = outliner;
        return this;
    }

    /** 当前是否绘制边框。 */
    public boolean hasOutline() {
        return outlineColor != PonderPlayerPreset.NO_OUTLINE;
    }

    /** 是否正在播放走动动画。 */
    public boolean isWalking() {
        return walkDelta != null;
    }

    /** 当前所在位置（含走动 / 传送位移）。 */
    public Vec3 currentLocation() {
        return location.add(offset);
    }

    /**
     * 在场景里找玩家模型（场景通常只有一个，取最后一个）。
     *
     * <p>供走动 / 传送步骤在没有拿到元素实例时兜底：宁可运行时在场景里找一遍，
     * 也不要因为跨步骤引用掉了就静默不播动画。
     */
    @Nullable
    public static PonderPlayerElement findInScene(PonderScene scene) {
        final PonderPlayerElement[] found = new PonderPlayerElement[1];
        scene.forEach(PonderPlayerElement.class, element -> found[0] = element);
        return found[0];
    }

    /**
     * 起步走向绝对坐标 {@code target}，总时长 {@code ticks}。
     *
     * <p>进度由 {@link PonderWalkInstruction} 逐 tick 通过 {@link #setWalkProgress(float)} 推进，
     * 不依赖元素自己的 tick，这样走动一定跟着时间轴走。
     */
    public void beginWalk(Vec3 target, int ticks) {
        // 上一段走动若被中断，腿可能停在半个步幅上；起步前先归零。
        if (puppet != null) {
            resetWalkAnimation();
        }
        this.walkBase = offset;
        this.walkDelta = target.subtract(location).subtract(offset);
        this.walkTicks = Math.max(1, ticks);
    }

    /**
     * 以击退方式起步到绝对坐标 {@code target}：位移先快后慢，并沿 Y 画一道抛物线，
     * 表现玩家被冲击波 / 爆炸推开、短暂腾空的效果。
     */
    public void beginKnockback(Vec3 target, int ticks, float arc) {
        if (puppet != null) {
            resetWalkAnimation();
        }
        this.walkBase = offset;
        this.walkDelta = target.subtract(location).subtract(offset);
        this.walkTicks = Math.max(1, ticks);
        this.knockbackMode = true;
        this.knockbackArc = Math.max(0.0F, arc);
    }

    /** 推进走动进度（0~1）；结束时把迈腿幅度归零，模型回到站立姿态。 */
    public void setWalkProgress(float progress) {
        if (walkDelta == null) {
            return;
        }
        float clamped = Mth.clamp(progress, 0.0F, 1.0F);
        if (knockbackMode) {
            // 1-(1-t)^2：被击飞的瞬间最快，随后减速落地。
            float eased = 1.0F - (1.0F - clamped) * (1.0F - clamped);
            offset = walkBase.add(walkDelta.scale(eased));
            offset = offset.add(0.0, knockbackArc * 4.0F * clamped * (1.0F - clamped), 0.0);
        } else {
            // 平滑起步与收尾，避免模型像被线性拖走。
            float eased = clamped * clamped * (3.0F - 2.0F * clamped);
            offset = walkBase.add(walkDelta.scale(eased));
        }
        if (puppet != null) {
            if (clamped >= 1.0F) {
                // 只 setSpeed(0) 会把 walkAnimation.pos 留在半个步幅上，
                // 玩家停在终点后腿会僵在一个奇怪角度（看到的就是「鬼畜」）。
                // 这里借 mixin accessors 同时清掉 pos 与 speed。
                resetWalkAnimation();
            } else if (!knockbackMode) {
                puppet.walkAnimation.update(1.0F, 1.0F);
                puppet.walkAnimation.setSpeed(1.0F);
            }
        }
        if (clamped >= 1.0F) {
            offset = walkBase.add(walkDelta);
            resetWalk();
        }
    }

    /** 直接落位到绝对坐标 {@code target}（传送），并中断未完成的走动。 */
    public void teleportTo(Vec3 target) {
        this.offset = target.subtract(location);
        resetWalk();
    }

    private void resetWalk() {
        this.walkBase = Vec3.ZERO;
        this.walkDelta = null;
        this.walkTicks = 0;
        this.knockbackMode = false;
    }

    /** 清零步幅与速度；思索场景不跑原版 tick，必须显式做这件事。 */
    private void resetWalkAnimation() {
        if (puppet == null) {
            return;
        }
        // WalkAnimationState 的 speed/speedOld/position 都是 public 字段（1.21.1 映射）。
        // update(-position, 1) 在某些 tick 下会被下一帧插值带回去，必须直接清零三个字段，
        // 否则玩家停在终点后腿部仍卡在半步姿态。
        puppet.walkAnimation.speedOld = 0.0F;
        puppet.walkAnimation.speed = 0.0F;
        puppet.walkAnimation.position = 0.0F;
    }

    private void registerSkinOverride() {
        if (puppet != null && skin != null) {
            PonderSkinAccess.override(puppet, skin);
        }
    }

    /**
     * 把物品挂到模型主手，交给原版 {@code ItemInHandLayer} 渲染：
     * 手臂姿势、第三人称握持变换、弓/三叉戟之类的双手姿势都由原版逻辑决定，
     * 不要再用 {@code ItemRenderer#renderStatic} 自己摆位置。
     */
    private void applyHeldItem() {
        if (puppet == null) {
            return;
        }
        puppet.setItemSlot(EquipmentSlot.MAINHAND, heldItem == null ? ItemStack.EMPTY : heldItem);
    }

    private void applyFacingToPuppet() {
        if (puppet == null) {
            return;
        }
        puppet.setYRot(bodyYaw);
        puppet.yRotO = bodyYaw;
        puppet.setYBodyRot(bodyYaw);
        puppet.yBodyRotO = bodyYaw;
        puppet.yHeadRot = headYaw;
        puppet.yHeadRotO = headYaw;
        puppet.setXRot(pitch);
        puppet.xRotO = pitch;
    }

    /**
     * 把姿态同步给模型。
     *
     * <p>蹲下用 {@link Pose#CROUCHING}（配合 shift 状态），趴下用 {@link Pose#SWIMMING}
     * ——玩家趴下时原版就是把姿态切成 SWIMMING 再渲染，这里沿用同一套。
     */
    private void applyPoseToPuppet() {
        if (puppet == null) {
            return;
        }
        puppet.setPose(pose);
        puppet.setShiftKeyDown(pose == Pose.CROUCHING);
        puppet.setSwimming(pose == Pose.SWIMMING);
    }

    private void ensurePuppet(PonderLevel world) {
        if (puppet != null) {
            return;
        }
        puppet = PonderPlayerFactory.createPlayer(world, profile);
        if (puppet == null) {
            return;
        }
        resetPuppetPose();
        applyFacingToPuppet();
        applyPoseToPuppet();
        registerSkinOverride();
        applyHeldItem();
    }

    private void resetPuppetPose() {
        puppet.tickCount = 0;
        puppet.setPosRaw(0, 0, 0);
        puppet.xo = 0;
        puppet.yo = 0;
        puppet.zo = 0;
        puppet.xOld = 0;
        puppet.yOld = 0;
        puppet.zOld = 0;
        puppet.walkAnimation.setSpeed(0);
    }

    @Override
    public void reset(PonderScene scene) {
        super.reset(scene);
        // 重播必须回到初始站位，否则第二遍起模型会停在上一遍的终点。
        this.offset = Vec3.ZERO;
        resetWalk();
        if (puppet == null) {
            return;
        }
        resetPuppetPose();
        applyFacingToPuppet();
        applyPoseToPuppet();
        registerSkinOverride();
        applyHeldItem();
    }

    @Override
    public void tick(PonderScene scene) {
        super.tick(scene);
        if (puppet == null) {
            return;
        }
        puppet.tickCount++;
        puppet.xo = puppet.getX();
        puppet.yo = puppet.getY();
        puppet.zo = puppet.getZ();
        puppet.xOld = puppet.getX();
        puppet.yOld = puppet.getY();
        puppet.zOld = puppet.getZ();
        puppet.yRotO = puppet.getYRot();
        puppet.xRotO = puppet.getXRot();
        puppet.yBodyRotO = puppet.yBodyRot;
        puppet.yHeadRotO = puppet.yHeadRot;
        puppet.oAttackAnim = puppet.attackAnim;
        // Outliner 条目默认 1 tick 后开始淡出，必须每 tick 保活。
        if (outliner != null && hasOutline() && isVisible()) {
            outliner.keep(this);
        }
    }

    @Override
    protected void renderLast(PonderLevel world, MultiBufferSource buffer, GuiGraphics graphics, float fade, float pt) {
        ensurePuppet(world);
        if (puppet == null) {
            return;
        }

        var poseStack = graphics.pose();
        Vec3 pos = currentLocation();
        poseStack.pushPose();
        poseStack.translate(pos.x, pos.y, pos.z);
        poseStack.translate(Mth.lerp(pt, puppet.xo, puppet.getX()),
                Mth.lerp(pt, puppet.yo, puppet.getY()),
                Mth.lerp(pt, puppet.zo, puppet.getZ()));
        if (scale != 1.0F) {
            poseStack.scale(scale, scale, scale);
        }

        int light = lightCoordsFromFade(fade);
        // 手上物品由原版 ItemInHandLayer 随模型一起画，这里不需要单独摆位置。
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.render(puppet, 0, 0, 0, 0, pt, poseStack, buffer, light);
        poseStack.popPose();

        renderModelOutline(buffer, graphics, fade, pt);
    }

    /**
     * 原版只有「开/关剔除」这一个开关，没有「剔除正面」，所以借 layering 分片的自定义
     * setup 把剔除面翻成 {@code GL_FRONT}。分片按 cull → layering 的顺序 setup，
     * 它会叠加在 {@link RenderStateShard#CULL} 之后；clear 顺序相反，能正确还原成 {@code GL_BACK}。
     */
    private static final RenderStateShard.LayeringStateShard OUTLINE_CULL_FRONT =
            new RenderStateShard.LayeringStateShard("sre_ponder_outline_cull_front", () -> {
                GlStateManager._enableCull();
                GL11.glCullFace(GL11.GL_FRONT);
            }, () -> {
                GL11.glCullFace(GL11.GL_BACK);
                GlStateManager._disableCull();
            });

    /**
     * 思索场景专用的轮廓层：只画模型背面、纯色、只写颜色不写深度。
     *
     * <p>正面被剔除后，外扩过的背面投影出来就是比模型大一圈的外壳；模型本体在 default
     * 阶段已经画好并写了深度，外壳的内圈会被深度测试挡掉，屏幕上只剩下一圈边。
     *
     * <p>不能用原版的 {@code OutlineBufferSource}：它产出的轮廓层是
     * {@code RenderType.outline(...)}，输出目标是 {@code LevelRenderer.entityTarget()}
     * 这个离屏缓冲，只有世界渲染末尾的 {@code LevelRenderer#doEntityOutline()} 才会把它叠回屏幕。
     * 思索是 GUI 场景，那一步永远不会执行，轮廓只会被写进一个没人读的缓冲里。
     */
    private static final RenderType OUTLINE_GLOW = RenderType.create(
            "sre_ponder_player_outline",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTextureState(RenderStateShard.NO_TEXTURE)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setCullState(RenderStateShard.CULL)
                    .setLayeringState(OUTLINE_CULL_FRONT)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    /**
     * 把玩家模型再画一遍当作描边：顶点强制成轮廓色、沿法线外扩一圈，形状跟着模型走，
     * 不是包住整个人的矩形盒子。
     *
     * <p>几何写进 {@link SuperRenderTypeBuffer#getLateBuffer} —— catnip 按
     * early → default → late 的顺序冲刷，模型本体和场景方块都在 default 里，
     * 轮廓必须最后画：既要用它们的深度挡掉内圈，也要能被挡在玩家前面的方块遮住。
     */
    private void renderModelOutline(MultiBufferSource buffer, GuiGraphics graphics, float fade, float pt) {
        if (!hasOutline() || puppet == null) {
            return;
        }
        VertexConsumer consumer = buffer instanceof SuperRenderTypeBuffer superBuffer
                ? superBuffer.getLateBuffer(OUTLINE_GLOW)
                : buffer.getBuffer(OUTLINE_GLOW);
        OutlineGlowVertexConsumer glow = new OutlineGlowVertexConsumer(consumer,
                withAlpha(outlineColor, fade), outlineOffset());
        MultiBufferSource glowBuffer = renderType -> glow;

        var poseStack = graphics.pose();
        Vec3 pos = currentLocation();
        poseStack.pushPose();
        poseStack.translate(pos.x, pos.y, pos.z);
        poseStack.translate(Mth.lerp(pt, puppet.xo, puppet.getX()),
                Mth.lerp(pt, puppet.yo, puppet.getY()),
                Mth.lerp(pt, puppet.zo, puppet.getZ()));
        if (scale != 1.0F) {
            poseStack.scale(scale, scale, scale);
        }
        // 只给人物描边：物品模型多是薄片/小方盒，按背面壳扩出来会变成一块色块，
        // 临时摘掉主手物品，画完再放回去。
        ItemStack mainHand = puppet.getMainHandItem();
        puppet.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        Minecraft.getInstance().getEntityRenderDispatcher().render(puppet, 0, 0, 0, 0, pt,
                poseStack, glowBuffer, lightCoordsFromFade(fade));
        puppet.setItemSlot(EquipmentSlot.MAINHAND, mainHand);
        poseStack.popPose();
        glow.finish();
    }

    /** 轮廓外扩量（格）：沿用 {@code outlineExpand} 的语义，线宽参与缩放。 */
    private float outlineOffset() {
        return Math.max(0.9F, outlineExpand * 0.9F * outlineLineWidth);
    }

    /** InstinctManager 的颜色通常不带 alpha，这里补成不透明并按淡出缩放。 */
    private static int withAlpha(int argb, float fade) {
        int alpha = (argb >>> 24) & 0xFF;
        if (alpha == 0) {
            alpha = 255;
        }
        int scaled = Mth.clamp(Math.round(alpha * fade), 0, 255);
        return (scaled << 24) | (argb & 0xFFFFFF);
    }

    /**
     * 把模型顶点流改写成纯色轮廓：贴图、光照、叠加层全部丢掉，颜色固定，
     * 顶点沿法线外扩 {@code offset} 格，让描边比模型本体粗出一圈。
     *
     * <p>法线是在顶点之后才写进来的，所以顶点要延迟到下一次 {@code addVertex} 才提交；
     * 模型收尾后再调一次 {@link #finish()} 把最后一个顶点补上。
     */
    private static final class OutlineGlowVertexConsumer implements VertexConsumer {

        private final VertexConsumer delegate;
        private final int color;
        private final float offset;

        private float x;
        private float y;
        private float z;
        private float normalX;
        private float normalY;
        private float normalZ;
        private boolean pending;

        private OutlineGlowVertexConsumer(VertexConsumer delegate, int color, float offset) {
            this.delegate = delegate;
            this.color = color;
            this.offset = offset;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            flush();
            // 有的层不写法线，这时保持零法线，不做外扩。
            this.normalX = 0.0F;
            this.normalY = 0.0F;
            this.normalZ = 0.0F;
            this.x = x;
            this.y = y;
            this.z = z;
            this.pending = true;
            return this;
        }

        private void flush() {
            if (!pending) {
                return;
            }
            pending = false;
            delegate.addVertex(x + normalX * offset, y + normalY * offset, z + normalZ * offset)
                    .setColor(color);
        }

        /** 冲刷最后一个顶点；模型渲染结束后必须调用。 */
        private void finish() {
            flush();
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            this.normalX = x;
            this.normalY = y;
            this.normalZ = z;
            return this;
        }
    }
}
