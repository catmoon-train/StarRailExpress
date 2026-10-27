package org.agmas.noellesroles.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.agmas.noellesroles.game.modes.echo.map.EchoTerrainRevision;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fabric 没有覆盖命令/活塞/流体写块的统一事件；只观察已成功的真实修改，不干预原行为。 */
@Mixin(LevelChunk.class)
public abstract class EchoRouteTerrainMixin {
    @Inject(method="setBlockState",at=@At("RETURN"))
    private void echo$terrainChanged(BlockPos pos,BlockState state,boolean moving,CallbackInfoReturnable<BlockState> cir) {
        if(cir.getReturnValue()!=null && cir.getReturnValue()!=state && ((LevelChunk)(Object)this).getLevel() instanceof ServerLevel level)
            EchoTerrainRevision.changed(level,pos);
    }
}
