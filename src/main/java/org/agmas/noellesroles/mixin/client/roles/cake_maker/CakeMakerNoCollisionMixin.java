package org.agmas.noellesroles.mixin.client.roles.cake_maker;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.agmas.noellesroles.client.ClientCakeMakerBlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes client-side Cake Maker blocks (smoker / cake) passable for players.
 */
@Mixin(BlockState.class)
public class CakeMakerNoCollisionMixin {

    @Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("HEAD"), cancellable = true)
    private void noCollisionForCakeMakerBlocks(BlockGetter level, BlockPos pos, CollisionContext context,
                                               CallbackInfoReturnable<VoxelShape> cir) {
        if (!(level instanceof ClientLevel) || !(context instanceof EntityCollisionContext)) {
            return;
        }
        if (ClientCakeMakerBlocks.isAt(pos)) {
            cir.setReturnValue(Shapes.empty());
        }
    }
}
