package io.wifi.starrailexpress.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** 光影最终合成后的 SRE 深度雾；不依赖光影包是否支持原版雾 uniform。 */
public final class ShaderPackFog {
    private static ShaderInstance shader;
    private static int framebuffer;
    private static boolean active;
    private static float start, end;
    private static int shape;
    private static final float[] color = new float[3];

    private ShaderPackFog() {}

    public static void register() {
        CoreShaderRegistrationCallback.EVENT.register(context -> context.register(
                ResourceLocation.fromNamespaceAndPath("starrailexpress", "shader_pack_fog"),
                DefaultVertexFormat.POSITION, loaded -> shader = loaded));
    }

    /** Iris 每帧开始时重置，避免大厅、退局、效果结束后沿用上一帧的雾。 */
    public static void beginFrame() {
        active = false;
    }

    /** 只由实际设置了 SRE 雾的路径调用，不能把普通远景雾强加给所有光影。 */
    public static void capture() {
        float fogEnd = RenderSystem.getShaderFogEnd();
        float fogStart = RenderSystem.getShaderFogStart();
        if (!Float.isFinite(fogStart) || !Float.isFinite(fogEnd) || fogEnd <= 0) return;
        active = true;
        start = fogStart;
        end = fogEnd;
        shape = RenderSystem.getShaderFogShape() == FogShape.CYLINDER ? 1 : 0;
        System.arraycopy(RenderSystem.getShaderFogColor(), 0, color, 0, 3);
    }

    public static void refreshCapturedFog() {
        if (active) capture();
    }

    public static void destroy() {
        beginFrame();
        if (framebuffer != 0) {
            GlStateManager._glDeleteFramebuffers(framebuffer);
            framebuffer = 0;
        }
    }

    public static void render(Matrix4fc projection, Matrix4fc modelView) {
        if (!active || shader == null || Minecraft.getInstance().level == null) return;
        // 同一帧只消费一次；不在 HUD、阴影贴图或无光影的原版路径绘制。
        active = false;
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        if (target.getDepthTextureId() < 0) return;
        try (State ignored = new State()) {
            // 单独挂颜色附件，不能在采样深度时仍把同一深度纹理挂在绘制 FBO 上。
            if (framebuffer == 0) framebuffer = GlStateManager.glGenFramebuffers();
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
            GlStateManager._glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, target.getColorTextureId(), 0);
            RenderSystem.viewport(0, 0, target.viewWidth, target.viewHeight);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.disableScissor();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
            RenderSystem.colorMask(true, true, true, false);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            GL33.glBindSampler(0, 0);
            shader.setSampler("DepthSampler", target.getDepthTextureId());
            shader.safeGetUniform("InverseProjection").set(new Matrix4f(projection).invert());
            shader.safeGetUniform("InverseModelView").set(new Matrix4f(modelView).invert());
            shader.safeGetUniform("FogRange").set(start, end);
            shader.safeGetUniform("FogShape").set(shape);
            shader.safeGetUniform("FogColor").set(color[0], color[1], color[2]);
            RenderSystem.setShader(() -> shader);
            BufferBuilder quad = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            quad.addVertex(-1, -1, 0);
            quad.addVertex(1, -1, 0);
            quad.addVertex(1, 1, 0);
            quad.addVertex(-1, 1, 0);
            BufferUploader.drawWithShader(quad.buildOrThrow());
        }
    }

    /** 同时恢复实际 GL 状态和原版缓存，避免污染后续 HUD 与下一帧光影。 */
    private static final class State implements AutoCloseable {
        private final int drawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        private final int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        private final int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        private final int equationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB), equationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend = GL11.glIsEnabled(GL11.GL_BLEND);
        private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        private final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        private final int[] viewport = new int[4], scissorBox = new int[4];
        private final java.nio.ByteBuffer colorMask = BufferUtils.createByteBuffer(4);
        private final ShaderInstance previousShader = RenderSystem.getShader();
        private final int texture, sampler;

        private State() {
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, colorMask);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            sampler = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
        }

        @Override public void close() {
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            RenderSystem.depthMask(depthMask);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            GL20.glBlendEquationSeparate(equationRgb, equationAlpha);
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (scissor) RenderSystem.enableScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
            else RenderSystem.disableScissor();
            RenderSystem.colorMask(colorMask.get(0) != 0, colorMask.get(1) != 0, colorMask.get(2) != 0, colorMask.get(3) != 0);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            RenderSystem.bindTexture(texture);
            GL33.glBindSampler(0, sampler);
            RenderSystem.activeTexture(activeTexture);
            RenderSystem.setShader(() -> previousShader);
            GlStateManager._glUseProgram(program);
        }
    }
}
