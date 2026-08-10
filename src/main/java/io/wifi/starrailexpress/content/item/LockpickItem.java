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
import io.wifi.starrailexpress.content.block.LockableButtonBlock;
import io.wifi.starrailexpress.content.block.SmallDoorBlock;
import io.wifi.starrailexpress.content.block_entity.LockableButtonBlockEntity;
import io.wifi.starrailexpress.content.block_entity.SmallDoorBlockEntity;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.util.AdventureUsable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public class LockpickItem extends Item implements AdventureUsable {
    public LockpickItem(Properties settings) {
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
                if (player.isShiftKeyDown()) {
                    entity.jam();
                    jamNearBy(context);

                    // 记录上锁事件（低频关键事件），替代原先的通用物品使用记录以避免重复刷屏
                    if (!world.isClientSide && SRE.REPLAY_MANAGER != null) {
                        SRE.REPLAY_MANAGER.recordDoorSeal(player.getUUID(), lowerPos);
                    }

                    if (!player.isCreative()) {
                        player.getCooldowns().addCooldown(this,
                               getCooldownTicks());
                    }

                    if (!world.isClientSide)
                        world.playSound(null, lowerPos.getX() + .5f, lowerPos.getY() + 1, lowerPos.getZ() + .5f,
                                TMMSounds.ITEM_LOCKPICK_DOOR, SoundSource.BLOCKS, 1f, 1f);
                    return InteractionResult.SUCCESS;
                }
            }

            return InteractionResult.PASS;
        } else if (state.getBlock() instanceof LockableButtonBlock) {
            BlockPos lowerPos = pos;
            if (world.getBlockEntity(lowerPos) instanceof LockableButtonBlockEntity entity) {
                if (player.isShiftKeyDown()) {
                    entity.jam();

                    if (!player.isCreative()) {
                        if (SRE.REPLAY_MANAGER != null) {
                            SRE.REPLAY_MANAGER.recordItemUse(player.getUUID(), BuiltInRegistries.ITEM.getKey(this));
                        }
                        player.getCooldowns().addCooldown(this,
                                getCooldownTicks());
                    }

                    if (!world.isClientSide)
                        world.playSound(null, lowerPos.getX() + .5f, lowerPos.getY() + 1, lowerPos.getZ() + .5f,
                                TMMSounds.ITEM_LOCKPICK_DOOR, SoundSource.BLOCKS, 1f, 1f);
                    return InteractionResult.SUCCESS;
                }
            }

            return InteractionResult.PASS;
        }

        return super.useOn(context);
    }

    public int getCooldownTicks() {
        return GameConstants.ITEM_COOLDOWNS.getOrDefault(this, 40);
    }
    
    public int getOpenCooldownTicks() {
        return 0;
    }

    private void jamNearBy(UseOnContext context) {
        Level world = context.getLevel();
        BlockPos clickpos = context.getClickedPos();
        Vec3i offsets[] = { new Vec3i(0, 0, -1), new Vec3i(0, 0, 1), new Vec3i(-1, 0, 0), new Vec3i(1, 0, 0) };
        for (int i = 0; i < offsets.length; i++) {
            BlockPos pos = clickpos.offset(offsets[i]);
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof SmallDoorBlock) {
                BlockPos lowerPos = state.getValue(SmallDoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
                if (world.getBlockEntity(lowerPos) instanceof SmallDoorBlockEntity entity) {
                    entity.jam();
                }
            }
        }
    }
}
