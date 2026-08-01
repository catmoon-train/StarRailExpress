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

package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.content.block.SmallDoorBlock;
import io.wifi.starrailexpress.content.block_entity.DoorBlockEntity;
import io.wifi.starrailexpress.content.block_entity.SmallDoorBlockEntity;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.DoorCustomOpenItem;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.util.AdventureUsable;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public class ArtisanKeyItem extends Item implements AdventureUsable,DoorCustomOpenItem {
    public ArtisanKeyItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        BlockPos clickedPos = context.getClickedPos();
        BlockState clickedState = world.getBlockState(clickedPos);
        BlockPos lowerPos = clickedPos;
        if (clickedState.getBlock() instanceof SmallDoorBlock) {
            lowerPos = clickedState.getValue(SmallDoorBlock.HALF) == DoubleBlockHalf.LOWER ? clickedPos
                    : clickedPos.below();
        }

        if (!(world.getBlockEntity(lowerPos) instanceof DoorBlockEntity doorEntity)) {
            return InteractionResult.PASS;
        }
        if (doorEntity.isBlasted()) {
            return InteractionResult.FAIL;
        }
        if (!(doorEntity instanceof SmallDoorBlockEntity smallDoorEntity)) {
            return InteractionResult.PASS;
        }

        // 巧匠钥匙强制清除卡门并直接切换门状态，不改动门上的附加道具。
        doorEntity.setJammed(0);
        BlockState lowerState = world.getBlockState(lowerPos);
        if (!(lowerState.getBlock() instanceof SmallDoorBlock)) {
            return InteractionResult.PASS;
        }

        world.playSound(null, lowerPos.getX() + 0.5, lowerPos.getY() + 1, lowerPos.getZ() + 0.5,
                TMMSounds.ITEM_KEY_DOOR, SoundSource.BLOCKS, 1f, 1f);
        if (lowerState.getBlock() instanceof SmallDoorBlock sb)
            sb.toggleDoor(lowerState, world, smallDoorEntity, lowerPos);
        return InteractionResult.sidedSuccess(world.isClientSide);
    }
}
