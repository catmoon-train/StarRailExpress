package net.exmo.mixin.client.side;

import io.wifi.starrailexpress.scenery.client.SceneAssetClient;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientChunkCache.class)
public abstract class ClientChunkCacheMixin {
    @Inject(
            method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/LevelChunk;",
            at = @At("RETURN"),
            cancellable = true)
    private void sre$getSceneChunk(int chunkX, int chunkZ, ChunkStatus status, boolean load,
            CallbackInfoReturnable<LevelChunk> cir) {
        LevelChunk sceneChunk = SceneAssetClient.getRemoteChunk(chunkX, chunkZ);
        LevelChunk vanillaChunk = cir.getReturnValue();
        if (sceneChunk != null && (vanillaChunk == null || vanillaChunk instanceof EmptyLevelChunk)) {
            cir.setReturnValue(sceneChunk);
        }
    }
}
