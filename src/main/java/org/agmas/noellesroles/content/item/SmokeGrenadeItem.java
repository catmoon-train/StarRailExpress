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
import org.agmas.noellesroles.content.entity.SmokeGrenadeEntity;
import org.agmas.noellesroles.init.ModEntities;
import org.jetbrains.annotations.NotNull;

/**
 * 烟雾弹物品
 * - 捣蛋鬼专属道具
 * - 右键丢掷，形成烟雾区域
 * - 进入烟雾的玩家获得失明效果
 * - 如果直接砸中玩家，清空目标的san值（精神值）
 * - 烟雾持续10秒
 */
public class SmokeGrenadeItem extends Item {

    public SmokeGrenadeItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user, InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);
        if (user.getCooldowns().isOnCooldown(itemStack.getItem()))
            return InteractionResultHolder.pass(itemStack);
        if (!user.isCreative())
            user.getCooldowns().addCooldown(itemStack.getItem(), 30 * 20);

        // 播放投掷音效
        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                TMMSounds.ITEM_GRENADE_THROW, SoundSource.NEUTRAL,
                0.5F, 1F + (world.random.nextFloat() - .5f) / 10f);

        if (!world.isClientSide) {
            // 创建烟雾弹实体
            SmokeGrenadeEntity smokeGrenade = new SmokeGrenadeEntity(ModEntities.SMOKE_GRENADE, world);
            smokeGrenade.setOwner(user);
            smokeGrenade.setPosRaw(user.getX(), user.getEyeY() - 0.1, user.getZ());
            smokeGrenade.shootFromRotation(user, user.getXRot(), user.getYRot(), 0.0F, 0.5F, 1.0F);
            world.addFreshEntity(smokeGrenade);
        }

        user.awardStat(Stats.ITEM_USED.get(this));
        itemStack.consume(1, user);

        return InteractionResultHolder.sidedSuccess(itemStack, world.isClientSide());
    }
}