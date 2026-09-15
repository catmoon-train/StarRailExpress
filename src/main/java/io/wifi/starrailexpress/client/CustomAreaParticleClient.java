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

package io.wifi.starrailexpress.client;

import io.wifi.starrailexpress.customitem.CustomItemRuntime;
import io.wifi.starrailexpress.network.CustomAreaParticlePayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 「自定义列车物品·投掷物」区域的客户端粒子渲染。
 *
 * <p>
 * 完全照烟雾弹的做法（{@code ClientSmokeAreaManager}）：服务端只发一次
 * {@link CustomAreaParticlePayload}，客户端自己按 tick 生成粒子，并且
 * <b>只在自己附近才生成</b>（{@link #DISPLAY_LIMIT} 格），远处区域直接跳过，
 * 所以远处的粒子开销为 0。
 */
@Environment(EnvType.CLIENT)
public final class CustomAreaParticleClient {

    /** 超过这个距离的区域不生成粒子（与烟雾弹一致）。 */
    private static final double DISPLAY_LIMIT = 24.0D;
    /** 每多少 tick 生成一批粒子（与烟雾弹一致为 3，这里按区域半径缩放批次数量）。 */
    private static final int SPAWN_INTERVAL = 3;

    private static final List<Area> AREAS = new ArrayList<>();

    private CustomAreaParticleClient() {
    }

    /** 客户端注册：收包 + 每 tick 推进 + 断线清理。 */
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CustomAreaParticlePayload.ID, (payload, context) -> context
                .client().execute(() -> createArea(context.client().level, payload)));
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }

    private static void createArea(ClientLevel level, CustomAreaParticlePayload payload) {
        if (level == null || payload == null) {
            return;
        }
        AREAS.add(new Area(level, payload.center(), Math.max(1.0D, payload.radius()), payload.durationTicks(),
                payload.particleId(), payload.planar()));
    }

    private static void tick() {
        if (AREAS.isEmpty()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        Vec3 eye = client.player == null ? null : client.player.position();
        for (Iterator<Area> iterator = AREAS.iterator(); iterator.hasNext();) {
            Area area = iterator.next();
            if (area.tick(eye)) {
                iterator.remove();
            }
        }
    }

    /** 清理全部区域（断开连接时）。 */
    public static void clear() {
        AREAS.clear();
    }

    /** 一个客户端区域。 */
    private static final class Area {
        private final ClientLevel level;
        private final Vec3 center;
        private final double radius;
        private final ParticleOptions particle;
        private final boolean planar;
        private int remainingTicks;
        private int tickCounter;

        Area(ClientLevel level, Vec3 center, double radius, int durationTicks, String particleId, boolean planar) {
            this.level = level;
            this.center = center;
            this.radius = radius;
            this.planar = planar;
            this.remainingTicks = Math.max(1, durationTicks);
            this.particle = CustomItemRuntime.resolveParticle(particleId,
                    planar ? ParticleTypes.FLAME : ParticleTypes.CAMPFIRE_COSY_SMOKE);
        }

        /**
         * @return 是否已过期
         */
        boolean tick(Vec3 playerPos) {
            if (--remainingTicks <= 0) {
                return true;
            }
            tickCounter++;
            if (tickCounter % SPAWN_INTERVAL != 0) {
                return false;
            }
            if (playerPos != null && center.distanceToSqr(playerPos) >= DISPLAY_LIMIT * DISPLAY_LIMIT) {
                // 离得太远：不生成任何粒子
                return false;
            }
            spawn();
            return false;
        }

        private void spawn() {
            int count = Math.max(6, (int) (radius * 6));
            for (int i = 0; i < count; i++) {
                double x;
                double y;
                double z;
                if (planar) {
                    // 平面区域（燃烧弹式）：贴地铺开，只有很小的高度浮动
                    double angle = level.random.nextDouble() * Math.PI * 2.0D;
                    double distance = Math.sqrt(level.random.nextDouble()) * radius;
                    x = center.x + Math.cos(angle) * distance;
                    z = center.z + Math.sin(angle) * distance;
                    y = center.y + 0.1D + level.random.nextDouble() * 0.4D;
                } else {
                    // 球形区域（烟雾弹式）
                    x = center.x + (level.random.nextDouble() - 0.5D) * radius * 2.0D;
                    z = center.z + (level.random.nextDouble() - 0.5D) * radius * 2.0D;
                    y = center.y - 0.5D + level.random.nextDouble() * 2.5D;
                }
                level.addAlwaysVisibleParticle(particle, true, x, y, z,
                        (level.random.nextDouble() - 0.5D) * 0.05D,
                        level.random.nextDouble() * 0.05D,
                        (level.random.nextDouble() - 0.5D) * 0.05D);
            }
        }
    }
}
