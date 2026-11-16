package io.wifi.srepackcompat.mixin.spawning;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** POI 的栖息地记录可能位于未加载区块，检测刷新影响时禁止同步加载它。 */
@Pseudo
@Mixin(targets = "com.cobblemon.mod.common.api.spawning.influence.detector.HabitatBlockDetector", remap = false)
public class HabitatBlockDetectorMixin {
    @Redirect(method = "detectFromInput", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
            remap = true), require = 1)
    private BlockEntity sre$loadedHabitatOnly(ServerLevel level, BlockPos pos) {
        // getChunk(..., false) 仍可能等待已提交的区块任务；这里只查询当前可用区块。
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK);
    }
}
