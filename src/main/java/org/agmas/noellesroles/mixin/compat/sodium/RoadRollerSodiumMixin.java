package org.agmas.noellesroles.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.client.RoadRollerClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Sodium 区块缓存也应用相同的临时隐藏，不改底层世界快照。 */
@Mixin(LevelSlice.class)
public abstract class RoadRollerSodiumMixin {
    @ModifyReturnValue(method = "getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN"), remap = false)
    private BlockState noellesroles$roadRollerBlock(BlockState original, int x, int y, int z) {
        return RoadRollerClient.hides(x, y, z) ? Blocks.AIR.defaultBlockState() : original;
    }

}
