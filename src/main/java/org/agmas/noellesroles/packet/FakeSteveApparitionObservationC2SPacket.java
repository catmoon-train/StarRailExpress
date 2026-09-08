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

package org.agmas.noellesroles.packet;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

/** Ordered client observation acknowledgement for a target-local apparition. */
public record FakeSteveApparitionObservationC2SPacket(UUID apparitionId, Stage stage)
        implements CustomPacketPayload {
    public enum Stage {
        OBSERVED,
        LOOKED_AWAY
    }

    public static final Type<FakeSteveApparitionObservationC2SPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID,
                    "fake_steve_apparition_observation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FakeSteveApparitionObservationC2SPacket> CODEC =
            StreamCodec.composite(UUIDUtil.STREAM_CODEC,
                    FakeSteveApparitionObservationC2SPacket::apparitionId,
                    ByteBufCodecs.idMapper(i -> Stage.values()[i], Stage::ordinal),
                    FakeSteveApparitionObservationC2SPacket::stage,
                    FakeSteveApparitionObservationC2SPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
