package net.exmo.mixin.client.side;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.wifi.starrailexpress.compat.SodiumShaderInterface;
import io.wifi.starrailexpress.scenery.client.SceneAssetClient;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlBufferUsage;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlMutableBuffer;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.gl.device.MultiDrawBatch;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.LocalSectionIndex;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.caffeinemc.mods.sodium.client.util.iterator.ByteIterator;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;

/**
 * sodium 0.8.x：场景资产（移动布景）的区块网格顶点偏移渲染。
 *
 * 相对 0.6.x 的适配点：
 * <ul>
 * <li>{@code _draw_id} 是 section 在 region 内的本地索引（0..255，见 LocalSectionIndex），
 * 偏移 UBO 按该索引写入，数组长度 {@link RenderRegion#REGION_SIZE}，须与
 * ShaderLoaderMixin / SodiumTransformerMixin 注入的 GLSL 数组长度一致；</li>
 * <li>{@code MultiDrawBatch} 现按 region+pass 缓存（batch.isFilled），fillCommandBuffer
 * 不再每帧执行——存在活跃场景资产时清空缓存强制每帧重填，保证 isActiveSection
 * 的面剔除豁免即时生效；</li>
 * <li>偏移数据改在 render() 的 executeDrawBatch 之前按当前 renderList 重建并上传。</li>
 * </ul>
 */
@Mixin(value = DefaultChunkRenderer.class)
public abstract class DefaultChunkRendererMixin {
    @Unique
    private static ByteBuffer sre$offsetBuffer;
    @Unique
    private static GlMutableBuffer sre$glBuffer;

    @WrapOperation(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/region/RenderRegion;getCachedBatch(Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;)Lnet/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch;"),
            remap = false)
    private MultiDrawBatch sre$refillWhileSceneActive(RenderRegion region, TerrainRenderPass pass,
            Operation<MultiDrawBatch> original) {
        MultiDrawBatch batch = original.call(region, pass);
        if (SceneAssetClient.hasActiveAsset()) {
            // 活跃 section 集合可能随时变化，不能吃批缓存（行为等同 0.6 的每帧重填）
            batch.clear();
        }
        return batch;
    }

    @WrapOperation(
            method = "fillCommandBuffer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer;getVisibleFaces(IIIIII)I"),
            remap = false)
    private static int sre$forceSceneFaces(int originX, int originY, int originZ,
            int chunkX, int chunkY, int chunkZ, Operation<Integer> original) {
        if (SceneAssetClient.isActiveSection(chunkX, chunkY, chunkZ)) {
            // 网格会被顶点偏移挪走，按网格原位置做的面剔除不成立
            return ModelQuadFacing.ALL;
        }
        return original.call(originX, originY, originZ, chunkX, chunkY, chunkZ);
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer;executeDrawBatch(Lnet/caffeinemc/mods/sodium/client/gl/device/CommandList;Lnet/caffeinemc/mods/sodium/client/gl/tessellation/GlTessellation;Lnet/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch;)V"),
            remap = false)
    private void sre$bindOffsets(
            ChunkRenderMatrices matrices,
            CommandList commandList,
            ChunkRenderListIterable renderLists,
            TerrainRenderPass renderPass,
            CameraTransform camera,
            boolean indexedRenderingEnabled,
            CallbackInfo ci,
            @Local(ordinal = 0) ChunkShaderInterface shader,
            @Local(ordinal = 0) ChunkRenderList renderList,
            @Local(ordinal = 0) RenderRegion region) {
        if (sre$offsetBuffer == null) {
            sre$offsetBuffer = MemoryUtil.memCalloc(RenderRegion.REGION_SIZE * 16);
        } else {
            MemoryUtil.memSet(sre$offsetBuffer, 0);
        }
        sre$writeSceneOffsets(renderList, region, renderPass);
        sre$glBuffer = commandList.createMutableBuffer();
        commandList.uploadData(sre$glBuffer, sre$offsetBuffer, GlBufferUsage.STREAM_DRAW);
        ((SodiumShaderInterface) shader).tmm$set(sre$glBuffer);
    }

    @Unique
    private static void sre$writeSceneOffsets(ChunkRenderList renderList, RenderRegion region,
            TerrainRenderPass pass) {
        if (!SceneAssetClient.hasActiveAsset()) {
            return;
        }
        ByteIterator iterator = renderList.sectionsWithGeometryIterator(pass.isTranslucent());
        if (iterator == null) {
            return;
        }
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        Vec3 offset = SceneAssetClient.renderOffset(partialTick);
        int originX = region.getChunkX();
        int originY = region.getChunkY();
        int originZ = region.getChunkZ();
        while (iterator.hasNext()) {
            int sectionIndex = iterator.nextByteAsInt();
            if (!SceneAssetClient.isActiveSection(
                    originX + LocalSectionIndex.unpackX(sectionIndex),
                    originY + LocalSectionIndex.unpackY(sectionIndex),
                    originZ + LocalSectionIndex.unpackZ(sectionIndex))) {
                continue;
            }
            int base = sectionIndex * 16;
            sre$offsetBuffer.putFloat(base, (float) offset.x);
            sre$offsetBuffer.putFloat(base + 4, (float) offset.y);
            sre$offsetBuffer.putFloat(base + 8, (float) offset.z);
        }
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer;executeDrawBatch(Lnet/caffeinemc/mods/sodium/client/gl/device/CommandList;Lnet/caffeinemc/mods/sodium/client/gl/tessellation/GlTessellation;Lnet/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch;)V",
                    shift = At.Shift.AFTER),
            remap = false)
    private void sre$releaseOffsets(
            ChunkRenderMatrices matrices,
            CommandList commandList,
            ChunkRenderListIterable renderLists,
            TerrainRenderPass renderPass,
            CameraTransform camera,
            boolean indexedRenderingEnabled,
            CallbackInfo ci) {
        if (sre$glBuffer != null) {
            commandList.deleteBuffer(sre$glBuffer);
            sre$glBuffer = null;
        }
    }
}
