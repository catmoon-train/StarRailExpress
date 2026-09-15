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

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * 「自定义列车物品·投掷物」的区域粒子（服务端 → 客户端）。
 *
 * <p>
 * 与烟雾弹的 {@code CreateClientSmokeAreaPacket} 同一套做法：服务端只在创建时发一次包，
 * 粒子由<b>每个客户端自己渲染</b>（客户端还会按距离裁剪），比服务端每 tick 广播粒子省得多。
 *
 * @param center        区域中心（落点）
 * @param radius        区域半径
 * @param durationTicks 持续 tick
 * @param particleId    粒子 id（空串 = 客户端用默认粒子）
 * @param planar        true = 平面区域（燃烧弹式，粒子贴地铺开）；false = 球形（烟雾弹式）
 */
public record CustomAreaParticlePayload(Vec3 center, double radius, int durationTicks, String particleId,
        boolean planar) implements CustomPacketPayload {

    public static final ResourceLocation PAYLOAD_ID = SRE.id("custom_area_particle");
    public static final Type<CustomAreaParticlePayload> ID = new Type<>(PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, CustomAreaParticlePayload> CODEC;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVec3(center);
        buf.writeDouble(radius);
        buf.writeInt(durationTicks);
        buf.writeUtf(particleId == null ? "" : particleId);
        buf.writeBoolean(planar);
    }

    public static CustomAreaParticlePayload read(FriendlyByteBuf buf) {
        return new CustomAreaParticlePayload(buf.readVec3(), buf.readDouble(), buf.readInt(), buf.readUtf(),
                buf.readBoolean());
    }

    static {
        CODEC = StreamCodec.ofMember(CustomAreaParticlePayload::write, CustomAreaParticlePayload::read);
    }
}
