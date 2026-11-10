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

package io.wifi.starrailexpress.client.gui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.client.SecurityCameraClientState;
import io.wifi.starrailexpress.mixin.client.MinecraftRenderTargetAccessor;
import io.wifi.starrailexpress.settlement.client.SettlementRenderState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * 监控台左上角小窗的实时画面：把「监控台本身」当成一台朝正面看的小摄像头。
 *
 * <p>主画面仍由 {@code SecurityCameraClientState} 接管主相机渲染当前选中的摄像头；
 * 这里在原版 {@code GameRenderer.renderLevel} 之前额外渲染一帧世界到离屏目标，
 * 渲染期间把 {@code Minecraft.mainRenderTarget} 临时换成离屏目标，
 * 因此 {@code LevelRenderer.renderLevel} 内部的辅助 RenderTarget（半透明、粒子、
 * 天气等）以及它们对主目标的深度拷贝都会自动落在离屏目标上，不需要改写世界渲染管线。
 *
 * <p>离屏目标与主渲染目标保持同样尺寸，避免辅助缓冲做深度 blit 时尺寸不匹配；
 * 小窗只是把这张全尺寸纹理缩放显示。整帧世界渲染开销较大，所以按
 * {@link #CAPTURE_INTERVAL} 降频，其余帧沿用上一帧画面。
 */
public final class SecurityMonitorFeedRenderer {

    /** 每多少帧刷新一次小窗画面；主画面依旧逐帧渲染。 */
    private static final int CAPTURE_INTERVAL = 2;

    private static TextureTarget feedTarget;
    private static boolean hasFrame;
    private static boolean capturing;
    private static int frameCounter;
    private static boolean loggedFailure;

    private SecurityMonitorFeedRenderer() {
    }

    /** 离屏渲染进行中：相机接管逻辑据此改用监控台自身视角。 */
    public static boolean isCapturing() {
        return capturing;
    }

    /**
     * 在原版世界渲染之前抓一帧「监控台视角」。
     * 失败时只放弃小窗画面，绝不抛异常中断主画面渲染。
     */
    public static void capture(DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!SecurityCameraClientState.isInSecurityMode()
                || !SREClientConfig.instance().enableSecurityCameraHUD
                || minecraft.level == null || minecraft.player == null
                || SecurityCameraClientState.getCurrentMonitorPos() == null
                        && SecurityCameraClientState.getCurrentCameraPos() == null) {
            return;
        }
        if (hasFrame && ++frameCounter % CAPTURE_INTERVAL != 0) {
            return;
        }

        RenderTarget original = minecraft.getMainRenderTarget();
        if (original == null || original.width <= 0 || original.height <= 0) {
            return;
        }
        if (feedTarget == null) {
            feedTarget = new TextureTarget(original.width, original.height, true, Minecraft.ON_OSX);
            feedTarget.setClearColor(0.0F, 0.0F, 0.0F, 1.0F);
            feedTarget.setFilterMode(GL11.GL_LINEAR);
            hasFrame = false;
        } else if (feedTarget.width != original.width || feedTarget.height != original.height) {
            feedTarget.resize(original.width, original.height, Minecraft.ON_OSX);
            hasFrame = false;
        }

        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        RenderSystem.getModelViewStack().pushMatrix();
        capturing = true;
        // 世界渲染会改动 shader / 混合 / 深度 / 纹理槽等大量全局状态，整段包起来恢复，
        // 否则紧接着的摄像头主画面和后处理会用到小窗残留的状态。
        try (SettlementRenderState ignored = new SettlementRenderState()) {
            ((MinecraftRenderTargetAccessor) minecraft).sre$setMainRenderTarget(feedTarget);
            feedTarget.clear(Minecraft.ON_OSX);
            feedTarget.bindWrite(true);
            // 内部会重新 setup 相机，CameraPositionMixin 在 capturing 期间使用监控台视角。
            minecraft.gameRenderer.renderLevel(deltaTracker);
            hasFrame = true;
        } catch (RuntimeException error) {
            if (!loggedFailure) {
                loggedFailure = true;
                SRE.LOGGER.warn("监控台小窗画面渲染失败，已回退为静态背景", error);
            }
        } finally {
            capturing = false;
            ((MinecraftRenderTargetAccessor) minecraft).sre$setMainRenderTarget(original);
            RenderSystem.getModelViewStack().popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, VertexSorting.ORTHOGRAPHIC_Z);
            // 恢复主目标（顺带恢复 viewport），原版紧接着会往它上面渲染摄像头画面。
            original.bindWrite(true);
        }
    }

    /**
     * 把离屏画面贴进 HUD 的小窗区域。
     *
     * @return 是否真的画出了画面；false 时调用方应画原来的深色底。
     */
    public static boolean draw(GuiGraphics graphics, int x, int y, int width, int height) {
        if (!hasFrame || feedTarget == null || width <= 0 || height <= 0) {
            return false;
        }
        graphics.flush();
        try (SettlementRenderState ignored = new SettlementRenderState()) {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, feedTarget.getColorTextureId());

            Matrix4f pose = graphics.pose().last().pose();
            BufferBuilder builder = Tesselator.getInstance()
                    .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            // RenderTarget 的纹理原点在左下，GUI 原点在左上，v 需要翻转。
            builder.addVertex(pose, (float) x, (float) (y + height), 0.0F).setUv(0.0F, 0.0F);
            builder.addVertex(pose, (float) (x + width), (float) (y + height), 0.0F).setUv(1.0F, 0.0F);
            builder.addVertex(pose, (float) (x + width), (float) y, 0.0F).setUv(1.0F, 1.0F);
            builder.addVertex(pose, (float) x, (float) y, 0.0F).setUv(0.0F, 1.0F);
            BufferUploader.drawWithShader(builder.buildOrThrow());
        }
        return true;
    }

    /** 退出监控模式时清掉旧画面，避免下次进入时闪出上一局的监控台。 */
    public static void reset() {
        hasFrame = false;
        frameCounter = 0;
        capturing = false;
    }

    /** 退出监控模式时释放离屏缓冲；GL 资源只能在渲染线程销毁。 */
    public static void close() {
        if (feedTarget != null && !RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(SecurityMonitorFeedRenderer::close);
            return;
        }
        if (feedTarget != null) {
            feedTarget.destroyBuffers();
            feedTarget = null;
        }
        reset();
    }
}
