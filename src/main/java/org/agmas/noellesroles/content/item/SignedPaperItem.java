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

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.role.ModRoles;

import java.util.List;

public class SignedPaperItem extends Item {

    private static final double RANGE = 16.0;
    private static final double FOV_COS_THRESHOLD = Math.cos(Math.toRadians(60));

    public SignedPaperItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack itemStack, Level level, Entity entity, int i, boolean bl) {
        if (level.isClientSide())
            return;
        if (!(entity instanceof Player player))
            return;

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();

        List<Player> nearby = level.getEntitiesOfClass(
                Player.class,
                new AABB(
                        eyePos.x - RANGE, eyePos.y - RANGE, eyePos.z - RANGE,
                        eyePos.x + RANGE, eyePos.y + RANGE, eyePos.z + RANGE),
                e -> e != player);

        for (Player target : nearby) {
            if (target.isSpectator())
                continue;
            Vec3 toTarget = target.getEyePosition().subtract(eyePos);
            if (toTarget.lengthSqr() < 1e-6)
                continue;
            double dot = lookVec.dot(toTarget.normalize());
            if (dot > FOV_COS_THRESHOLD && isStarPlayer(target) && level.getGameTime() % 20 == 0) {
                player.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SPEED,
                        30,
                        0,
                        false,
                        false));
                break;
            }
        }
    }

    public static boolean isStarPlayer(Player player) {
        return SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.SUPERSTAR);
    }
}