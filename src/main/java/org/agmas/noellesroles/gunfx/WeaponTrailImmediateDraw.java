/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.agmas.noellesroles.gunfx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;

import java.util.function.Consumer;

/**
 * 世界半透明阶段用独立 Tesselator 立刻画线。
 * 不能走 {@code MultiBufferSource#getBuffer}：切换自定义 RenderType 会结束上一个
 * BufferBuilder，再写顶点就会抛 {@code Not building!}（尤其是 Sodium）。
 */
public final class WeaponTrailImmediateDraw {
    private WeaponTrailImmediateDraw() {
    }

    public static void drawLines(float lineWidth, Consumer<VertexConsumer> writer) {
        BufferBuilder builder = Tesselator.getInstance()
                .begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        writer.accept(builder);
        MeshData mesh = builder.build();
        if (mesh == null) {
            return;
        }
        try {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(false);
            RenderSystem.enableDepthTest();
            RenderSystem.disableCull();
            RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
            RenderSystem.lineWidth(lineWidth);
            BufferUploader.drawWithShader(mesh);
        } finally {
            mesh.close();
            RenderSystem.lineWidth(1.0F);
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
    }
}
