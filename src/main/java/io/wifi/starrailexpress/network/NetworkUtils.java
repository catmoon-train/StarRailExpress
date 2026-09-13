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

package io.wifi.starrailexpress.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 测量自定义载荷的实际字节数。
 *
 * <p>只处理 mod 的自定义载荷（{@link CustomPacketPayload}）：这类包可以用注册时使用的
 * {@code StreamCodec} 真实编码一次，得到准确大小，而不是像过去那样常量返回 64 / 128 / 0。
 * 原版 Minecraft 包不参与统计，因此这里没有它们的估算表。
 *
 * <p>开销控制：
 * <ul>
 *   <li>编码缓冲区按线程复用，不再每包新建 ByteBuf；写大过的缓冲区不复用，避免常驻内存；</li>
 *   <li>超过 {@link #MAX_MEASURE_BYTES} 的载荷会编码失败并回退，不会无限扩张缓冲区；</li>
 *   <li>编码失败（类型未注册、缺少注册表等）时回退到固定估算值，并单独计数，
 *       使导出的 {@code size_measurement} 能反映有多少字节其实是估算的。</li>
 * </ul>
 */
public final class NetworkUtils {

    /** 无法真实序列化时的回退值（字节）。 */
    public static final long FALLBACK_SIZE = 128L;

    /** 复用缓冲区的初始容量。 */
    private static final int REUSABLE_CAPACITY = 4096;
    /** 超过此大小的载荷放弃测量。 */
    private static final int MAX_MEASURE_BYTES = 4 * 1024 * 1024;
    /** 缓冲区被撑到这个倍数以上时不再复用。 */
    private static final int REUSE_CAPACITY_LIMIT = REUSABLE_CAPACITY * 4;

    private static final ThreadLocal<ByteBuf> ENCODE_BUFFER =
            ThreadLocal.withInitial(() -> Unpooled.buffer(REUSABLE_CAPACITY, MAX_MEASURE_BYTES));

    /** 查找载荷 codec 时依次尝试的注册表。 */
    private static final PayloadTypeRegistryImpl<?>[] CODEC_REGISTRIES = {
            PayloadTypeRegistryImpl.PLAY_S2C,
            PayloadTypeRegistryImpl.PLAY_C2S,
            PayloadTypeRegistryImpl.CONFIGURATION_S2C,
            PayloadTypeRegistryImpl.CONFIGURATION_C2S,
    };

    private NetworkUtils() {
    }

    /**
     * 测量自定义载荷的字节数。成功时返回真实序列化大小并在 {@code stats} 上记一次“已测量”，
     * 失败时返回 {@link #FALLBACK_SIZE} 并记一次“回退估算”。
     *
     * <p>口径说明：得到的是载荷体大小，不含外层 {@code CustomPayloadPacket} 的包 id varint
     * 与载荷类型 ResourceLocation，两者合计是固定的几十字节。
     */
    public static long measurePayloadSize(CustomPacketPayload payload, NetworkStatistics stats) {
        try {
            ByteBuf buffer = ENCODE_BUFFER.get();
            buffer.clear();
            // 含注册表引用的载荷（ItemStack / Holder 等）必须拿到真实注册表才能编码，
            // 否则对应的 codec 会抛异常，走下面的回退分支。
            RegistryFriendlyByteBuf friendly = new RegistryFriendlyByteBuf(buffer, stats.resolveRegistryAccess());
            if (encode(payload, friendly)) {
                long size = buffer.readableBytes();
                stats.countMeasuredSize();
                if (buffer.capacity() > REUSE_CAPACITY_LIMIT) {
                    // 刚写过一个很大的包，别把大缓冲区长期挂在线程上。
                    dropCachedBuffer();
                }
                return size;
            }
        } catch (Throwable ignored) {
            // 复用缓冲区可能已被写坏，丢弃后下次重新申请。
            dropCachedBuffer();
        }
        stats.countFallbackSize();
        return FALLBACK_SIZE;
    }

    /**
     * 用该载荷类型注册时使用的 codec 编码一次。
     *
     * <p>注：codec 存放在 Fabric 的载荷类型注册表里（{@code CustomPacketPayload.Type} 本身只带 id），
     * 所以这里按 play → configuration 的顺序查找。缺失注册表只可能出现在未注册的类型上，
     * 走回退分支即可。
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean encode(CustomPacketPayload payload, RegistryFriendlyByteBuf friendly) {
        StreamCodec codec = codecFor((CustomPacketPayload.Type) payload.type());
        if (codec == null) {
            return false;
        }
        codec.encode(friendly, payload);
        return true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static StreamCodec codecFor(CustomPacketPayload.Type type) {
        for (PayloadTypeRegistryImpl<?> registry : CODEC_REGISTRIES) {
            var registered = registry.get(type);
            if (registered != null) {
                return registered.codec();
            }
        }
        return null;
    }

    private static void dropCachedBuffer() {
        ByteBuf buffer = ENCODE_BUFFER.get();
        ENCODE_BUFFER.remove();
        if (buffer.refCnt() > 0) {
            buffer.release();
        }
    }
}
