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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.netty.buffer.ByteBuf;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.game.modes.funny.volunteer.VolunteerDraftState.Phase;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record VolunteerDraftSyncS2CPacket(
        Phase phase,
        int remainingTime,
        List<String> myCandidates,
        Map<UUID, String> finalRoles,
        String myFinalRole,
        int volunteerCount) implements CustomPacketPayload {

    public static final Type<VolunteerDraftSyncS2CPacket> TYPE = new Type<>(
            ResourceLocation.tryBuild(SRE.MOD_ID, "volunteer/draft_sync"));

    private static final StreamCodec<ByteBuf, Phase> PHASE_CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeInt(packet.ordinal()),
            buf -> {
                int a = buf.readInt();
                if (a >= 0 && a < Phase.values().length) {
                    return Phase.values()[a];
                }
                return Phase.WAITING;
            });
    private static final StreamCodec<ByteBuf, Map<UUID, String>> FINAL_ROLES_CODEC = ByteBufCodecs.map(HashMap::new,
            StreamCodec.of(FriendlyByteBuf::writeUUID, FriendlyByteBuf::readUUID),
            ByteBufCodecs.STRING_UTF8, 256);

    public static final StreamCodec<ByteBuf, VolunteerDraftSyncS2CPacket> CODEC = new StreamCodec<>() {
        @Override
        public VolunteerDraftSyncS2CPacket decode(ByteBuf buf) {
            Phase phase = PHASE_CODEC.decode(buf);
            int remaining = ByteBufCodecs.VAR_INT.decode(buf);
            List<String> candidates = ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).decode(buf);
            Map<UUID, String> finalRoles = FINAL_ROLES_CODEC.decode(buf);
            String myFinal = ByteBufCodecs.STRING_UTF8.decode(buf);
            int volunteerCount = ByteBufCodecs.VAR_INT.decode(buf);
            return new VolunteerDraftSyncS2CPacket(phase, remaining, candidates, finalRoles, myFinal, volunteerCount);
        }

        @Override
        public void encode(ByteBuf buf, VolunteerDraftSyncS2CPacket pkt) {
            PHASE_CODEC.encode(buf, pkt.phase);
            ByteBufCodecs.VAR_INT.encode(buf, pkt.remainingTime);
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).encode(buf, pkt.myCandidates);
            FINAL_ROLES_CODEC.encode(buf, pkt.finalRoles);
            ByteBufCodecs.STRING_UTF8.encode(buf, pkt.myFinalRole);
            ByteBufCodecs.VAR_INT.encode(buf, pkt.volunteerCount);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}