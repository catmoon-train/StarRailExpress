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
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.block.SmallDoorBlock;
import io.wifi.starrailexpress.content.block_entity.SmallDoorBlockEntity;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.DoorCustomOpenItem;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.util.AdventureUsable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.role.touhou.THRedHouseRoles;

public class CrowbarItem extends Item implements AdventureUsable, DoorCustomOpenItem {
    public CrowbarItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        BlockPos lowerPos = context.getClickedPos();
        BlockEntity entity = world.getBlockEntity(lowerPos);
        if (!(entity instanceof SmallDoorBlockEntity)) {
            lowerPos = lowerPos.below();
            entity = world.getBlockEntity(lowerPos);
            if (!(entity instanceof SmallDoorBlockEntity)) {
                // 不是门，跳过
                return InteractionResult.PASS;
            }
        }
        BlockState state = world.getBlockState(lowerPos);
        Player player = context.getPlayer();
        var gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (entity instanceof SmallDoorBlockEntity door && !door.isBlasted() && player != null) {
            world.playSound(null, context.getClickedPos(), TMMSounds.ITEM_CROWBAR_PRY, SoundSource.BLOCKS, 2.5f, 1f);
            player.swing(InteractionHand.MAIN_HAND, true);

            if (!player.isCreative()) {
                if (SRE.REPLAY_MANAGER != null) {
                    SRE.REPLAY_MANAGER.recordItemUse(player.getUUID(), BuiltInRegistries.ITEM.getKey(this));
                }
                if (gameWorldComponent.isRole(player, TMMRoles.LOOSE_END)) {
                    player.getCooldowns().addCooldown(this,
                            GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.CROWBAR, 45 * 20) / 4);
                } else if (gameWorldComponent.isRole(player, THRedHouseRoles.FURANDORU)) {
                    player.getCooldowns().addCooldown(this,
                            GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.CROWBAR, 45 * 20) / 6);
                } else {
                    player.getCooldowns().addCooldown(this,
                            GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.CROWBAR, 45 * 20));
                }
            }
            if (state.getBlock() instanceof SmallDoorBlock sb) {
                if (!sb.isOpen(state))
                    sb.open(state, world, door, context.getClickedPos());
            }
            door.blast();
            // 记录撬门事件（低频关键事件）
            if (!world.isClientSide && SRE.REPLAY_MANAGER != null) {
                SRE.REPLAY_MANAGER.recordDoorPry(player.getUUID(), context.getClickedPos());
            }
        }
        return super.useOn(context);
    }
}
