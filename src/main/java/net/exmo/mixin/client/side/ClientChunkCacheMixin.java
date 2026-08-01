/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
