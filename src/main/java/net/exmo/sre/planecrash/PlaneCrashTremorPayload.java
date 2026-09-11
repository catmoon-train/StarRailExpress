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

package net.exmo.sre.planecrash;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 服务端通知客户端播放一次大地震颤：屏幕抖动、倾泻、碎玻璃与楼房倒塌音效。 */
public record PlaneCrashTremorPayload(float tiltYaw, int durationTicks) implements CustomPacketPayload {
    public static final Type<PlaneCrashTremorPayload> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, "plane_crash_tremor"));
    public static final StreamCodec<FriendlyByteBuf, PlaneCrashTremorPayload> CODEC = StreamCodec
            .ofMember(PlaneCrashTremorPayload::encode, PlaneCrashTremorPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(tiltYaw);
        buf.writeVarInt(durationTicks);
    }

    public static PlaneCrashTremorPayload decode(FriendlyByteBuf buf) {
        return new PlaneCrashTremorPayload(buf.readFloat(), buf.readVarInt());
    }
}
