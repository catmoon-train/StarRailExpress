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

import io.wifi.starrailexpress.api.ChargeableItem;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.TrainWeapon;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.content.entity.ThrownBambooEntity;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 竹子 —— 蓄力投掷。松手后生成 3D 竹子投掷物：途中最多挂上 2 名玩家，撞墙后钉在墙上；
 * 从发射起 12 秒后消失（含钉在墙上的时间）。
 */
public class ThrownBambooItem extends Item implements ChargeableItem, TrainWeapon {

    public static final int CHARGE_TICKS = 16;
    public static final int COOLDOWN_TICKS = 20 * 3;
    private static final int MAX_USE_DURATION = 72000;

    public ThrownBambooItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public int getMaxChargeTime(ItemStack stack, Player player) {
        return CHARGE_TICKS;
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
        user.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(@NotNull ItemStack stack, @NotNull Level world, @NotNull LivingEntity entity,
            int timeLeft) {
        if (!(entity instanceof Player user)) {
            return;
        }
        int charged = this.getUseDuration(stack, entity) - timeLeft;
        if (charged < CHARGE_TICKS) {
            return;
        }
        SRERole role = getRole(world, user);
        if (role != null && !role.onUseGun(user)) {
            return;
        }
        if (user.getCooldowns().isOnCooldown(this) || user.isSpectator() || user.hasEffect(ModEffects.SAFE_TIME)) {
            return;
        }
        if (!world.isClientSide && user instanceof ServerPlayer serverPlayer) {
            ThrownBambooEntity bamboo = new ThrownBambooEntity(ModEntities.THROWN_BAMBOO, user, world,
                    ModItems.BAMBOO.getDefaultInstance());
            bamboo.setPos(user.getEyePosition());
            Vec3 look = user.getLookAngle();
            Vec3 horiz = new Vec3(look.x, 0.0, look.z);
            if (horiz.lengthSqr() < 1.0E-6) {
                float yaw = user.getYRot() * net.minecraft.util.Mth.DEG_TO_RAD;
                horiz = new Vec3(-net.minecraft.util.Mth.sin(yaw), 0.0, net.minecraft.util.Mth.cos(yaw));
            }
            horiz = horiz.normalize();
            bamboo.shoot(horiz.x, 0.0, horiz.z, ThrownBambooEntity.THROW_SPEED, 1.0f);
            bamboo.setThrownDirection(horiz);
            bamboo.setOwner(user);
            world.addFreshEntity(bamboo);
            ServerLevel serverLevel = serverPlayer.serverLevel();
            serverLevel.players().forEach(p -> serverLevel.playSound(p, bamboo.getX(), bamboo.getY(), bamboo.getZ(),
                    SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0f, 1.2f));

            user.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
            if (!user.isCreative()) {
                stack.shrink(1);
            }
        }
        user.swing(user.getUsedItemHand());
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack, @NotNull LivingEntity entity) {
        return MAX_USE_DURATION;
    }

    @Override
    public @NotNull UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
            @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
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
