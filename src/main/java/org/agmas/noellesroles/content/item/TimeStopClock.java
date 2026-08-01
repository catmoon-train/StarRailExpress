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

import io.wifi.starrailexpress.util.ItemComponentUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.content.effects.TimeStopEffect;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * 时停钟
 * <p>
 * 可以触发时停效果
 * </p>
 */
public class TimeStopClock extends Item {
    public static final String TAG_STOP_TIME = "StopTime";
    public static final String TAG_COOLDOWN = "CoolDown";
    public static final int MAX_DURABILITY = 100;
    public static final int DEFAULT_STOP_TIME = 100;
    public static final int DEFAULT_COOL_DOWN = 300;

    public TimeStopClock(Properties properties) {
        super(properties);
    }

    @Override
    public @NonNull ItemStack getDefaultInstance() {
        // 创建带默认组件的物品栈
        ItemStack stack = new ItemStack(this);
        stack.set(DataComponents.CUSTOM_DATA, getDefaultCustomData());
        return stack;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof TimeStopClock) {
            if (!world.isClientSide) {
                if (!TimeStopEffect.tryTriggerStart((ServerPlayer) player,
                        ItemComponentUtils.getCustomDataTagIntValue(stack, TAG_STOP_TIME),
                        Component.translatable("item.noellesroles.time_stop_clock")
                                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD))) {
                    return InteractionResultHolder.fail(stack);
                }
                if (!player.isCreative()) {
                    player.getCooldowns().addCooldown(this,
                            ItemComponentUtils.getCustomDataTagIntValue(stack, TAG_COOLDOWN));
                    stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
                }
                return InteractionResultHolder.success(stack);
            }
        }
        return InteractionResultHolder.fail(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.translatable("item.noellesroles.time_stop_clock.tooltip",
                ItemComponentUtils.getCustomDataTagIntValue(stack, TAG_STOP_TIME) / 20,
                (MAX_DURABILITY - stack.getDamageValue()))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.noellesroles.time_stop_clock.tooltip.cooldown",
                ItemComponentUtils.getCustomDataTagIntValue(stack, TAG_COOLDOWN) / 20));
        super.appendHoverText(stack, context, tooltip, type);
    }

    public static CustomData getDefaultCustomData() {
        CompoundTag defaultTag = new CompoundTag();
        defaultTag.putInt(TAG_STOP_TIME, DEFAULT_STOP_TIME);
        defaultTag.putInt(TAG_COOLDOWN, DEFAULT_COOL_DOWN);

        return CustomData.of(defaultTag);
    }
}
