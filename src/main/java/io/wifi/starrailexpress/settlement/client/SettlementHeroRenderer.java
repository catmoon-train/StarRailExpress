package io.wifi.starrailexpress.settlement.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import io.wifi.starrailexpress.SRE;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/** MVP 独立透明人像及轮廓合成；只在最终 GUI 绘制，不接管世界光影管线。 */
final class SettlementHeroRenderer {
    private static TextureTarget target;
    private static ShaderInstance shader;
    private static ByteBufferBuilder storage;
    private static MultiBufferSource.BufferSource buffers;
    private SettlementHeroRenderer() { }

    static void register() {
        CoreShaderRegistrationCallback.EVENT.register(context -> context.register(SRE.id("settlement_portrait"),
                DefaultVertexFormat.POSITION_TEX_COLOR, loaded -> shader=loaded));
    }

    static void render(GuiGraphics g, SettlementClient.ActorState actor, SettlementPresentation.Rect rect,
                       double time, float alpha, float ui) {
        if (shader==null || alpha<.01f) return;
        g.flush();
        Minecraft mc=Minecraft.getInstance();
        double pixelScale=mc.getMainRenderTarget().height/(double)mc.getWindow().getGuiScaledHeight();
        double reduction=Math.min(1,1024/Math.max(rect.w()*pixelScale,rect.h()*pixelScale));
        int width=Math.max(64,(int)(rect.w()*pixelScale*reduction));
        int height=Math.max(64,(int)(rect.h()*pixelScale*reduction));
        Matrix4f oldProjection=new Matrix4f(RenderSystem.getProjectionMatrix());
        // 不使用全局 backupProjectionMatrix，避免覆盖外层最终 HUD 的投影备份。
        RenderSystem.getModelViewStack().pushMatrix();
        try (SettlementRenderState ignored=new SettlementRenderState()) {
            if (target==null) {
                target=new TextureTarget(width,height,true,Minecraft.ON_OSX);
                target.setClearColor(0,0,0,0); target.setFilterMode(GL11.GL_LINEAR);
                storage=new ByteBufferBuilder(131072); buffers=MultiBufferSource.immediate(storage);
            } else if (target.width!=width||target.height!=height) target.resize(width,height,Minecraft.ON_OSX);
            RenderSystem.disableScissor();RenderSystem.depthMask(true);RenderSystem.colorMask(true,true,true,true);
            target.clear(Minecraft.ON_OSX);target.bindWrite(true);
            float vertical=(float)(2.4-.13*SettlementPresentation.easeOut((time-212)/14));
            float halfWidth=vertical*width/height/2;
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(-halfWidth,halfWidth,-vertical/2,vertical/2,.05f,20),VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.getModelViewStack().identity().mul(new Matrix4f().lookAt(0,1.16f,-6,0,1.16f,0,0,1,0));
            RenderSystem.applyModelViewMatrix();RenderSystem.setShaderColor(1,1,1,1);
            RenderSystem.enableDepthTest();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
            SettlementStageRenderer.renderPortrait(new PoseStack(),buffers,actor,time);
            buffers.endBatch();
        } finally {
            RenderSystem.getModelViewStack().popMatrix();RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(oldProjection,VertexSorting.ORTHOGRAPHIC_Z);
        }
        // 边缘采样只使用离屏人物纹理，不读取正在写入的主颜色/深度附件。
        RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShaderColor(1,1,1,1);
        // setDefaultUniforms 会重写 Sampler0，必须同步 RenderSystem 的纹理槽而非只调用 setSampler。
        RenderSystem.setShaderTexture(0,target.getColorTextureId());
        shader.safeGetUniform("TexelSize").set(1f/target.width,1f/target.height);
        shader.safeGetUniform("OutlineWidth").set((float)(3*ui*target.height/rect.h()));
        double slide=(1-SettlementPresentation.easeOut((time-205)/18))*24*ui;
        shader.safeGetUniform("Opacity").set(alpha*.55f);shader.safeGetUniform("Shadow").set(1f);
        drawTexture(g,rect.x()+slide+4*ui,rect.y()+5*ui,rect.w(),rect.h());
        shader.safeGetUniform("Opacity").set(alpha);shader.safeGetUniform("Shadow").set(0f);
        drawTexture(g,rect.x()+slide,rect.y(),rect.w(),rect.h());
    }

    private static void drawTexture(GuiGraphics g,double x,double y,double w,double h) {
        RenderSystem.setShader(()->shader);
        BufferBuilder b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
        var m=g.pose().last().pose();
        b.addVertex(m,(float)x,(float)(y+h),0).setUv(0,0).setColor(-1);
        b.addVertex(m,(float)(x+w),(float)(y+h),0).setUv(1,0).setColor(-1);
        b.addVertex(m,(float)(x+w),(float)y,0).setUv(1,1).setColor(-1);
        b.addVertex(m,(float)x,(float)y,0).setUv(0,1).setColor(-1);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    static void close() {
        if (target!=null) {target.destroyBuffers();target=null;}
        if (storage!=null) {storage.close();storage=null;buffers=null;}
    }
}
