package io.wifi.starrailexpress.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.compat.SodiumShaderInterface;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlBufferUsage;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlMutableBuffer;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.gl.device.MultiDrawBatch;
import net.caffeinemc.mods.sodium.client.gl.tessellation.GlTessellation;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.LocalSectionIndex;
import net.caffeinemc.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;

@Mixin(value = DefaultChunkRenderer.class)
public abstract class DefaultChunkRendererMixin {
//    @Unique
//    private static ByteBuffer sre_buffer = MemoryUtil.memAlloc(RenderRegion.REGION_SIZE * 16);
//    @Unique
//    private static GlMutableBuffer glBuffer;
//
//    @ModifyExpressionValue(
//            method = "render",
//            at = @At(
//                    value = "FIELD",
//                    target = "Lnet/caffeinemc/mods/sodium/client/gui/SodiumGameOptions$PerformanceSettings;useBlockFaceCulling:Z"
//            ),
//            remap = false
//    )
//    private boolean sre$disableFaceCulling(boolean original) {
//        if (SREClient.needsChunkOffset()) {
//            return false;
//        }
//        return original;
//    }
//
//    @Inject(method = "render", at = @At(value = "INVOKE",
//            target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer;executeDrawBatch(Lnet/caffeinemc/mods/sodium/client/gl/device/CommandList;Lnet/caffeinemc/mods/sodium/client/gl/tessellation/GlTessellation;Lnet/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch;)V"),
//            remap = false)
//    private void sre$uploadOffsetBuffer(ChunkRenderMatrices matrices,
//                                        CommandList commandList,
//                                        ChunkRenderListIterable renderLists,
//                                        TerrainRenderPass renderPass,
//                                        CameraTransform camera,
//                                        CallbackInfo ci,
//                                        @Local(ordinal = 0) ChunkShaderInterface shader) {
//        glBuffer = commandList.createMutableBuffer();
//        commandList.uploadData(glBuffer, sre_buffer, GlBufferUsage.STREAM_DRAW);
//        ((SodiumShaderInterface) shader).tmm$set(glBuffer);
//    }
//
//    @Inject(method = "render", at = @At(value = "INVOKE",
//            target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer;executeDrawBatch(Lnet/caffeinemc/mods/sodium/client/gl/device/CommandList;Lnet/caffeinemc/mods/sodium/client/gl/tessellation/GlTessellation;Lnet/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch;)V",
//            shift = At.Shift.AFTER),
//            remap = false)
//    private void sre$cleanupOffsetBuffer(ChunkRenderMatrices matrices,
//                                         CommandList commandList,
//                                         ChunkRenderListIterable renderLists,
//                                         TerrainRenderPass renderPass,
//                                         CameraTransform camera,
//                                         CallbackInfo ci) {
//        MemoryUtil.memFree(sre_buffer);
//        commandList.deleteBuffer(glBuffer);
//        sre_buffer = null;
//    }
//
//    @Inject(method = "fillCommandBuffer",
//            at = @At(value = "INVOKE",
//                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/data/SectionRenderDataUnsafe;getSliceMask(J)I"),
//            remap = false)
//    private static void sre$applyOffsets(
//            MultiDrawBatch batch,
//            RenderRegion region,
//            SectionRenderDataStorage renderDataStorage,
//            ChunkRenderList renderList,
//            CameraTransform camera,
//            TerrainRenderPass pass,
//            boolean useBlockFaceCulling,
//            CallbackInfo ci,
//            @Local(name = "sectionIndex") int sectionIndex
//    ) {
//        if (sre_buffer == null) {
//            sre_buffer = MemoryUtil.memAlloc(RenderRegion.REGION_SIZE * 16);
//        }
//
//        float offsetX = 0, offsetY = 0, offsetZ = 0;
//
//        int sectionWorldX = region.getOriginX() + LocalSectionIndex.unpackX(sectionIndex) * 16;
//        int sectionWorldY = region.getOriginY() + LocalSectionIndex.unpackY(sectionIndex) * 16;
//        int sectionWorldZ = region.getOriginZ() + LocalSectionIndex.unpackZ(sectionIndex) * 16;
//
//        // 场景偏移：将sceneArea内的区块渲染到偏移位置（默认关闭，可在地图配置中启用）
//        if (SREClient.isSceneOffsetActive()) {
//            AreasWorldComponent areas = SREClient.areaComponent;
//            AABB sceneArea = areas.getSceneArea();
//
//            // 检查此section是否与sceneArea重叠
//            if (sectionWorldX + 16 > sceneArea.minX && sectionWorldX < sceneArea.maxX &&
//                sectionWorldY + 16 > sceneArea.minY && sectionWorldY < sceneArea.maxY &&
//                sectionWorldZ + 16 > sceneArea.minZ && sectionWorldZ < sceneArea.maxZ) {
//                offsetX += (float) areas.sceneOffsetX;
//                offsetY += (float) areas.sceneOffsetY;
//                offsetZ += (float) areas.sceneOffsetZ;
//            }
//        }
//
//        // 列车运动：滚动/平铺场景
//        if (SREClient.isTrainMoving()) {
//            float trainSpeed = SREClient.getTrainSpeed();
//            int chunkSize = 16;
//            int tileWidth = 15 * chunkSize;
//            int height = 116;
//            int tileLength = 32 * chunkSize;
//            int tileSize = tileLength * 3;
//            float time = SREClient.getTrainComponent().getTime()
//                    + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
//
//            boolean trainSection = SectionPos.blockToSectionCoord(sectionWorldY) >= 4;
//            float v1 = (float) ((double) sectionWorldX - camera.fracX);
//            float v2 = (float) ((double) sectionWorldY - camera.fracY);
//            float v3 = (float) ((double) sectionWorldZ - camera.fracZ);
//            int zSection = sectionWorldZ / chunkSize - SectionPos.blockToSectionCoord(camera.intZ);
//
//            float desiredX = v1, desiredY = v2, desiredZ = v3;
//
//            if (zSection <= -8) {
//                desiredX = ((v1 - tileLength + ((time) / 73.8f * trainSpeed)) % tileSize - tileSize / 2f);
//                desiredY = (v2 + height);
//                desiredZ = v3 + tileWidth;
//            } else if (zSection >= 8) {
//                desiredX = ((v1 + tileLength + ((time) / 73.8f * trainSpeed)) % tileSize - tileSize / 2f);
//                desiredY = (v2 + height);
//                desiredZ = v3 - tileWidth;
//            } else if (!trainSection) {
//                desiredX = ((v1 + ((time) / 73.8f * trainSpeed)) % tileSize - tileSize / 2f);
//                desiredY = (v2 + height);
//                desiredZ = v3;
//            }
//
//            offsetX += desiredX - v1;
//            offsetY += desiredY - v2;
//            offsetZ += desiredZ - v3;
//        }
//
//        sre_buffer.putFloat(sectionIndex * 16, offsetX);
//        sre_buffer.putFloat(sectionIndex * 16 + 4, offsetY);
//        sre_buffer.putFloat(sectionIndex * 16 + 8, offsetZ);
//    }
}
