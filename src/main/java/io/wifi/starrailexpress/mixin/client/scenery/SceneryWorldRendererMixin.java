package io.wifi.starrailexpress.mixin.client.scenery;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.VertexBuffer;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SRETrainWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class SceneryWorldRendererMixin {

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 可自定义参数
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** 速度换算系数（trainSpeed km/h → 方块/tick） */
    private static final float SPEED_FACTOR = 1.0f / 73.8f;

    /** 景色区域在滚动轴上的尺寸（方块数），用于无缝循环 */
    private static final int TILE_SIZE = 32 * 16 * 3;

    /** 滚动轴方向的视距剔除阈值（方块数） */
    private static final float CULL_SCROLL_NORMAL = 160f;
    private static final float CULL_SCROLL_SUNDOWN = 320f;

    /**
     * 非滚动轴方向的剔除阈值（方块数）。
     * NONE 模式下对三轴均生效。
     */
    private static final float CULL_SIDE_X = 192f;
    private static final float CULL_SIDE_Y = 96f;
    private static final float CULL_SIDE_Z = 192f;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "renderSectionLayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ShaderInstance;apply()V", shift = At.Shift.AFTER))
    private void tmm$renderScenery(
            RenderType renderLayer, double x, double y, double z,
            Matrix4f matrix4f, Matrix4f positionMatrix, CallbackInfo ci,
            @Local ObjectListIterator<SectionRenderDispatcher.RenderSection> objectListIterator,
            @Local ShaderInstance shaderProgram) {

        if (!SREClient.isTrainMoving())
            return;

        Uniform glUniform = shaderProgram.CHUNK_OFFSET;
        AreasWorldComponent.ScrollAxis scrollAxis = SREClient.areaComponent.SceneScrollAxis;

        // ── 区域中心偏移 ──
        AABB sceneArea = SREClient.areaComponent.getSceneArea();
        AABB playArea = SREClient.areaComponent.getPlayArea();
        Vec3 areaOffset = sceneArea.getCenter().subtract(playArea.getCenter());

        // ── 滚动量（NONE 时不使用，但计算轻量无需条件跳过） ──
        float time = SREClient.trainComponent.getTime()
                + minecraft.getTimer().getGameTimeDeltaPartialTick(true);
        float trainSpeed = SREClient.getTrainSpeed();
        float scroll = (time * SPEED_FACTOR * trainSpeed) % TILE_SIZE - TILE_SIZE / 2f;

        // ── 剔除阈值（滚动轴） ──
        boolean isSundown = SREClient.trainComponent.getTimeOfDay() == SRETrainWorldComponent.TimeOfDay.SUNDOWN;
        float cullScroll = isSundown ? CULL_SCROLL_SUNDOWN : CULL_SCROLL_NORMAL;

        boolean isOpaque = renderLayer != RenderType.translucent();

        while (isOpaque ? objectListIterator.hasNext() : objectListIterator.hasPrevious()) {
            SectionRenderDispatcher.RenderSection section = isOpaque ? objectListIterator.next()
                    : objectListIterator.previous();

            if (section.getCompiled().isEmpty(renderLayer))
                continue;

            BlockPos origin = section.getOrigin();

            // 跳过列车本体区块
            if (SectionPos.blockToSectionCoord(origin.getY()) >= 4)
                continue;

            // ── 相机空间坐标 + 区域偏移 ──
            float fx = (float) (origin.getX() - x) + (float) areaOffset.x;
            float fy = (float) (origin.getY() - y) + (float) areaOffset.y;
            float fz = (float) (origin.getZ() - z) + (float) areaOffset.z;

            // ── 非滚动轴提前剔除 ──
            // NONE：景色静止，对三轴都做剔除
            // X/Y/Z：仅对另外两轴剔除，滚动轴留到叠加 scroll 后再剔除
            switch (scrollAxis) {
                case NONE -> {
                    if (Math.abs(fx) >= CULL_SIDE_X)
                        continue;
                    if (Math.abs(fy) >= CULL_SIDE_Y)
                        continue;
                    if (Math.abs(fz) >= CULL_SIDE_Z)
                        continue;
                }
                case X -> {
                    if (Math.abs(fy) >= CULL_SIDE_Y)
                        continue;
                    if (Math.abs(fz) >= CULL_SIDE_Z)
                        continue;
                }
                case Y -> {
                    if (Math.abs(fx) >= CULL_SIDE_X)
                        continue;
                    if (Math.abs(fz) >= CULL_SIDE_Z)
                        continue;
                }
                case Z -> {
                    if (Math.abs(fx) >= CULL_SIDE_X)
                        continue;
                    if (Math.abs(fy) >= CULL_SIDE_Y)
                        continue;
                }
            }

            // ── 有滚动轴时：叠加 scroll 并做滚动轴剔除 ──
            if (scrollAxis != AreasWorldComponent.ScrollAxis.NONE) {
                switch (scrollAxis) {
                    case X -> fx += scroll;
                    case Y -> fy += scroll;
                    case Z -> fz += scroll;
                    default -> scroll += 0;
                }

                float scrollValue = switch (scrollAxis) {
                    case X -> fx;
                    case Y -> fy;
                    case Z -> fz;
                    default -> 0f;
                };
                if (Math.abs(scrollValue) >= cullScroll)
                    continue;
            }

            // ── 通过所有剔除，提交绘制 ──
            minecraft.smartCull = false;

            if (glUniform != null) {
                glUniform.set(fx, fy, fz);
                glUniform.upload();
            }

            VertexBuffer vb = section.getBuffer(renderLayer);
            vb.bind();
            vb.draw();
        }

        // ── 收尾清理 ──
        if (glUniform != null)
            glUniform.set(0f, 0f, 0f);
        shaderProgram.clear();
        VertexBuffer.unbind();
        this.minecraft.getProfiler().pop();
        renderLayer.clearRenderState();
        ci.cancel();
    }
}