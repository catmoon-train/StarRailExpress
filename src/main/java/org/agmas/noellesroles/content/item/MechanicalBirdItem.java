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

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.entity.MechanicalBirdEntity;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.neutral.SilverWingRoleData;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;

/** 银翼机械小鸟：使用后由玩家控制飞行，碰到玩家或敌人后爆炸。 */
public class MechanicalBirdItem extends Item {
    public MechanicalBirdItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSpectator() || !GameUtils.isPlayerAliveAndSurvival(player)
                || !RoleUtils.isPlayerTheJob(player, ModRoles.SILVER_WING)) {
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (!(player instanceof ServerPlayer owner)) {
            return InteractionResultHolder.fail(stack);
        }
        SilverWingRoleData data = RoleData.getNullable(SilverWingRoleData.class, owner);
        if (data == null) {
            return InteractionResultHolder.fail(stack);
        }
        if (data.hasActiveBird()) {
            owner.displayClientMessage(Component.translatable("message.noellesroles.silver_wing.bird_active"), true);
            return InteractionResultHolder.fail(stack);
        }

        MechanicalBirdEntity bird = new MechanicalBirdEntity(ModEntities.MECHANICAL_BIRD, level);
        bird.setOwner(owner);
        Vec3 launchDirection = owner.getLookAngle().normalize();
        bird.setPos(owner.getX() + launchDirection.x * 0.8D,
                owner.getEyeY() - 0.1D + launchDirection.y * 0.8D,
                owner.getZ() + launchDirection.z * 0.8D);
        bird.setYRot(owner.getYRot());
        bird.setXRot(owner.getXRot());
        level.addFreshEntity(bird);
        data.setActiveBird(bird);
        stack.consume(1, owner);
        return InteractionResultHolder.consume(stack);
    }
}
