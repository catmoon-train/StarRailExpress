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

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CustomNarratorPacket(Component content, boolean shouldInterrupt) implements CustomPacketPayload {
    public static final Type<CustomNarratorPacket> ID = new Type<>(
            ResourceLocation.tryBuild(SRE.MOD_ID, "custom_narrator"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CustomNarratorPacket> CODEC = StreamCodec.composite(
            ComponentSerialization.TRUSTED_STREAM_CODEC, CustomNarratorPacket::content, ByteBufCodecs.BOOL,
            CustomNarratorPacket::shouldInterrupt, CustomNarratorPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}