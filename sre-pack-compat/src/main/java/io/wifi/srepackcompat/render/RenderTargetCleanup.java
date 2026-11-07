package io.wifi.srepackcompat.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.List;

/** 遗弃帧缓冲的 GL 删除入口，仅在渲染线程调用，保留未删除的原 GL 绑定。 */
public final class RenderTargetCleanup {
    public static final AbandonedRenderTargets TRACKER = new AbandonedRenderTargets();
    private static final int CLEANUP_LIMIT = 20;

    private RenderTargetCleanup() { }

    public static void tick() {
        RenderSystem.assertOnRenderThreadOrInit();
        List<AbandonedRenderTargets.Handles> batch = new ArrayList<>();
        TRACKER.drain(CLEANUP_LIMIT, batch::add);
        if (batch.isEmpty()) return;
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GlStateManager._bindTexture(0);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        try {
            for (var handles : batch) {
                if (handles.colorTexture() >= 0) TextureUtil.releaseTextureId(handles.colorTexture());
                if (handles.depthTexture() >= 0 && handles.depthTexture() != handles.colorTexture()) {
                    TextureUtil.releaseTextureId(handles.depthTexture());
                }
                if (handles.framebuffer() >= 0) GlStateManager._glDeleteFramebuffers(handles.framebuffer());
            }
        } finally {
            boolean textureDeleted = batch.stream().anyMatch(ids -> ids.containsTexture(texture));
            GlStateManager._bindTexture(textureDeleted ? 0 : texture);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,
                    retainedFramebuffer(batch, drawFramebuffer));
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,
                    retainedFramebuffer(batch, readFramebuffer));
        }
    }

    private static int retainedFramebuffer(List<AbandonedRenderTargets.Handles> batch, int id) {
        return batch.stream().anyMatch(handles -> handles.framebuffer() == id) ? 0 : id;
    }
}
