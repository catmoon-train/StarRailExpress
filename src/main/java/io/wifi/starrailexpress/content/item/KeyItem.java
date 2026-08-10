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

package io.wifi.starrailexpress.content.item;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.block.SmallDoorBlock;
import io.wifi.starrailexpress.content.block_entity.SmallDoorBlockEntity;
import io.wifi.starrailexpress.util.AdventureUsable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.List;

public class KeyItem extends Item implements AdventureUsable {
    public KeyItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = world.getBlockState(pos);

        if (state.getBlock() instanceof SmallDoorBlock) {
            BlockPos lowerPos = state.getValue(SmallDoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
            if (world.getBlockEntity(lowerPos) instanceof SmallDoorBlockEntity entity) {
                ItemStack mainHandStack = player.getMainHandItem();
                ItemLore loreComponent = mainHandStack.get(DataComponents.LORE);
                if (loreComponent != null) {
                    List<Component> lines = loreComponent.lines();
                    if (lines == null || lines.isEmpty()) {
                        return InteractionResult.PASS;
                    }

                    // Sneaking creative player with key sets the door to require a key with the same name
                    if (player.isCreative() && player.isShiftKeyDown()) {
                        String roomName = lines.getFirst().getString();
                        entity.setKeyName(roomName);
                        entity.setChanged();
                        player.displayClientMessage(Component.translatable("message.starrailexpress.key.bound_to_door", roomName), true);
                        if (SRE.REPLAY_MANAGER != null) {
                            SRE.REPLAY_MANAGER.recordItemUse(player.getUUID(), BuiltInRegistries.ITEM.getKey(this));
                        }
                        return InteractionResult.SUCCESS;
                    }
                }
            }

            return InteractionResult.PASS;
        }
        return super.useOn(context);
    }
}
