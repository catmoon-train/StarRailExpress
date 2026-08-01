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

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 磁铁
 * - 携带在物品栏中时，每tick吸取周围掉落物向自己移动
 * - 吸取半径：8格
 * - 距离过近时直接触发拾取
 */
public class MagnetItem extends Item {

    /** 吸取半径（格） */
    private static final double PULL_RADIUS = 8.0D;
    /** 吸取速度系数 */
    private static final double PULL_SPEED = 0.6D;
    /** 小于该距离时直接将掉落物拉到玩家脚下以触发拾取 */
    private static final double SNAP_DISTANCE = 1.0D;

    public MagnetItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack itemStack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide())
            return;
        if (!(entity instanceof Player player))
            return;
        if (entity.isSpectator())
            return;

        Vec3 center = player.position().add(0, player.getBbHeight() / 2.0D, 0);
        AABB area = player.getBoundingBox().inflate(PULL_RADIUS);

        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, area,
                item -> item.isAlive() && !item.hasPickUpDelay());

        for (ItemEntity item : items) {
            Vec3 toPlayer = center.subtract(item.position());
            double distance = toPlayer.length();
            if (distance < 1.0E-4D)
                continue;

            if (distance <= SNAP_DISTANCE) {
                // 足够近，直接拉到玩家身上以触发原版拾取逻辑
                item.setPos(player.getX(), player.getY(), player.getZ());
                continue;
            }

            Vec3 velocity = toPlayer.scale(PULL_SPEED / distance);
            item.setDeltaMovement(velocity);
            item.hasImpulse = true;
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.magnet.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }
}
