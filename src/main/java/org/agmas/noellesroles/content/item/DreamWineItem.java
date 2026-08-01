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

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.init.ModEffects;

/**
 * Dream 酿的酒（制酒技能产物，90s 冷却酿一瓶）。
 *
 * <p>
 * 喝下后进入隐身状态 {@code dreamWineDurationSeconds}（默认 10s），期间
 * <b>无法攻击</b>（{@code SAFE_TIME}）也<b>无法受伤</b>（{@code INVINCIBLE}）——
 * 与愚者灵性斗篷同一套效果组合。
 */
public class DreamWineItem extends Item {
    private static final int DRINK_TICKS = 32;

    public DreamWineItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        if (!level.isClientSide && user instanceof Player player) {
            int durationTicks = NoellesRolesConfig.HANDLER.instance().dreamWineDurationSeconds * 20;

            player.addEffect(ModEffects.of(ModEffects.USED_BANED, 10 * 20, 1, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, durationTicks, 0,
                    false, false, false));
            player.addEffect(new MobEffectInstance(ModEffects.INVINCIBLE, durationTicks, 254,
                    false, false, false));
            player.addEffect(new MobEffectInstance(ModEffects.SAFE_TIME, durationTicks, 254,
                    false, false, false));
            level.playSound(null, player.blockPosition(), SoundEvents.WITCH_DRINK, SoundSource.PLAYERS,
                    1.0f, 0.8f);
            if (!player.isCreative()) {
                stack.shrink(1);
            }
        }
        return stack;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return DRINK_TICKS;
    }
}
