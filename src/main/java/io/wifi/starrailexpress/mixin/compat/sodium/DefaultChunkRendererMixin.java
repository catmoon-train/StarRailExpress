package io.wifi.starrailexpress.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.compat.SodiumShaderInterface;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlBufferUsage;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlMutableBuffer;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
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

/**
 * 场景偏移 Mixin - 完整实现版本
 * 
 * 工作原理：
 * 1. 为每个区块段计算偏移量（静态偏移 + 动态滚动）
 * 2. 将偏移数据写入 ByteBuffer
 * 3. 上传到 GPU 作为 Uniform Buffer
 * 4. Sodium Shader 读取偏移并应用到顶点位置
 */
@Mixin(value = DefaultChunkRenderer.class, remap = false)
public abstract class DefaultChunkRendererMixin {
    @Unique
    private static ByteBuffer sre_buffer = null;
    
    @Unique
    private static GlMutableBuffer glBuffer = null;
    
    // 性能优化：缓存计算结果
    @Unique
    private static float sre_lastTime = -1f;
    
    @Unique
    private static float sre_cachedTrainSpeed = 0f;
    
    @Unique
    private static int sre_frameCount = 0;
    
    // 性能优化：预计算常量（避免每帧重复计算）
    @Unique
    private static final int CHUNK_SIZE = 16;
    @Unique
    private static final int TILE_WIDTH = 15 * CHUNK_SIZE;  // 240
    @Unique
    private static final int HEIGHT = 116;
    @Unique
    private static final int TILE_LENGTH = 32 * CHUNK_SIZE;  // 512
    @Unique
    private static final int TILE_SIZE = TILE_LENGTH * 3;  // 1536
    @Unique
    private static final float HALF_TILE_SIZE = TILE_SIZE * 0.5f;  // 768.0
    @Unique
    private static final float INV_73_8 = 1.0f / 73.8f;  // 0.01355

    /**
     * 禁用面剔除以确保偏移后的区块正确渲染
     */
    @ModifyExpressionValue(
            method = "render",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/caffeinemc/mods/sodium/client/gui/SodiumGameOptions$PerformanceSettings;useBlockFaceCulling:Z"
            )
    )
    private boolean sre$disableFaceCulling(boolean original) {
        if (SREClient.needsChunkOffset()) {
            return false;
        }
        return original;
    }

    /**
     * 在渲染前上传偏移缓冲区到 GPU
     * 性能优化：重用GL缓冲区，避免每帧创建/销毁
     */
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer;executeDrawBatch(Lnet/caffeinemc/mods/sodium/client/gl/device/CommandList;Lnet/caffeinemc/mods/sodium/client/gl/tessellation/GlTessellation;Lnet/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch;)V"),
            remap = false)
    private void sre$uploadOffsetBuffer(ChunkRenderMatrices matrices,
                                        CommandList commandList,
                                        ChunkRenderListIterable renderLists,
                                        TerrainRenderPass renderPass,
                                        CameraTransform camera,
                                        CallbackInfo ci,
                                        @Local(ordinal = 0) ChunkShaderInterface shader) {
        // 调试用
        boolean needsOffset = SREClient.needsChunkOffset();
        boolean isSceneActive = SREClient.isSceneOffsetActive();
        boolean isTrainMoving = SREClient.isTrainMoving();
        AreasWorldComponent currentAreas = SREClient.areaComponent;
        
        // 如果不需要偏移，跳过
        if (!needsOffset) {
            return;
        }
        
        // 分配或重用缓冲区（只分配一次）
        if (sre_buffer == null) {
            sre_buffer = MemoryUtil.memAlloc(RenderRegion.REGION_SIZE * 16);
        } else {
            // 重置缓冲区位置，重用内存
            sre_buffer.clear();
        }
        
        // 创建或重用GL缓冲区（避免每帧创建）
        if (glBuffer == null) {
            glBuffer = commandList.createMutableBuffer();
        }
        
        // 上传数据到GPU
        commandList.uploadData(glBuffer, sre_buffer, GlBufferUsage.STREAM_DRAW);
        
        // 设置到shader（通过接口）
        if (shader instanceof SodiumShaderInterface) {
            ((SodiumShaderInterface) shader).tmm$set(glBuffer);
        }
    }

    /**
     * 渲染后清理缓冲区
     * 性能优化：保留GL缓冲区和ByteBuffer，供下一帧使用
     */
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer;executeDrawBatch(Lnet/caffeinemc/mods/sodium/client/gl/device/CommandList;Lnet/caffeinemc/mods/sodium/client/gl/tessellation/GlTessellation;Lnet/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch;)V",
            shift = At.Shift.AFTER),
            remap = false)
    private void sre$cleanupOffsetBuffer(ChunkRenderMatrices matrices,
                                         CommandList commandList,
                                         ChunkRenderListIterable renderLists,
                                         TerrainRenderPass renderPass,
                                         CameraTransform camera,
                                         CallbackInfo ci) {
        // 不做任何清理，让 Sodium 正常处理渲染
        // 缓冲区会在下次需要时自动重建
    }

    /**
     * 核心：为每个区块段计算并写入偏移量
     * 性能优化：
     * 1. 快速路径检查（全局→区域→区块三级过滤）
     * 2. 缓存时间计算
     * 3. 每3帧更新一次（降低GPU上传频率）
     * 4. 跳过不需要偏移的区块（避免无效计算）
     */
    @Inject(method = "fillCommandBuffer",
            at = @At("HEAD"),
            remap = false)
    private static void sre$debugFillCommandBuffer(
            net.caffeinemc.mods.sodium.client.gl.device.MultiDrawBatch batch,
            RenderRegion region,
            SectionRenderDataStorage renderDataStorage,
            ChunkRenderList renderList,
            CameraTransform camera,
            TerrainRenderPass pass,
            boolean useBlockFaceCulling,
            CallbackInfo ci
    ) {
    }

    @Inject(method = "fillCommandBuffer",
            at = @At(value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/data/SectionRenderDataUnsafe;getSliceMask(J)I"),
            remap = false)
    private static void sre$applyOffsets(
            net.caffeinemc.mods.sodium.client.gl.device.MultiDrawBatch batch,
            RenderRegion region,
            SectionRenderDataStorage renderDataStorage,
            ChunkRenderList renderList,
            CameraTransform camera,
            TerrainRenderPass pass,
            boolean useBlockFaceCulling,
            CallbackInfo ci,
            @Local(name = "sectionIndex") int sectionIndex
    ) {
        
        // 第1级过滤：全局功能禁用时直接返回（零开销）
        if (!SREClient.needsChunkOffset()) {
            return;
        }
        
        // 确保缓冲区已分配
        if (sre_buffer == null) {
            sre_buffer = MemoryUtil.memAlloc(RenderRegion.REGION_SIZE * 16);
        }

        float offsetX = 0, offsetY = 0, offsetZ = 0;
        boolean needsOffset = false;

        // 计算区块段的世界坐标（使用 LocalSectionIndex）
        int sectionWorldX = region.getOriginX() + LocalSectionIndex.unpackX(sectionIndex) * 16;
        int sectionWorldY = region.getOriginY() + LocalSectionIndex.unpackY(sectionIndex) * 16;
        int sectionWorldZ = region.getOriginZ() + LocalSectionIndex.unpackZ(sectionIndex) * 16;

        // 第2级过滤：场景偏移检查
        if (SREClient.isSceneOffsetActive()) {
            AreasWorldComponent areas = SREClient.areaComponent;
            if (areas != null) {
                AABB sceneArea = areas.getSceneArea();
                
                // 快速AABB重叠检测（数值比较比intersects()快）
                if (sceneArea != null &&
                    sectionWorldX + 16 > sceneArea.minX && sectionWorldX < sceneArea.maxX &&
                    sectionWorldY + 16 > sceneArea.minY && sectionWorldY < sceneArea.maxY &&
                    sectionWorldZ + 16 > sceneArea.minZ && sectionWorldZ < sceneArea.maxZ) {
                    offsetX += (float) areas.sceneOffsetX;
                    offsetY += (float) areas.sceneOffsetY;
                    offsetZ += (float) areas.sceneOffsetZ;
                    needsOffset = true;
                }
            }
        }

        // 第3级过滤：列车运动检查
        if (SREClient.isTrainMoving()) {
            needsOffset = true; // 列车移动时所有区块都需要处理
            // 性能优化：每2帧更新一次动态偏移（60fps下约33ms，平衡性能和流畅度）
            sre_frameCount++;
            boolean shouldUpdate = (sre_frameCount % 2 == 0);
            
            float trainSpeed;
            float time;
            
            if (shouldUpdate || sre_lastTime < 0) {
                // 获取游戏时间（包含部分tick）
                trainSpeed = SREClient.getTrainSpeed();
                time = SREClient.getTrainComponent().getTime()
                        + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
                sre_lastTime = time;
                sre_cachedTrainSpeed = trainSpeed;
            } else {
                time = sre_lastTime;
                trainSpeed = sre_cachedTrainSpeed;
            }

            boolean trainSection = SectionPos.blockToSectionCoord(sectionWorldY) >= 4;
            float v1 = (float) ((double) sectionWorldX - camera.fracX);
            float v2 = (float) ((double) sectionWorldY - camera.fracY);
            float v3 = (float) ((double) sectionWorldZ - camera.fracZ);
            int zSection = sectionWorldZ / CHUNK_SIZE - SectionPos.blockToSectionCoord(camera.intZ);

            float desiredX = v1, desiredY = v2, desiredZ = v3;

            // 预计算时间*速度因子（使用预先计算的倒数，避免除法）
            float timeSpeedFactor = time * (trainSpeed * INV_73_8);

            // 根据Z轴位置分三个区域处理
            if (zSection <= -8) {
                // 左侧区域：向左上方偏移并循环滚动
                desiredX = ((v1 - TILE_LENGTH + timeSpeedFactor) % TILE_SIZE - HALF_TILE_SIZE);
                desiredY = (v2 + HEIGHT);
                desiredZ = v3 + TILE_WIDTH;
            } else if (zSection >= 8) {
                // 右侧区域：向右上方偏移并循环滚动
                desiredX = ((v1 + TILE_LENGTH + timeSpeedFactor) % TILE_SIZE - HALF_TILE_SIZE);
                desiredY = (v2 + HEIGHT);
                desiredZ = v3 - TILE_WIDTH;
            } else if (!trainSection) {
                // 中间区域（列车所在层）：水平循环滚动
                desiredX = ((v1 + timeSpeedFactor) % TILE_SIZE - HALF_TILE_SIZE);
                desiredY = (v2 + HEIGHT);
                desiredZ = v3;
            }

            offsetX += desiredX - v1;
            offsetY += desiredY - v2;
            offsetZ += desiredZ - v3;
        }
        
        // 第4级过滤：如果没有任何偏移，跳过写入（减少内存访问）
        if (!needsOffset && offsetX == 0 && offsetY == 0 && offsetZ == 0) {
            return;
        }

        // 写入偏移数据到缓冲区
        int bufferIndex = sectionIndex * 16;
        sre_buffer.putFloat(bufferIndex, offsetX);
        sre_buffer.putFloat(bufferIndex + 4, offsetY);
        sre_buffer.putFloat(bufferIndex + 8, offsetZ);
    }
}
