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

package io.wifi.starrailexpress.content.block;

import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

public abstract class ToggleableFacingLightBlock extends FacingLightBlock {

    public ToggleableFacingLightBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(super.defaultBlockState()
                .setValue(LIT, false));
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!player.isSecondaryUseActive()) {
            boolean lit = state.getValue(LIT);
            world.setBlock(pos, state.setValue(LIT, !lit), Block.UPDATE_ALL);
            world.playSound(null, pos, TMMSounds.BLOCK_LIGHT_TOGGLE, SoundSource.BLOCKS, 0.5f, lit ? 1f : 1.2f);
            if (!state.getValue(ACTIVE)) {
                world.playSound(player, pos, TMMSounds.BLOCK_BUTTON_TOGGLE_NO_POWER, SoundSource.BLOCKS, 0.1f, 1f);
            }
            return InteractionResult.sidedSuccess(world.isClientSide);
        }
        return super.useWithoutItem(state, world, pos, player, hit);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
        super.createBlockStateDefinition(builder);
    }
}
