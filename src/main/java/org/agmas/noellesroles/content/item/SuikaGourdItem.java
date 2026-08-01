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

import org.agmas.noellesroles.component.FoodDrinkGlowComponent;

import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.content.item.CocktailItem;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

public class SuikaGourdItem extends CocktailItem {
    public static final int COOLDOWN_TICKS = 20 * 20;

    public SuikaGourdItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        if (!(user instanceof Player player)) {
            return super.finishUsingItem(stack, level, user);
        }
        var foodProperties = stack.getOrDefault(DataComponents.FOOD, null);

        level.playSound((Player) null, user.getX(), user.getY(), user.getZ(), user.getEatingSound(stack),
                SoundSource.NEUTRAL, 1.0F, 1.0F + (level.random.nextFloat() - level.random.nextFloat()) * 0.4F);
        if (foodProperties != null) {
            if (!level.isClientSide()) {
                for (FoodProperties.PossibleEffect possibleEffect : foodProperties.effects()) {
                    if (level.random.nextFloat() < possibleEffect.probability()) {
                        user.addEffect(possibleEffect.effect());
                    }
                }
            }
        }
        user.gameEvent(GameEvent.EAT);
        if (user instanceof ServerPlayer serverPlayerEntity) {
            FoodDrinkGlowComponent.playerDrink(serverPlayerEntity, stack);
            CriteriaTriggers.CONSUME_ITEM.trigger(serverPlayerEntity, stack);
            serverPlayerEntity.awardStat(Stats.ITEM_USED.get(this));
            SREPlayerMoodComponent.KEY.get(serverPlayerEntity).drinkCocktail();
        }
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 40;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public SoundEvent getEatingSound() {
        return SoundEvents.GENERIC_DRINK;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        var stack = user.getItemInHand(hand);
        if (user.getCooldowns().isOnCooldown(this))
            return InteractionResultHolder.pass(stack);
        return super.use(world, user, hand);
    }
}
