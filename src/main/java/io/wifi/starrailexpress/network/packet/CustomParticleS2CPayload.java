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

package io.wifi.starrailexpress.network.packet;

import io.netty.handler.codec.DecoderException;
import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用"自定义形状粒子"包（S2C）：服务端只说"在哪个位置、播放哪一条特效、要持续多久"，
 * 具体形状由客户端按 {@code id} 自己算（见 {@code io.wifi.starrailexpress.client.particle.CustomParticleHandlers}）。
 *
 * <p>为什么要这样做：{@code ServerLevel.sendParticles} 只能表达"在一个点上按高斯散布若干颗"，环形、
 * 螺旋、沿轨迹、跟随实体等形状用原版包表达不了；而在服务端循环里逐点发包又会把包数放大成百上千倍。
 * 因此约定：**需要自定义形状时，服务端只发这一个包，形状在客户端生成**（客户端逐点 addParticle 不走网络）。
 *
 * <p>一个包可以携带多条 entry，请尽量合并成一次发送（服务端用
 * {@code ParticleFx.sendCustomBatch}）。未知的 {@code id} 在客户端会被静默忽略，所以服务端可以先上、
 * 客户端后续补处理器，不会崩。
 */
public record CustomParticleS2CPayload(List<Entry> entries) implements CustomPacketPayload {

    /** 单个包最多携带多少条特效。 */
    public static final int MAX_ENTRIES = 64;
    /** 每条特效最多携带多少个自定义参数。 */
    public static final int MAX_PARAMS = 8;

    public static final Type<CustomParticleS2CPayload> ID = new Type<>(SRE.id("custom_particle_s2c"));

    public static final StreamCodec<FriendlyByteBuf, CustomParticleS2CPayload> CODEC = StreamCodec.ofMember(
            CustomParticleS2CPayload::write, CustomParticleS2CPayload::read);

    public CustomParticleS2CPayload {
        entries = entries.size() > MAX_ENTRIES
                ? List.copyOf(entries.subList(0, MAX_ENTRIES))
                : List.copyOf(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * 一条特效请求。
     *
     * @param id            客户端处理器 id（{@link ResourceLocation}）
     * @param x,y,z         原点（一般是特效中心，客户端按自己的形状规则围绕它摆点）
     * @param durationTicks 期望持续时间（tick）；0 表示"一次性/由客户端决定"
     * @param params        自定义参数（最多 {@link #MAX_PARAMS} 个），含义由该 id 的处理器定义
     */
    public record Entry(ResourceLocation id, double x, double y, double z, int durationTicks, List<Float> params) {

        public Entry {
            params = params.size() > MAX_PARAMS
                    ? List.copyOf(params.subList(0, MAX_PARAMS))
                    : List.copyOf(params);
        }

        /** 构造一条 entry，多余的参数会被丢弃。 */
        public static Entry at(ResourceLocation id, double x, double y, double z,
                int durationTicks, float... params) {
            int count = Math.min(params.length, MAX_PARAMS);
            List<Float> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                list.add(params[i]);
            }
            return new Entry(id, x, y, z, durationTicks, list);
        }
    }

    private static void write(CustomParticleS2CPayload payload, FriendlyByteBuf buf) {
        buf.writeVarInt(payload.entries.size());
        for (Entry entry : payload.entries) {
            buf.writeResourceLocation(entry.id());
            buf.writeDouble(entry.x());
            buf.writeDouble(entry.y());
            buf.writeDouble(entry.z());
            buf.writeVarInt(entry.durationTicks());
            List<Float> params = entry.params();
            buf.writeByte(params.size());
            for (float param : params) {
                buf.writeFloat(param);
            }
        }
    }

    private static CustomParticleS2CPayload read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) {
            throw new DecoderException("custom_particle_s2c: entries=" + count + " 超出 0.." + MAX_ENTRIES);
        }
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ResourceLocation id = buf.readResourceLocation();
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            int durationTicks = buf.readVarInt();
            int paramCount = buf.readUnsignedByte();
            if (paramCount > MAX_PARAMS) {
                throw new DecoderException("custom_particle_s2c: params=" + paramCount + " 超出 0.." + MAX_PARAMS);
            }
            List<Float> params = paramCount == 0 ? List.of() : new ArrayList<>(paramCount);
            for (int j = 0; j < paramCount; j++) {
                params.add(buf.readFloat());
            }
            entries.add(new Entry(id, x, y, z, durationTicks, params));
        }
        return new CustomParticleS2CPayload(entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
