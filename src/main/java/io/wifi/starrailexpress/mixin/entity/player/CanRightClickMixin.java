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

package io.wifi.starrailexpress.mixin.entity.player;

import dev.upcraft.datasync.api.ext.DataSyncPlayerExt;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.CantRightClickBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class CanRightClickMixin extends LivingEntity implements DataSyncPlayerExt {
    protected CanRightClickMixin(EntityType<? extends LivingEntity> entityType, Level world) {
        super(entityType, world);
    }

    @Inject(method = "canInteractWithBlock", at = @At("TAIL"), cancellable = true)
    public void canInteractWithBlockAt(BlockPos pos, double additionalRange,
            CallbackInfoReturnable<Boolean> cir) {
        if (SRE.isLobby)
            return;
        if (!cir.getReturnValue())
            return;
        final var player = (Player) (Object) this;
        if (!GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player)) {
            return;
        }

        BlockState state = level().getBlockState(pos);
        if (state.is(Blocks.LECTERN)) {
            if (!state.getValue(LecternBlock.HAS_BOOK)) {
                cir.setReturnValue(false);
                return;
            }
        }
        Block block = state.getBlock();

        if (CantRightClickBlocks.shouldPreventInteraction(player, block, player.level())) {
            cir.setReturnValue(false);
        }
    }

}