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

package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.client.SREClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.FastColor;
import org.agmas.noellesroles.packet.EmbalmerPaletteS2CPacket;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 嬉命人·调色盘 客户端状态：保存「目标 UUID -> 渲染颜色」，供渲染层取用。
 *
 * <p>渲染层（活体玩家走 {@code LivingEntityRenderer}，尸体走 {@code PlayerBodyEntityRenderer}）
 * 只需调用 {@link #blend(UUID, int)} 即可拿到混合后的颜色。
 */
public final class ClientEmbalmerPaletteState {

    /** 染色权重：越大越接近纯色。 */
    private static final float STRENGTH = 0.72F;

    private static final Map<UUID, Integer> TINTS = new ConcurrentHashMap<>();
    private static volatile int expiresAtTick = -1;

    private ClientEmbalmerPaletteState() {
    }

    /**
     * 注册位置：{@code client/NoellesrolesClient#onInitializeClient}。
     * 遵循仓库约定：receiver 只做转发，实际改动放到 {@code ctx.client().execute(...)} 里执行。
     */
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(EmbalmerPaletteS2CPacket.ID,
                (payload, ctx) -> ctx.client().execute(() -> {
                    if (payload.tints().isEmpty() || payload.remainingTicks() <= 0) {
                        clear();
                        return;
                    }
                    TINTS.clear();
                    TINTS.putAll(payload.tints());
                    expiresAtTick = (int) (SREClient.getTicksFromGameStart() + payload.remainingTicks());
                }));
    }

    public static void clear() {
        TINTS.clear();
        expiresAtTick = -1;
    }

    private static boolean active() {
        if (expiresAtTick >= 0 && SREClient.getTicksFromGameStart() >= expiresAtTick) {
            clear();
            return false;
        }
        return !TINTS.isEmpty();
    }

    /** 返回该目标的染色值（0xRRGGBB）；无染色返回 -1。 */
    public static int color(UUID uuid) {
        if (uuid == null || !active()) {
            return -1;
        }
        return TINTS.getOrDefault(uuid, -1);
    }

    /**
     * 把原版渲染颜色向调色盘颜色混合。
     *
     * @return 混合后的 ARGB；无染色时原样返回
     */
    public static int blend(UUID uuid, int originalColor) {
        int tint = color(uuid);
        if (tint < 0) {
            return originalColor;
        }
        int alpha = FastColor.ARGB32.alpha(originalColor);
        int baseR = FastColor.ARGB32.red(originalColor);
        int baseG = FastColor.ARGB32.green(originalColor);
        int baseB = FastColor.ARGB32.blue(originalColor);
        int tintR = (tint >> 16) & 0xFF;
        int tintG = (tint >> 8) & 0xFF;
        int tintB = tint & 0xFF;
        int r = Math.round(baseR * (1.0F - STRENGTH) + tintR * STRENGTH);
        int g = Math.round(baseG * (1.0F - STRENGTH) + tintG * STRENGTH);
        int b = Math.round(baseB * (1.0F - STRENGTH) + tintB * STRENGTH);
        return FastColor.ARGB32.color(alpha, r, g, b);
    }
}
