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

package io.wifi.starrailexpress.client.particle;

import io.wifi.starrailexpress.network.packet.CustomParticleS2CPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 客户端"自定义形状粒子"处理器注册表：服务端发来 {@link CustomParticleS2CPayload} 后，这里按
 * {@code id} 找到对应处理器生成形状。
 *
 * <p>为什么要有它：原版 {@code ServerLevel.sendParticles} 只能表达"在一个点上按高斯散布"，环形、
 * 螺旋、沿轨迹、跟随实体等形状没法用原版包表达；而在服务端循环里逐点发包会把包数放大成百上千倍。
 * 约定是：**服务端只发一个包（带 id + 位置 + 参数），形状由客户端在本地逐点 addParticle 生成**——
 * 客户端粒子不进网络，形状可以任意复杂。
 *
 * <p>注册位置：客户端初始化处（例如 {@code SREClient} 的 client 初始化流程，或该特效所属功能的
 * 客户端初始化方法）。同一个 id 不能注册两次，重复注册会立刻抛错，便于早发现冲突。
 *
 * <pre>{@code
 * CustomParticleHandlers.register(ResourceLocation.fromNamespaceAndPath("mymod", "shock_wave"),
 *         (level, origin, durationTicks, params) -> {
 *             double radius = params.length > 0 ? params[0] : 1.0D;
 *             for (int i = 0; i < 64; i++) {
 *                 double angle = Math.PI * 2.0 * i / 64;
 *                 level.addParticle(ParticleTypes.END_ROD,
 *                         origin.x + Math.cos(angle) * radius,
 *                         origin.y, origin.z + Math.sin(angle) * radius,
 *                         0, 0, 0);
 *             }
 *         });
 * }</pre>
 */
public final class CustomParticleHandlers {
    private CustomParticleHandlers() {
    }

    @FunctionalInterface
    public interface Handler {
        /**
         * 在客户端生成形状。在客户端主线程调用，未做距离判断（服务端已按半径筛选过接收者）。
         *
         * @param level         当前客户端世界
         * @param origin        服务端指定的原点
         * @param durationTicks 服务端给的期望时长（tick），0 表示一次性/由处理器自定
         * @param params        自定义参数，含义由本处理器定义，长度不超过
         *                      {@link CustomParticleS2CPayload#MAX_PARAMS}
         */
        void play(ClientLevel level, Vec3 origin, int durationTicks, float[] params);
    }

    private static final Map<ResourceLocation, Handler> HANDLERS = new HashMap<>();

    /** 注册一个 id 对应的客户端形状处理器。重复注册会抛错。 */
    public static void register(ResourceLocation id, Handler handler) {
        if (HANDLERS.putIfAbsent(id, handler) != null) {
            throw new IllegalStateException("CustomParticleHandlers: id 重复注册 " + id);
        }
    }

    public static boolean isRegistered(ResourceLocation id) {
        return HANDLERS.containsKey(id);
    }

    /** 已注册的 id 快照，可用于调试/校验。 */
    public static Set<ResourceLocation> registeredIds() {
        return Set.copyOf(HANDLERS.keySet());
    }

    /**
     * 由 S2C 接收器调用：逐条按 id 派发。未知 id 静默忽略，这样服务端可以先上新特效、
     * 客户端后续版本再补处理器。
     */
    public static void dispatch(CustomParticleS2CPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || payload.isEmpty()) {
            return;
        }
        for (CustomParticleS2CPayload.Entry entry : payload.entries()) {
            Handler handler = HANDLERS.get(entry.id());
            if (handler == null) {
                continue;
            }
            handler.play(level, new Vec3(entry.x(), entry.y(), entry.z()),
                    entry.durationTicks(), toArray(entry.params()));
        }
    }

    private static float[] toArray(List<Float> params) {
        float[] out = new float[params.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = params.get(i);
        }
        return out;
    }
}
