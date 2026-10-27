package org.agmas.noellesroles.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.client.DreadlordClientFx;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Sodium 网格同样把浮起的方块原位置视为空气。 */
@Mixin(LevelSlice.class)
public abstract class DreadlordFloatSodiumMixin {
    @ModifyReturnValue(method = "getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN"), remap = false)
    private BlockState noellesroles$hideLiftedBlock(BlockState original, int x, int y, int z) {
        if (!original.isAir() && DreadlordClientFx.hides(x, y, z)) {
            return Blocks.AIR.defaultBlockState();
        }
        return original;
    }
}
