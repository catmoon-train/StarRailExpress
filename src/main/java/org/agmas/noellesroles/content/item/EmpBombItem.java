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
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.content.entity.EmpBombEntity;
import org.agmas.noellesroles.game.roles.neutral.silver_wing.SilverWingEffects;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;

/** 银翼电磁脉冲炸弹：向前掷出，命中玩家后使其无法使用物品并缓慢。 */
public class EmpBombItem extends Item {
    public EmpBombItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user, InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);
        if (user.isSpectator() || !GameUtils.isPlayerAliveAndSurvival(user)
                || !RoleUtils.isPlayerTheJob(user, ModRoles.SILVER_WING)) {
            return InteractionResultHolder.fail(itemStack);
        }

        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                TMMSounds.ITEM_GRENADE_THROW, SoundSource.NEUTRAL,
                0.5F, 1F + (world.random.nextFloat() - .5f) / 10f);

        if (!world.isClientSide) {
            EmpBombEntity empBomb = new EmpBombEntity(ModEntities.EMP_BOMB, world);
            empBomb.setOwner(user);
            empBomb.setPosRaw(user.getX(), user.getEyeY() - 0.1, user.getZ());
            empBomb.shootFromRotation(user, user.getXRot(), user.getYRot(), 0.0F, 0.85F, 1.0F);
            world.addFreshEntity(empBomb);
            if (user instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                SilverWingEffects.applyEmpUseCooldownIfReady(serverPlayer);
            }
        }

        user.awardStat(Stats.ITEM_USED.get(this));
        itemStack.consume(1, user);
        return InteractionResultHolder.sidedSuccess(itemStack, world.isClientSide());
    }
}
