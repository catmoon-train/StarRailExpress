package org.agmas.noellesroles.mixin.client.map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.agmas.noellesroles.client.map.AreaMapManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 单方块、批量网络更新和客户端造墙均在真正写入区块后使地图局部失效。 */
@Mixin(LevelChunk.class)
public abstract class AreaMapBlockUpdateMixin {
    @Shadow @Final private Level level;

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void noellesroles$invalidateMap(BlockPos pos, BlockState state, boolean moved,
                                           CallbackInfoReturnable<BlockState> cir) {
        if (level instanceof ClientLevel client && cir.getReturnValue() != null && cir.getReturnValue() != state) {
            AreaMapManager.invalidateColumn(client, pos);
        }
    }
}
