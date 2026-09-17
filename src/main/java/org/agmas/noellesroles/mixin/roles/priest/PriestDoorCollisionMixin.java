package org.agmas.noellesroles.mixin.roles.priest;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.block.DoorPartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 神父无视门碰撞，可从关闭的门穿过。
 */
@Mixin(DoorPartBlock.class)
public class PriestDoorCollisionMixin {

    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void noellesroles$priestPassDoors(BlockState state, BlockGetter world, BlockPos pos,
            CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (!(context instanceof EntityCollisionContext entityContext)) {
            return;
        }
        Entity entity = entityContext.getEntity();
        if (!(entity instanceof Player player)) {
            return;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.maybeGet(player.level()).orElse(null);
        if (game != null && game.isRole(player, ModRoles.PRIEST)) {
            cir.setReturnValue(Shapes.empty());
        }
    }
}
