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

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.entity.TomatoProjectileEntity;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role_data.innocence.TomatoHeadRoleData;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class TomatoItem extends Item {

    public static final float THROW_SPEED = 2.35F;
    public static final int THROW_COOLDOWN_TICKS = 20 * 20;
    public static final int DROP_TRIP_SECONDS = 2;
    private static final int LAND_GRACE_TICKS = 8;
    private static final int THROWER_GRACE_TICKS = 20;
    private static final String THROWER_KEY = "TomatoThrower";

    public TomatoItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.tomato.desc").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    public static boolean tryThrow(ServerPlayer player) {
        if (player.isSpectator() || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }
        if (player.hasEffect(ModEffects.SAFE_TIME) || player.hasEffect(ModEffects.USED_BANED)) {
            return false;
        }
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.TOMATO)) {
            return false;
        }
        if (player.getCooldowns().isOnCooldown(ModItems.TOMATO)) {
            return true;
        }
        TomatoProjectileEntity projectile = new TomatoProjectileEntity(ModEntities.TOMATO_PROJECTILE, player,
                player.level());
        projectile.setItem(withThrower(stack.copyWithCount(1), player.getUUID()));
        projectile.setPos(player.getEyePosition());
        projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, THROW_SPEED, 0.4F);
        player.level().addFreshEntity(projectile);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        applyItemCooldown(player);
        player.swing(InteractionHand.MAIN_HAND, true);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.6F, 0.7F);
        return true;
    }

    public static ItemEntity spawnGroundDrop(ServerLevel level, double x, double y, double z, Vec3 motion,
            @Nullable Entity thrower) {
        ItemStack stack = new ItemStack(ModItems.TOMATO);
        if (thrower != null) {
            stack = withThrower(stack, thrower.getUUID());
        }
        ItemEntity drop = new ItemEntity(level, x, y, z, stack);
        if (thrower != null) {
            drop.setThrower(thrower);
        }
        drop.setDeltaMovement(motion);
        drop.setPickUpDelay(LAND_GRACE_TICKS);
        level.addFreshEntity(drop);
        return drop;
    }

    public static boolean tryWalkOverPickup(ServerPlayer walker, ItemEntity item) {
        if (item.isRemoved() || !item.getItem().is(ModItems.TOMATO)) {
            return false;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(walker) || TomatoHeadRoleData.isTomatoForm(walker)) {
            return false;
        }
        if (item.getAge() < LAND_GRACE_TICKS) {
            return false;
        }
        Entity owner = item.getOwner();
        if (owner == walker && item.getAge() < THROWER_GRACE_TICKS) {
            return false;
        }
        UUID throwerId = getThrowerId(item);
        TomatoHeadRoleData.tripPlayer(walker, DROP_TRIP_SECONDS);
        TomatoHeadRoleData.giveTomato(walker);
        item.discard();
        applyItemCooldown(walker);
        if (throwerId != null) {
            ServerPlayer thrower = walker.server.getPlayerList().getPlayer(throwerId);
            if (thrower != null) {
                applyItemCooldown(thrower);
            }
        }
        return true;
    }

    public static void applyItemCooldown(ServerPlayer player) {
        player.getCooldowns().addCooldown(ModItems.TOMATO, THROW_COOLDOWN_TICKS);
    }

    public static ItemStack withThrower(ItemStack stack, UUID thrower) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putUUID(THROWER_KEY, thrower);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    @Nullable
    public static UUID getThrowerId(ItemEntity item) {
        CompoundTag tag = item.getItem().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.hasUUID(THROWER_KEY)) {
            return tag.getUUID(THROWER_KEY);
        }
        Entity owner = item.getOwner();
        return owner != null ? owner.getUUID() : null;
    }

    public static boolean isTomato(Player player) {
        return player.getMainHandItem().is(ModItems.TOMATO);
    }
}
