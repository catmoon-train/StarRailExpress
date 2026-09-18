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

import io.wifi.starrailexpress.content.item.DerringerItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.modifier.refugee.RefugeeDesperadoFx;
import org.agmas.noellesroles.init.ModItems;

/**
 * 难民词条转化亡命徒专属德林加：射击逻辑与德林加一致，弹道绘制红光并让附近玩家缓慢。
 */
public final class DesperadoGunItem extends DerringerItem {
    private static final double BEAM_LENGTH = 20.0D;
    private static final double SLOW_RADIUS = 1.35D;
    private static final int SLOW_TICKS = 60;

    public DesperadoGunItem(Properties settings) {
        super(settings);
    }

    public static boolean isDerringerWeapon(ItemStack stack) {
        return stack.getItem() instanceof DerringerItem;
    }

    public static void onFired(ServerPlayer shooter) {
        if (!shooter.getMainHandItem().is(ModItems.DESPERADO_GUN)) {
            return;
        }
        if (!(shooter.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 eye = shooter.getEyePosition();
        Vec3 look = shooter.getViewVector(1.0F).normalize();
        RefugeeDesperadoFx.sendCustomNearby(level, RefugeeDesperadoFx.BEAM_ID, eye, 10,
                shooter.getYRot(), shooter.getXRot(), (float) BEAM_LENGTH);

        Vec3 end = eye.add(look.scale(BEAM_LENGTH));
        AABB beamBox = new AABB(eye, end).inflate(SLOW_RADIUS);
        for (Player nearby : level.getEntitiesOfClass(Player.class, beamBox,
                player -> RefugeeDesperadoFx.isLivingTarget(shooter, player)
                        && distanceToSegmentSqr(player.getEyePosition(), eye, end) <= SLOW_RADIUS * SLOW_RADIUS)) {
            nearby.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOW_TICKS, 4, false, true, true));
        }
    }

    private static double distanceToSegmentSqr(Vec3 point, Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        double lengthSqr = delta.lengthSqr();
        if (lengthSqr < 1.0E-6D) {
            return point.distanceToSqr(from);
        }
        double t = Math.max(0.0D, Math.min(1.0D, point.subtract(from).dot(delta) / lengthSqr));
        return point.distanceToSqr(from.add(delta.scale(t)));
    }

    @Override
    public String getItemSkinType() {
        return "derringer";
    }
}
