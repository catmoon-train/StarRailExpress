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

package io.wifi.starrailexpress.customitem;

import io.wifi.starrailexpress.network.CustomAreaParticlePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 「自定义列车物品·投掷物」的两类区域（服务端侧）。
 *
 * <ul>
 * <li><b>粒子区域</b>（同烟雾弹）：只发一次 {@link CustomAreaParticlePayload}，
 * 粒子完全由客户端渲染（客户端还会按距离裁剪），服务端不留任何状态。</li>
 * <li><b>持续生效区域</b>（同燃烧弹）：同样只发一次包让客户端画粒子；服务端只负责
 * <b>玩法结算</b>——<b>平面</b>圆形范围
 * （与 {@code ServerGrenadeAreaManager} 一致用 {@code dx*dx + dz*dz <= r*r} 判定）、
 * 区域内停留够 tick 的玩家触发药水效果与指令。</li>
 * </ul>
 *
 * <p>
 * 与 {@code ServerSmokeAreaManager} 的区别仅在于：粒子 id、半径、持续时间、停留 tick、
 * 药水效果、指令全部来自物品配置。
 */
public final class CustomThrowableAreas {

    /** 只有持续生效区域需要服务端结算，粒子区域纯客户端。 */
    private static final List<PersistentArea> PERSISTENT_AREAS = new ArrayList<>();

    private CustomThrowableAreas() {
    }

    /** 持续生效区域（燃烧弹式，平面）。 */
    private static final class PersistentArea {
        final ServerLevel level;
        final Vec3 center;
        final double radius;
        final int stayTicks;
        final List<CustomItemData.EffectData> effects;
        final List<String> commands;
        /** 投掷者：区域指令里的 {@code <attacker>} 指他（可能为 null）。 */
        final ServerPlayer attacker;
        /** 每个玩家在区域内已停留的 tick。 */
        final Map<UUID, Integer> stayTicksByPlayer = new HashMap<>();
        int remainingTicks;

        PersistentArea(ServerLevel level, Vec3 center, double radius, int stayTicks,
                List<CustomItemData.EffectData> effects, List<String> commands, int durationTicks,
                ServerPlayer attacker) {
            this.level = level;
            this.center = center;
            this.radius = radius;
            this.stayTicks = stayTicks;
            this.effects = effects;
            this.commands = commands;
            this.remainingTicks = durationTicks;
            this.attacker = attacker;
        }
    }

    // ==================== 创建 ====================

    /** 创建粒子区域（粒子 id 留空时客户端用烟雾弹的默认粒子）。 */
    public static void createParticleArea(ServerLevel level, Vec3 center, double radius, String particleId,
            int durationTicks) {
        broadcast(level, center, Math.max(1.0D, radius), Math.max(1, durationTicks), particleId, false);
    }

    /** 创建持续生效区域（半径 / 滞留时间 / 停留 tick / 药水效果 / 指令全部来自配置）。 */
    public static void createPersistentArea(ServerLevel level, Vec3 center, double radius, CustomItemData data,
            ServerPlayer attacker) {
        int durationTicks = Math.max(1, data.throwAreaDurationSeconds) * 20;
        double areaRadius = Math.max(1.0D, radius);
        broadcast(level, center, areaRadius, durationTicks, data.throwAreaParticleId, true);
        PERSISTENT_AREAS.add(new PersistentArea(level, center, areaRadius,
                Math.max(1, data.throwAreaStayTicks),
                data.throwAreaEffects == null ? List.of() : data.throwAreaEffects,
                data.throwAreaCommands == null ? List.of() : data.throwAreaCommands,
                durationTicks, attacker));
    }

    /** 把区域交给客户端渲染（服务端不再自己广播粒子）。 */
    private static void broadcast(ServerLevel level, Vec3 center, double radius, int durationTicks,
            String particleId, boolean planar) {
        CustomAreaParticlePayload payload = new CustomAreaParticlePayload(center, radius, durationTicks,
                particleId == null ? "" : particleId, planar);
        for (ServerPlayer player : level.players()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    // ==================== 每 tick（只结算玩法） ====================

    /** 服务端每 tick 结算（由 {@code CustomItemRuntime} 注册）。 */
    public static void tick(MinecraftServer server) {
        if (PERSISTENT_AREAS.isEmpty()) {
            return;
        }
        for (Iterator<PersistentArea> iterator = PERSISTENT_AREAS.iterator(); iterator.hasNext();) {
            PersistentArea area = iterator.next();
            if (--area.remainingTicks <= 0 || area.level.getServer() == null) {
                iterator.remove();
                continue;
            }
            for (ServerPlayer player : area.level.players()) {
                UUID uuid = player.getUUID();
                if (!insidePlanar(area, player.position())) {
                    area.stayTicksByPlayer.remove(uuid);
                    continue;
                }
                int stay = area.stayTicksByPlayer.merge(uuid, 1, Integer::sum);
                if (stay < area.stayTicks) {
                    continue;
                }
                // 触发一次后重新累计，可以反复触发
                area.stayTicksByPlayer.put(uuid, 0);
                CustomItemRuntime.applyEffects(player, area.effects);
                CustomItemLoader.executeCommands(area.commands, player, area.attacker);
            }
        }
    }

    /** 平面（水平圆形）范围判定，与燃烧弹一致。 */
    private static boolean insidePlanar(PersistentArea area, Vec3 position) {
        double dx = position.x - area.center.x;
        double dz = position.z - area.center.z;
        return dx * dx + dz * dz <= area.radius * area.radius;
    }

    /** 清理全部区域（换局时调用）。 */
    public static void clear() {
        PERSISTENT_AREAS.clear();
    }
}
