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

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.block_entity.DoorBlockEntity;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.util.AdventureUsable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Dream 的钻石镐（商店 90 金币）。
 *
 * <p>直接右键锁着的门：像开锁器一样直接打开（见 {@code SmallDoorBlock} 的
 * hasLockpick 判定），<b>无法锁门</b>。
 * 潜行 + 右键：直接撬开门（同消防斧的撬门，50s 冷却）。
 * 撬门破坏动静很大 —— 会额外播放响亮的破坏声，附近玩家都能听到。
 */
public class DreamPickaxeItem extends Item implements AdventureUsable {
    /** 撬门冷却（50s）。 */
    private static final int PRY_COOLDOWN_TICKS = 50 * 20;

    public DreamPickaxeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        Player player = context.getPlayer();

        // 潜行 + 右键：撬门（不提供任何锁门能力）
        if (player != null && player.isShiftKeyDown()) {
            BlockEntity entity = world.getBlockEntity(context.getClickedPos());
            if (!(entity instanceof DoorBlockEntity)) {
                entity = world.getBlockEntity(context.getClickedPos().below());
            }

            if (entity instanceof DoorBlockEntity door && !door.isBlasted()) {
                if (player.getCooldowns().isOnCooldown(this)) {
                    if (!world.isClientSide) {
                        player.displayClientMessage(
                                net.minecraft.network.chat.Component
                                        .translatable("item.noellesroles.dream_pickaxe.on_cooldown")
                                        .withStyle(net.minecraft.ChatFormatting.RED),
                                true);
                    }
                    return InteractionResult.FAIL;
                }
                if (!player.isCreative()) {
                    player.getCooldowns().addCooldown(this, PRY_COOLDOWN_TICKS);
                }
                world.playSound(null, context.getClickedPos(), TMMSounds.ITEM_CROWBAR_PRY,
                        SoundSource.BLOCKS, 2.5f, 1f);
                // 额外破坏声：钻石镐撬门动静很大
                world.playSound(null, context.getClickedPos(), SoundEvents.ANVIL_LAND,
                        SoundSource.BLOCKS, 3.0f, 0.6f);
                world.playSound(null, context.getClickedPos(), SoundEvents.WOOD_BREAK,
                        SoundSource.BLOCKS, 3.0f, 0.7f);
                player.swing(InteractionHand.MAIN_HAND, true);

                if (!world.isClientSide) {
                    if (SRE.REPLAY_MANAGER != null) {
                        SRE.REPLAY_MANAGER.recordItemUse(player.getUUID(),
                                BuiltInRegistries.ITEM.getKey(this));
                    }
                    door.blast();
                }
                return InteractionResult.SUCCESS;
            }
        }

        return super.useOn(context);
    }
}
