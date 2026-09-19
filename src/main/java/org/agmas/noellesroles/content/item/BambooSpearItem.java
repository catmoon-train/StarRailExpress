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

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.TrainWeapon;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.content.entity.BambooSpearEntity;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModEntities;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 竹枪 —— 右键后竹子模型沿视线向前伸长，最长 10 格 / 最多 3 秒；碰到玩家即击杀并收回。
 * 3 点耐久，每次成功释放消耗 1 点；冷却 15 秒。
 */
public class BambooSpearItem extends Item implements TrainWeapon {

    public static final int COOLDOWN_TICKS = 20 * 15;

    public BambooSpearItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        SRERole role = getRole(world, user);
        if (role != null && !role.onUseGun(user)) {
            return InteractionResultHolder.fail(stack);
        }
        if (user.getCooldowns().isOnCooldown(this) || user.isSpectator() || user.hasEffect(ModEffects.SAFE_TIME)) {
            return InteractionResultHolder.fail(stack);
        }
        if (stack.getMaxDamage() > 0 && stack.getDamageValue() >= stack.getMaxDamage()) {
            if (!world.isClientSide) {
                user.displayClientMessage(Component.translatable("item.noellesroles.bamboo_spear.no_durability")
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (BambooSpearEntity.hasActiveFor(user)) {
            return InteractionResultHolder.fail(stack);
        }
        if (!world.isClientSide && user instanceof ServerPlayer serverPlayer) {
            BambooSpearEntity spear = new BambooSpearEntity(ModEntities.BAMBOO_SPEAR, world);
            spear.setup(serverPlayer);
            world.addFreshEntity(spear);
            world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.TRIDENT_RIPTIDE_1,
                    SoundSource.PLAYERS, 0.8f, 1.35f);
            user.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
            EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
            stack.hurtAndBreak(1, serverPlayer, slot);
        }
        user.swing(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
            @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
        if (stack.getMaxDamage() > 0) {
            tooltip.add(Component.translatable(getDescriptionId() + ".tooltip.durability",
                    String.format("%d", stack.getMaxDamage() - stack.getDamageValue()),
                    String.format("%d", stack.getMaxDamage())).withStyle(ChatFormatting.DARK_GREEN));
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }

    private static SRERole getRole(Level world, Player user) {
        if (world.isClientSide) {
            var gameComponent = SREClient.gameComponent;
            return gameComponent == null ? null : gameComponent.getRole(user);
        }
        var gameComponent = SREGameWorldComponent.KEY.get(world);
        return gameComponent == null ? null : gameComponent.getRole(user);
    }
}
