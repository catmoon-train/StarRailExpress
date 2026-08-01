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
import io.wifi.starrailexpress.cca.ExtraSlotComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.init.ModItems;

public class HandCuffsItem extends Item {
    public static final int MAX_DAMAGE = 10;
    public HandCuffsItem(Item.Properties settings) {
        super(settings.durability(10));
    }

    public static final ResourceLocation SLOT_HANDCUFFS = SRE.id("handcuffs");

    public static void putOnHandCuff(Player player, ItemStack stack) {
        ExtraSlotComponent.setSlot(player, SLOT_HANDCUFFS, stack);
    }

    public static ItemStack getHandCuffItemStack(Player player) {
        return ExtraSlotComponent.getSlot(player, SLOT_HANDCUFFS);
    }

    public static ItemStack putOffHandCuff(Player player) {
        return ExtraSlotComponent.removeSlot(player, SLOT_HANDCUFFS);
    }

    public static ItemStack breakHandCuff(Player player) {
        var stack = ExtraSlotComponent.getSlot(player, SLOT_HANDCUFFS);
        if(stack.is(ModItems.HANDCUFFS)){
            ExtraSlotComponent.hurtAndBreak(player,stack,MAX_DAMAGE,SLOT_HANDCUFFS);
            return ExtraSlotComponent.removeSlot(player, SLOT_HANDCUFFS);
        }
        return ItemStack.EMPTY;
    }

    public static boolean hasHandCuff(Player player) {
        return ExtraSlotComponent.getSlot(player, SLOT_HANDCUFFS).is(ModItems.HANDCUFFS);
    }

    @Override
    public void inventoryTick(ItemStack itemStack, Level level, Entity entity, int slot, boolean bl) {
        if (itemStack.is(ModItems.HANDCUFFS)) {
            if (entity instanceof Player player) {
                if (hasHandCuff(player)) {
                    if (!player.isSpectator()) {
                        player.addEffect(new MobEffectInstance(
                                MobEffects.MOVEMENT_SLOWDOWN,
                                (int) (20), // 持续时间（tick）
                                3, // 等级（0 = 速度 I）
                                false, // ambient（环境效果，如信标）
                                true, // showParticles（显示粒子）
                                true // showIcon（显示图标）
                        ));
                    }

                    if (!level.isClientSide && player.isShiftKeyDown() && level.getGameTime() % 20 == 0) {
                        ExtraSlotComponent.hurtAndBreak(player, itemStack, 1, SLOT_HANDCUFFS);
                    }
                }
            }
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand interactionHand) {

        if (hasHandCuff(user))
            return InteractionResultHolder.pass(user.getItemInHand(interactionHand));
        if (user.getCooldowns().isOnCooldown(ModItems.HANDCUFFS))
            return InteractionResultHolder.pass(user.getItemInHand(interactionHand));
        user.getCooldowns().addCooldown(ModItems.HANDCUFFS, 20);

        return super.use(level, user, interactionHand);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player user, LivingEntity entity,
            InteractionHand hand) {
        if (hasHandCuff(user))
            return InteractionResult.PASS;
        if (user.getCooldowns().isOnCooldown(ModItems.HANDCUFFS))
            return InteractionResult.PASS;
        user.getCooldowns().addCooldown(ModItems.HANDCUFFS, 20);
        if (user.level().isClientSide)
            return InteractionResult.SUCCESS;
        if (entity instanceof Player target) {
            if (hasHandCuff(target)) {
                user.displayClientMessage(
                        Component.translatable("item.noellesroles.handcuffs.failed", user.getName())
                                .withStyle(ChatFormatting.RED),
                        true);
                return InteractionResult.FAIL;
            }
            putOnHandCuff(target, stack.copy());
            stack.shrink(1);
            user.displayClientMessage(Component.translatable("item.noellesroles.handcuffs.put", target.getName())
                    .withStyle(ChatFormatting.GOLD), true);
            target.displayClientMessage(
                    Component.translatable("item.noellesroles.handcuffs.recieved", user.getName())
                            .withStyle(ChatFormatting.RED),
                    true);
        } else {
            return InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }
}
