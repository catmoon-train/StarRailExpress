package io.wifi.starrailexpress.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.compat.sodium.SRESceneryShaderInterface;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlBufferUsage;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlMutableBuffer;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.gl.device.MultiDrawBatch;
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
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;

/**
 * 核心修复：将景色背景渲染从"第二次独立渲染通道"改为"着色器 UBO 顶点偏移"。
 *
 * <h3>旧方案的 Bug 原因</h3>
 * <p>
 * 旧方案在 {@code renderLayer} HEAD 时调用 {@code chunkRenderer.render()} 并传入修改后的
 * {@code CameraTransform}。这会把错误的摄像机 uniform 写入 GL 状态，
 * 等主渲染通道继续时读到污染的 uniform，导致摄像机每隔一段时间瞬间回到原点。
 * </p>
 *
 * <h3>新方案原理（参考 wathe DefaultChunkRendererMixin）</h3>
 * <ol>
 * <li>{@code fillCommandBuffer} 为每个区块计算偏移量并写入 CPU 端 ByteBuffer。</li>
 * <li>在 {@code executeDrawBatch} 执行之前，将 CPU Buffer 上传为 GPU UBO。</li>
 * <li>GLSL 顶点着色器自动将偏移量叠加到 {@code _vert_position}，无需额外渲染通道。</li>
 * <li>GL 摄像机状态全程不被触碰，彻底消除视角抖动 bug。</li>
 * </ol>
 */
@Mixin(value = DefaultChunkRenderer.class, remap = false)
public abstract class SodiumChunkRendererMixin {

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 常量（与 SodiumSceneryWorldRendererMixin 保持一致）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Unique
    private static final float SPEED_FACTOR = 1.0f / 73.8f;
    @Unique
    private static final int TILE_SIZE = 32 * 16 * 3;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // CPU / GPU 偏移缓冲区
    // 每个 region 最多 256 个区块，每个区块 16 字节（vec4：x, y, z, 填充）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Unique
    private static ByteBuffer sre$cpuBuffer = MemoryUtil.memAlloc(RenderRegion.REGION_SIZE * 16);
    @Unique
    private static GlMutableBuffer sre$gpuBuffer;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 禁用面剔除（列车移动时景色区块朝向不可预测）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @ModifyExpressionValue(method = "render", at = @At(value = "FIELD", target = "Lnet/caffeinemc/mods/sodium/client/gui/SodiumGameOptions$PerformanceSettings;useBlockFaceCulling:Z"))
    private boolean sre$disableBlockFaceCulling(boolean original) {
        return SREClient.isTrainMoving() ? false : original;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // executeDrawBatch 之前：上传 CPU Buffer → GPU UBO，绑定到着色器
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer;"
            +
            "executeDrawBatch(Lnet/caffeinemc/mods/sodium/client/gl/device/CommandList;" +
            "Lnet/caffeinemc/mods/sodium/client/gl/tessellation/GlTessellation;" +
            "Lnet/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch;)V"))
    private void sre$uploadOffsetBuffer(
            ChunkRenderMatrices matrices,
            CommandList commandList,
            ChunkRenderListIterable renderLists,
            TerrainRenderPass renderPass,
            CameraTransform camera,
            CallbackInfo ci,
            @Local(ordinal = 0) ChunkShaderInterface shader,
            @Local(ordinal = 0) RenderRegion region) {

        if (sre$cpuBuffer == null) {
            // 理论上不应为 null（cleanup 注入出问题时的保底）
            sre$cpuBuffer = MemoryUtil.memAlloc(RenderRegion.REGION_SIZE * 16);
        }

        sre$gpuBuffer = commandList.createMutableBuffer();
        commandList.uploadData(sre$gpuBuffer, sre$cpuBuffer, GlBufferUsage.STREAM_DRAW);
        ((SRESceneryShaderInterface) shader).sre$setSceneryOffsets(sre$gpuBuffer);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // executeDrawBatch 之后：释放 GPU Buffer 和 CPU Buffer
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer;"
            +
            "executeDrawBatch(Lnet/caffeinemc/mods/sodium/client/gl/device/CommandList;" +
            "Lnet/caffeinemc/mods/sodium/client/gl/tessellation/GlTessellation;" +
            "Lnet/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch;)V", shift = At.Shift.AFTER))
    private void sre$cleanupOffsetBuffer(
            ChunkRenderMatrices matrices,
            CommandList commandList,
            ChunkRenderListIterable renderLists,
            TerrainRenderPass renderPass,
            CameraTransform camera,
            CallbackInfo ci) {

        if (sre$cpuBuffer != null) {
            MemoryUtil.memFree(sre$cpuBuffer);
            sre$cpuBuffer = null;
        }
        if (sre$gpuBuffer != null) {
            commandList.deleteBuffer(sre$gpuBuffer);
            sre$gpuBuffer = null;
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // fillCommandBuffer：为每个区块写入偏移量到 CPU Buffer
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 核心偏移计算逻辑。
     *
     * <p>
     * 对于非景色区块（列车本体 / playArea 内），写入 (0,0,0)，偏移为零。
     * </p>
     * <p>
     * 对于景色区块，写入 (areaOffset + scroll)，GPU 着色器自动叠加到顶点位置。
     * </p>
     *
     * <h4>偏移量含义</h4>
     * <ul>
     * <li>{@code areaOffset}：将景色区块从实际世界坐标视觉上平移到 sceneArea 中心附近。</li>
     * <li>{@code rawScroll}：沿 scrollAxis 方向的随时间滚动量，取模保证无缝循环。</li>
     * </ul>
     */
    @Inject(method = "fillCommandBuffer", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/data/SectionRenderDataUnsafe;getSliceMask(J)I"), remap = false)
    private static void sre$writeSceneryOffset(
            MultiDrawBatch batch,
            RenderRegion region,
            SectionRenderDataStorage renderDataStorage,
            ChunkRenderList renderList,
            CameraTransform camera,
            TerrainRenderPass pass,
            boolean useBlockFaceCulling,
            CallbackInfo ci,
            @Local(name = "sectionIndex") int sectionIndex) {

        // ── 保证缓冲区存在（render 的 cleanup 注入先于下一帧 fillCommandBuffer，正常情况无需重建）──
        if (sre$cpuBuffer == null) {
            sre$cpuBuffer = MemoryUtil.memAlloc(RenderRegion.REGION_SIZE * 16);
        }

        // ── 默认：偏移为零（不影响正常区块） ──
        int base = sectionIndex * 16;
        sre$cpuBuffer.putFloat(base, 0f);
        sre$cpuBuffer.putFloat(base + 4, 0f);
        sre$cpuBuffer.putFloat(base + 8, 0f);
        sre$cpuBuffer.putFloat(base + 12, 0f); // w 分量（填充，保持 std140 对齐）

        // ── 快速退出条件 ──
        if (!SREClient.isTrainMoving())
            return;
        if (SREConfig.instance().isUltraPerfMode())
            return;

        // ── 计算本区块在世界中的起点坐标（区块对齐，16 的倍数） ──
        int secX = region.getOriginX() + LocalSectionIndex.unpackX(sectionIndex) * 16;
        int secY = region.getOriginY() + LocalSectionIndex.unpackY(sectionIndex) * 16;
        int secZ = region.getOriginZ() + LocalSectionIndex.unpackZ(sectionIndex) * 16;

        // ── 跳过列车本体区块（Y section >= 4） ──
        if (SectionPos.blockToSectionCoord(secY) >= 4)
            return;

        // ── 跳过 playArea 内的区块（列车内部，不参与景色滚动） ──
        AABB playArea = SREClient.areaComponent.getPlayArea();
        if (playArea == null)
            return;
        // 使用区块中心点做包含检测（与旧版 sre$tryAddToCache 逻辑一致）
        double cx = secX + 8.0, cy = secY + 8.0, cz = secZ + 8.0;
        if (playArea.contains(cx, cy, cz))
            return;

        // ── 获取景色区域参数 ──
        AABB sceneArea = SREClient.areaComponent.getSceneArea();
        if (sceneArea == null)
            return;

        // areaOffset：sceneArea 中心相对 playArea 中心的偏移
        // 这是将景色从实际位置"搬移"到视觉上应当出现的位置所需的偏移
        Vec3 areaOffset = sceneArea.getCenter().subtract(playArea.getCenter());

        AreasWorldComponent.ScrollAxis scrollAxis = SREClient.areaComponent.SceneScrollAxis;
        float time = SREClient.trainComponent.getTime();
        float trainSpeed = SREClient.getTrainSpeed();

        // rawScroll：取模保证无缝循环；减去半个 TILE_SIZE 使循环中心对齐
        float rawScroll = (time * SPEED_FACTOR * trainSpeed) % TILE_SIZE - (TILE_SIZE / 2f);

        // ── 合成最终偏移量：areaOffset + 沿 scrollAxis 的滚动 ──
        float offX = (float) areaOffset.x + (scrollAxis == AreasWorldComponent.ScrollAxis.X ? rawScroll : 0f);
        float offY = (float) areaOffset.y + (scrollAxis == AreasWorldComponent.ScrollAxis.Y ? rawScroll : 0f);
        float offZ = (float) areaOffset.z + (scrollAxis == AreasWorldComponent.ScrollAxis.Z ? rawScroll : 0f);

        sre$cpuBuffer.putFloat(base, offX);
        sre$cpuBuffer.putFloat(base + 4, offY);
        sre$cpuBuffer.putFloat(base + 8, offZ);
        // base+12 (w) 保持 0f
    }
}