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
import io.wifi.starrailexpress.util.AdventureUsable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.FunnyItems;
import org.agmas.noellesroles.role_data.vigilante.JojoRoleData;

/**
 * 欧拉一拳：右键进入攻击期间，绑定第一个打到的目标，6 秒内右键 20 次即可击杀。
 * 开门等其他特性仍由方块侧检测本物品。
 */
public class BowenBadgeItem extends Item implements AdventureUsable {

    public BowenBadgeItem(Properties properties) {
        super(properties);
    }

    public static boolean isHolding(Player player) {
        return JojoRoleData.isHoldingOraPunch(player);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack itemStack = player.getItemInHand(usedHand);
        if (player.getCooldowns().isOnCooldown(this) || player.getCooldowns().isOnCooldown(FunnyItems.BOWEN_BADGE)) {
            return InteractionResultHolder.fail(itemStack);
        }
        if (player.isSpectator()) {
            return InteractionResultHolder.fail(itemStack);
        }
        if (!level.isClientSide) {
            JojoRoleData data = RoleData.getNullable(JojoRoleData.class, player);
            if (data != null) {
                data.onOraUse(player);
            }
        }
        return InteractionResultHolder.consume(itemStack);
    }

    public static void fowardAndKnockbackPlayerNearby(Level level, Player player, float force) {
        // ── 击退周围的玩家（排除正前方碰撞目标）──
        float g = player.getYRot();

        // 水平朝向向量
        float k = -Mth.sin(g * ((float) Math.PI / 180F));
        float m = Mth.cos(g * ((float) Math.PI / 180F));
        float horizLen = Mth.sqrt(k * k + m * m);
        float kNorm = k / horizLen;
        float mNorm = m / horizLen;
        Vec3 playerPos = player.position();
        AABB nearbyBox = new AABB(playerPos, playerPos).inflate(3.0, 1.5, 3.0);

        Vec3 dashFront = playerPos.add(kNorm * 2.5, 0, mNorm * 2.5);
        AABB collisionBox = new AABB(dashFront, dashFront).inflate(0.8, 1.0, 0.8);

        for (var entity : level.getEntities(player, nearbyBox)) {
            if (!(entity instanceof LivingEntity target))
                continue;
            if (collisionBox.intersects(target.getBoundingBox()))
                continue;

            Vec3 knockback = target.position()
                    .subtract(playerPos)
                    .multiply(1, 0, 1)
                    .normalize()
                    .scale(0.5);
            target.push(knockback.x, 0, knockback.z);
            target.hurtMarked = true;
            // 被击退实体产生冲击波粒子
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
                        target.getX(), target.getY() + 1, target.getZ(),
                        1, 0, 0, 0, 0);
            }
        }
        player.push(kNorm * force, 0.0, mNorm * force);
        player.hurtMarked = true;
    }
}
