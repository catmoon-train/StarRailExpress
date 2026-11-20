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

    /** 绑定场景 Outliner；由 {@link PonderPlayerPreset} 在加入场景时调用。 */
    public PonderPlayerElement bindOutliner(@Nullable Outliner outliner) {
        this.outliner = outliner;
        return this;
    }

    /** 当前是否绘制边框。 */
    public boolean hasOutline() {
        return outlineColor != PonderPlayerPreset.NO_OUTLINE;
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
        if (puppet == null) {
            return;
        }
        resetPuppetPose();
        applyFacingToPuppet();
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
        puppet.walkAnimation.setSpeed(0);
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
        poseStack.pushPose();
        poseStack.translate(location.x, location.y, location.z);
        poseStack.translate(Mth.lerp(pt, puppet.xo, puppet.getX()),
                Mth.lerp(pt, puppet.yo, puppet.getY()),
                Mth.lerp(pt, puppet.zo, puppet.getZ()));

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
        poseStack.pushPose();
        poseStack.translate(location.x, location.y, location.z);
        poseStack.translate(Mth.lerp(pt, puppet.xo, puppet.getX()),
                Mth.lerp(pt, puppet.yo, puppet.getY()),
                Mth.lerp(pt, puppet.zo, puppet.getZ()));
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
