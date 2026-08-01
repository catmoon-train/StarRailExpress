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

import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.content.entity.DecoyGrenadeEntity;
import org.agmas.noellesroles.init.ModEntities;
import org.jetbrains.annotations.NotNull;

/**
 * 诱饵弹物品
 * - 右键投掷
 * - 落地时不会产生粒子效果
 * - 在落地处发生5声左轮手枪射击的声音（时间间隔不一）
 */
public class DecoyGrenadeItem extends Item {

    public DecoyGrenadeItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user, InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);

        // 播放投掷音效
        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                TMMSounds.ITEM_GRENADE_THROW, SoundSource.NEUTRAL,
                0.5F, 1F + (world.random.nextFloat() - .5f) / 10f);

        if (!world.isClientSide) {
            // 创建诱饵弹实体
            DecoyGrenadeEntity decoyGrenade = new DecoyGrenadeEntity(ModEntities.DECOY_GRENADE, world);
            decoyGrenade.setOwner(user);
            decoyGrenade.setPosRaw(user.getX(), user.getEyeY() - 0.1, user.getZ());
            decoyGrenade.shootFromRotation(user, user.getXRot(), user.getYRot(), 0.0F, 0.5F, 1.0F);
            world.addFreshEntity(decoyGrenade);
        }

        user.awardStat(Stats.ITEM_USED.get(this));
        itemStack.consume(1, user);

        return InteractionResultHolder.sidedSuccess(itemStack, world.isClientSide());
    }
}
