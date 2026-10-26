package io.wifi.starrailexpress.settlement.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/** 在最终 GUI 合成点保存实际 GL 状态，同时通过 RenderSystem 恢复其状态缓存。 */
final class SettlementRenderState implements AutoCloseable {
    private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend = GL11.glIsEnabled(GL11.GL_BLEND),
            cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),
            depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    private final int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC),
            srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
            srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),
            drawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
            readFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
            program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    private final int[] viewport = new int[4], scissorBox = new int[4], bindings = new int[4], shaderTextures = new int[4];
    private final float[] color = RenderSystem.getShaderColor().clone();
    private final ShaderInstance shader = RenderSystem.getShader();
    private final int blendEquationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB),
            blendEquationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
    private final java.nio.ByteBuffer mask = org.lwjgl.BufferUtils.createByteBuffer(4);

    SettlementRenderState() {
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport); GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox);
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
        for (int i = 0; i < bindings.length; i++) {
            RenderSystem.activeTexture(GL13.GL_TEXTURE0 + i);
            bindings[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            shaderTextures[i] = RenderSystem.getShaderTexture(i);
        }
        RenderSystem.activeTexture(activeTexture);
    }

    @Override public void close() {
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
        RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
        RenderSystem.depthMask(depthMask); RenderSystem.depthFunc(depthFunc);
        if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        GL20.glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
        if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
        if (scissor) RenderSystem.enableScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
        else RenderSystem.disableScissor();
        RenderSystem.colorMask(mask.get(0) != 0, mask.get(1) != 0, mask.get(2) != 0, mask.get(3) != 0);
        RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
        RenderSystem.setShader(() -> shader);
        for (int i = 0; i < bindings.length; i++) {
            RenderSystem.setShaderTexture(i, shaderTextures[i]);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0 + i); RenderSystem.bindTexture(bindings[i]);
        }
        RenderSystem.activeTexture(activeTexture); GlStateManager._glUseProgram(program);
    }
}
