package io.wifi.starrailexpress.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * RenderType 和它内部的 CompositeState、TextureStateShard 都没有覆写 equals/hashCode，
 * MultiBufferSource.BufferSource 的 startedBuilders / fixedBuffers / lastSharedType 全按引用比较。
 * 所以每个 layer 必须 memoize：每次现造一个新实例，等于告诉 BufferSource "这层从没见过"，
 * 它会为此结束上一批、另起一个 BufferBuilder —— HPManager 每个粒子一次 draw call，还白扔一堆垃圾。
 * 原版所有 entity 层（RenderType.entityTranslucent 等）都是这么 memoize 的。
 */
public interface TMMRenderLayers {

    Function<ResourceLocation, RenderType> ADDITIVE = Util.memoize(texture -> RenderType.create(
            "hand_particle_additive",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            256,
            true,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                    .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .createCompositeState(true)));

    Function<ResourceLocation, RenderType> ADDITIVE_FULLBRIGHT = Util.memoize(texture -> RenderType.create(
            "hand_particle_additive_fullbright",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            256,
            true,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                    .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .createCompositeState(true)));

    static RenderType additive(ResourceLocation texture) {
        return ADDITIVE.apply(texture);
    }

    static RenderType additiveFullbright(ResourceLocation texture) {
        return ADDITIVE_FULLBRIGHT.apply(texture);
    }
}
