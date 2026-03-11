package io.wifi.starrailexpress.mixin.compat.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = DefaultChunkRenderer.class)
public abstract class DefaultChunkRendererMixin {
//    @Unique
//    private static ByteBuffer tmm_buffer = MemoryUtil.memAlloc(RenderRegion.REGION_SIZE * 16);
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
//    private boolean tmm$disable_culling(boolean original) {
//        if (TMMClient.isTrainMoving()) {
//            return false;
//        }
//        return original;
//    }
//
//    @Inject(method = "render", at = @At(value = "INVOKE",
//            target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer;executeDrawBatch(Lnet/caffeinemc/mods/sodium/client/gl/device/CommandList;Lnet/caffeinemc/mods/sodium/client/gl/tessellation/GlTessellation;Lnet/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch;)V"),
//            remap = false)
//    private void modifyChunkRenderBefore(ChunkRenderMatrices matrices,
//                                         CommandList commandList,
//                                         ChunkRenderListIterable renderLists,
//                                         TerrainRenderPass renderPass,
//                                         CameraTransform camera,
//                                         CallbackInfo ci,
//                                         @Local(ordinal = 0) ChunkShaderInterface shader,
//                                         @Local(ordinal = 0) RenderRegion region) {
//        glBuffer = commandList.createMutableBuffer();
//        commandList.uploadData(glBuffer, tmm_buffer, GlBufferUsage.STREAM_DRAW);
//
//        ((SodiumShaderInterface) shader).tmm$set(glBuffer);
//    }
//
//    @Inject(method = "render", at = @At(value = "INVOKE",
//            target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer;executeDrawBatch(Lnet/caffeinemc/mods/sodium/client/gl/device/CommandList;Lnet/caffeinemc/mods/sodium/client/gl/tessellation/GlTessellation;Lnet/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch;)V",
//            shift = At.Shift.AFTER),
//            remap = false)
//    private void modifyChunkRenderAfter(ChunkRenderMatrices matrices,
//                                        CommandList commandList,
//                                        ChunkRenderListIterable renderLists,
//                                        TerrainRenderPass renderPass,
//                                        CameraTransform camera,
//                                        CallbackInfo ci) {
//        MemoryUtil.memFree(tmm_buffer);
//        commandList.deleteBuffer(glBuffer);
//        tmm_buffer = null;
//    }
//
//    @Inject(method = "fillCommandBuffer",
//            at = @At(value = "INVOKE",
//                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/data/SectionRenderDataUnsafe;getSliceMask(J)I"),
//            remap = false)
//    private static void tmm$offsetScenery(
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
//        if (tmm_buffer == null) {
//            tmm_buffer = MemoryUtil.memAlloc(RenderRegion.REGION_SIZE * 16);
//        }
//        tmm_buffer.putFloat(sectionIndex * 16, 0);
//        tmm_buffer.putFloat(sectionIndex * 16 + 4, 0);
//        tmm_buffer.putFloat(sectionIndex * 16 + 8, 0);
//
//        if (TMMClient.isTrainMoving()) {
//            float trainSpeed = TMMClient.getTrainSpeed();
//            int chunkSize = 16;
//            int tileWidth = 15 * chunkSize;
//            int height = 116;
//            int tileLength = 32 * chunkSize;
//            int tileSize = tileLength * 3;
//            float time = TMMClient.trainComponent.getTime() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
//
//            BlockPos blockPos = new BlockPos(
//                    region.getOriginX() + LocalSectionIndex.unpackX(sectionIndex) * 16,
//                    region.getOriginY() + LocalSectionIndex.unpackY(sectionIndex) * 16,
//                    region.getOriginZ() + LocalSectionIndex.unpackZ(sectionIndex) * 16
//            );
//
//            boolean trainSection = SectionPos.blockToSectionCoord(blockPos.getY()) >= 4;
//            float v1 = (float) ((double) blockPos.getX() - camera.fracX);
//            float v2 = (float) ((double) blockPos.getY() - camera.fracY);
//            float v3 = (float) ((double) blockPos.getZ() - camera.fracZ);
//            int zSection = blockPos.getZ() / chunkSize - SectionPos.blockToSectionCoord(camera.intZ);
//
//            float finalX = v1;
//            float finalY = v2;
//            float finalZ = v3;
//
//            if (zSection <= -8) {
//                finalX = ((v1 - tileLength + ((time) / 73.8f * trainSpeed)) % tileSize - tileSize / 2f);
//                finalY = (v2 + height);
//                finalZ = v3 + tileWidth;
//            } else if (zSection >= 8) {
//                finalX = ((v1 + tileLength + ((time) / 73.8f * trainSpeed)) % tileSize - tileSize / 2f);
//                finalY = (v2 + height);
//                finalZ = v3 - tileWidth;
//            } else if (!trainSection) {
//                finalX = ((v1 + ((time) / 73.8f * trainSpeed)) % tileSize - tileSize / 2f);
//                finalY = (v2 + height); // + zSection * 16;
//                finalZ = v3;
//            }
//
//            finalX = (blockPos.getX() - finalX) - camera.fracX;
//            finalY = (blockPos.getY() - finalY) - camera.fracY;
//            finalZ = (blockPos.getZ() - finalZ) - camera.fracZ;
//
//            tmm_buffer.putFloat(sectionIndex * 16, -finalX);
//            tmm_buffer.putFloat(sectionIndex * 16 + 4, -finalY);
//            tmm_buffer.putFloat(sectionIndex * 16 + 8, -finalZ);
//        }
//    }
}
