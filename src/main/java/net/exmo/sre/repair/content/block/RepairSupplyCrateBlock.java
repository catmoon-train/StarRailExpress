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

package net.exmo.sre.repair.content.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.exmo.sre.repair.util.RepairGameplayEffects;
import net.exmo.sre.repair.state.RepairModeState;
import org.agmas.noellesroles.init.ModItems;

public class RepairSupplyCrateBlock extends Block {
    public static final BooleanProperty OPENED = BooleanProperty.create("opened");

    public RepairSupplyCrateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OPENED, false));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult) {
        return openCrate(state, level, pos, player) ? ItemInteractionResult.SUCCESS
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        openCrate(state, level, pos, player);
        return InteractionResult.SUCCESS;
    }

    private boolean openCrate(BlockState state, Level level, BlockPos pos, Player player) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        if (!RepairModeState.canUseSurvivorUtility(serverPlayer)) {
            return true;
        }
        if (state.getValue(OPENED)) {
            serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.repair.crate_empty")
                    .withStyle(ChatFormatting.GRAY), true);
            return true;
        }
        Item item = randomReward(serverLevel.random);
        ItemStack reward = new ItemStack(item);
        if (item == ModItems.SPARE_PARTS) {
            reward.setCount(2 + serverLevel.random.nextInt(3));
        }
        serverPlayer.addItem(reward);
        RepairModeState.awardCoins(serverPlayer, 20, "repair_coin_source.boost");
        RepairModeState.addNeutralTaskProgress(serverPlayer, "collector", 1, RepairModeState.COLLECTOR_TASK_NEEDED);
        level.setBlockAndUpdate(pos, state.setValue(OPENED, true));
        RepairGameplayEffects.burst(serverLevel, pos.getX() + 0.5D, pos.getY() + 0.8D, pos.getZ() + 0.5D, 0);
        serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.repair.crate_reward",
                reward.getHoverName()).withStyle(ChatFormatting.GREEN), true);
        serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.repair.coin_reward", 20)
                .withStyle(ChatFormatting.GOLD), true);
        return true;
    }

    private Item randomReward(RandomSource random) {
        return switch (random.nextInt(5)) {
            case 0 -> ModItems.SMOKE_PELLET;
            case 1 -> ModItems.DECOY_BEACON;
            case 2 -> ModItems.ESCAPE_GRAPPLE;
            case 3 -> ModItems.REPAIR_TOOLBOX;
            default -> ModItems.SPARE_PARTS;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPENED);
    }
}
