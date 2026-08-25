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

import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.content.item.CocktailItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class IceRedTeaItem extends CocktailItem {

    public IceRedTeaItem(Properties settings) {
        super(settings);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity user) {
        stack = super.finishUsingItem(stack, world, user);
        if (user instanceof ServerPlayer) {
            user.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SPEED,
                    10 * 20,
                    5,
                    false, // ambient - 环境效果（粒子更少更透明）
                    true, // showParticles - 不显示粒子
                    true // showIcon - 不显示图标
            ));
            user.addEffect(new MobEffectInstance(
                    MobEffects.SLOW_FALLING,
                    10 * 20,
                    5,
                    false, // ambient - 环境效果（粒子更少更透明）
                    true, // showParticles - 不显示粒子
                    true // showIcon - 不显示图标
            ));
            user.setRemainingFireTicks(0);
            SREPlayerMoodComponent.KEY.get(user).addMood(0.1f);
        }
        return stack;
    }
}
