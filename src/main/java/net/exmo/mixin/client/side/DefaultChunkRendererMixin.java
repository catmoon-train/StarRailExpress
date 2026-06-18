package net.exmo.mixin.client.side;

import java.nio.ByteBuffer;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
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
import net.caffeinemc.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DefaultChunkRenderer.class)
public abstract class DefaultChunkRendererMixin {
    @Unique
    private static final int sre$offsetCount =
            RenderRegion.REGION_SIZE * ModelQuadFacing.COUNT + 1;
    @Unique
    private static ByteBuffer sre$offsetBuffer;
    @Unique
    private static GlMutableBuffer sre$glBuffer;

    @Inject(method = "fillCommandBuffer", at = @At("HEAD"), remap = false)
    private static void sre$prepareOffsetBuffer(
            MultiDrawBatch batch,
            RenderRegion region,
            SectionRenderDataStorage renderDataStorage,
            ChunkRenderList renderList,
            CameraTransform camera,
            TerrainRenderPass pass,
            boolean useBlockFaceCulling,
            CallbackInfo ci) {
        if (sre$offsetBuffer != null) {
            MemoryUtil.memFree(sre$offsetBuffer);
        }
        sre$offsetBuffer = MemoryUtil.memCalloc(sre$offsetCount * 16);
    }

    @Inject(
            method = "fillCommandBuffer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer;addNonIndexedDrawCommands(Lnet/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch;JI)V",
                    shift = At.Shift.AFTER),
            remap = false)
    private static void sre$writeNonIndexedSceneOffset(
            MultiDrawBatch batch,
            RenderRegion region,
            SectionRenderDataStorage renderDataStorage,
            ChunkRenderList renderList,
            CameraTransform camera,
            TerrainRenderPass pass,
            boolean useBlockFaceCulling,
            CallbackInfo ci,
            @Local(name = "chunkX") int sectionX,
            @Local(name = "chunkY") int sectionY,
            @Local(name = "chunkZ") int sectionZ,
            @Local(name = "slices") int slices) {
        int firstCommand = batch.size() - Integer.bitCount(slices);
        sre$writeSceneOffset(batch, sectionX, sectionY, sectionZ, firstCommand);
    }

    @Inject(
            method = "fillCommandBuffer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer;addIndexedDrawCommands(Lnet/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch;JI)V",
                    shift = At.Shift.AFTER),
            remap = false)
    private static void sre$writeIndexedSceneOffset(
            MultiDrawBatch batch,
            RenderRegion region,
            SectionRenderDataStorage renderDataStorage,
            ChunkRenderList renderList,
            CameraTransform camera,
            TerrainRenderPass pass,
            boolean useBlockFaceCulling,
            CallbackInfo ci,
            @Local(name = "chunkX") int sectionX,
            @Local(name = "chunkY") int sectionY,
            @Local(name = "chunkZ") int sectionZ,
            @Local(name = "slices") int slices) {
        int firstCommand = batch.size() - Integer.bitCount(slices);
        sre$writeSceneOffset(batch, sectionX, sectionY, sectionZ, firstCommand);
    }

    @Unique
    private static void sre$writeSceneOffset(MultiDrawBatch batch, int sectionX, int sectionY,
            int sectionZ, int firstCommand) {
        if (sre$offsetBuffer == null
                || !SceneAssetClient.isActiveSection(sectionX, sectionY, sectionZ)) {
            return;
        }
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        Vec3 offset = SceneAssetClient.renderOffset(partialTick);
        int lastCommand = Math.min(batch.size(), sre$offsetCount);
        for (int command = firstCommand; command < lastCommand; command++) {
            int base = command * 16;
            sre$offsetBuffer.putFloat(base, (float) offset.x);
            sre$offsetBuffer.putFloat(base + 4, (float) offset.y);
            sre$offsetBuffer.putFloat(base + 8, (float) offset.z);
        }
    }

    @ModifyExpressionValue(
            method = "fillCommandBuffer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer;getVisibleFaces(IIIIII)I"),
            remap = false)
    private static int sre$keepSceneFaces(
            int original,
            @Local(name = "chunkX") int sectionX,
            @Local(name = "chunkY") int sectionY,
            @Local(name = "chunkZ") int sectionZ) {
        return SceneAssetClient.isActiveSection(sectionX, sectionY, sectionZ)
                ? ModelQuadFacing.ALL
                : original;
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
            CallbackInfo ci,
            @Local(ordinal = 0) ChunkShaderInterface shader) {
        if (sre$offsetBuffer == null) {
            return;
        }
        sre$glBuffer = commandList.createMutableBuffer();
        commandList.uploadData(sre$glBuffer, sre$offsetBuffer, GlBufferUsage.STREAM_DRAW);
        ((SodiumShaderInterface) shader).tmm$set(sre$glBuffer);
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
            CallbackInfo ci) {
        if (sre$glBuffer != null) {
            commandList.deleteBuffer(sre$glBuffer);
            sre$glBuffer = null;
        }
        if (sre$offsetBuffer != null) {
            MemoryUtil.memFree(sre$offsetBuffer);
            sre$offsetBuffer = null;
        }
    }
}
