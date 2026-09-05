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

package org.agmas.noellesroles.content.item.angler;

import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.content.entity.AnglerRodMountEntity;
import org.agmas.noellesroles.game.roles.innocence.angler.AnglerCatchHandler;
import org.agmas.noellesroles.game.roles.innocence.angler.AnglerRules;
import org.agmas.noellesroles.game.roles.innocence.angler.AnglerWorldMemory;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.bouns.BounsRoles;
import org.jetbrains.annotations.NotNull;

public class AnglerRodItem extends FishingRodItem {
    public AnglerRodItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player,
            @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && !(this instanceof ErrorAnglerRodItem)) {
            return tryRide(level, player, stack);
        }
        if (!canPlayerCast(player, stack)) {
            return InteractionResultHolder.fail(stack);
        }
        return super.use(level, player, hand);
    }

    private InteractionResultHolder<ItemStack> tryRide(Level level, Player player, ItemStack stack) {
        if (!GameUtils.isGameRunning(player) || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return InteractionResultHolder.fail(stack);
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        if (game == null || !game.isSkillAvailable || !game.isRole(player, BounsRoles.ANGLER)) {
            return InteractionResultHolder.fail(stack);
        }
        if (SREGameTimeComponent.KEY.get(player.level()).isTimeFrozen()) {
            return InteractionResultHolder.fail(stack);
        }
        if (player.isPassenger() || player.isVehicle()) {
            return InteractionResultHolder.fail(stack);
        }
        if (AnglerCatchHandler.remainingUses(stack) <= 0) {
            return InteractionResultHolder.fail(stack);
        }
        if (!AnglerWorldMemory.canRide(player) || player.getCooldowns().isOnCooldown(ModItems.ANGLER_ROD)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.noellesroles.angler.ride_cd")
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return InteractionResultHolder.fail(stack);
        }
        if (player.fishing != null) {
            player.fishing.discard();
        }
        AnglerRodMountEntity mount = ModEntities.ANGLER_ROD_MOUNT.create(serverLevel);
        if (mount == null) {
            return InteractionResultHolder.fail(stack);
        }
        if (AnglerCatchHandler.consumeRod(serverPlayer, stack)) {
            GameUtils.killPlayer(serverPlayer, true, null, AnglerRules.DEATH_EXHAUSTED);
            return InteractionResultHolder.fail(stack);
        }
        mount.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        mount.bindRider(player);
        serverLevel.addFreshEntity(mount);
        player.startRiding(mount, true);
        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.8f, 0.6f);
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    public static boolean canPlayerCast(Player player, ItemStack stack) {
        if (!GameUtils.isGameRunning(player) || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }
        if (player.isPassenger()) {
            return false;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        if (game == null || !game.isSkillAvailable) {
            return false;
        }
        if (SREGameTimeComponent.KEY.get(player.level()).isTimeFrozen()) {
            return false;
        }
        if (stack.getItem() instanceof ErrorAnglerRodItem) {
            return true;
        }
        return game.isRole(player, BounsRoles.ANGLER) && AnglerCatchHandler.remainingUses(stack) > 0;
    }
}
