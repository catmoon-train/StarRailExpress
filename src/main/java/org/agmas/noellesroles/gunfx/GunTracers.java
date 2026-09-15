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

package org.agmas.noellesroles.gunfx;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.jetbrains.annotations.Nullable;

/**
 * 枪械射击轨迹广播（所有枪通用）：把弹道终点发给射手与周围观察者，
 * 客户端 {@link GunTracerRenderer} 渲染渐隐轨迹线。
 * 由服务端配置 {@link NoellesRolesConfig#gunTracerEffect} 控制（默认开启）。
 */
public final class GunTracers {

    private GunTracers() {
    }

    /** @param hit 命中的实体（null=未命中，按视线方向延伸 range）。 */
    public static void broadcast(ServerPlayer shooter, @Nullable Entity hit, double range) {
        broadcast(shooter, hit, range, false);
    }

    /**
     * @param hit                命中的实体（null=未命中，按视线方向延伸 range）
     * @param ignoreGlobalSwitch true 时不看服务端总开关
     *                           （{@link NoellesRolesConfig#gunTracerEffect}），
     *                           供自定义列车物品按物品自身配置独立控制弹道
     */
    public static void broadcast(ServerPlayer shooter, @Nullable Entity hit, double range,
            boolean ignoreGlobalSwitch) {
        broadcast(shooter, hit, range, ignoreGlobalSwitch, GunTracerS2CPacket.STYLE_DEFAULT);
    }

    /**
     * 狙击枪样式：轨迹线之外，客户端还会沿弹道生成一层烟雾（与狙击枪开火的表现一致）。
     *
     * @param hit 命中的实体（null=未命中，按视线方向延伸 range）
     */
    public static void broadcastSniper(ServerPlayer shooter, @Nullable Entity hit, double range) {
        broadcast(shooter, hit, range, true, GunTracerS2CPacket.STYLE_SNIPER);
    }

    private static void broadcast(ServerPlayer shooter, @Nullable Entity hit, double range,
            boolean ignoreGlobalSwitch, int style) {
        // 服务端侧开关：关闭时不广播任何轨迹线
        if (!ignoreGlobalSwitch && !NoellesRolesConfig.instance().gunTracerEffect) {
            return;
        }
        Vec3 eye = shooter.getEyePosition();
        Vec3 view = shooter.getViewVector(1.0F).normalize();
        Vec3 from = WeaponTrailGeometry.muzzlePoint(shooter, eye, view);

        Vec3 to = hit != null ? hit.getBoundingBox().getCenter() : eye.add(view.scale(range));
        if (hit == null) {
            BlockHitResult blockHit = shooter.level().clip(new ClipContext(eye, to,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
            if (blockHit.getType() == HitResult.Type.BLOCK) {
                to = blockHit.getLocation();
            }
        }
        GunTracerS2CPacket packet = new GunTracerS2CPacket(shooter.getId(),
                from.x, from.y, from.z, to.x, to.y, to.z, style);
        for (ServerPlayer tracking : PlayerLookup.tracking(shooter)) {
            ServerPlayNetworking.send(tracking, packet);
        }
        ServerPlayNetworking.send(shooter, packet);
    }
}
