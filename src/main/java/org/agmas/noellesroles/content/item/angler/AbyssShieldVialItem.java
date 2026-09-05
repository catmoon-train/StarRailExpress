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

import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class AbyssShieldVialItem extends Item {
    public AbyssShieldVialItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player,
            @NotNull InteractionHand hand) {
        if (!GameUtils.isGameRunning(player) || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack, @NotNull LivingEntity entity) {
        return 32;
    }

    @Override
    public @NotNull UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public @NotNull ItemStack finishUsingItem(@NotNull ItemStack stack, @NotNull Level level,
            @NotNull LivingEntity entity) {
        if (!level.isClientSide && entity instanceof Player player && GameUtils.isPlayerAliveAndSurvival(player)) {
            SREArmorPlayerComponent.KEY.get(player).addArmor();
            level.playSound(null, player.blockPosition(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 1f, 1.1f);
            stack.shrink(1);
        }
        return stack;
    }
}
