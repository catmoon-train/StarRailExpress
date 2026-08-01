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

// Source code is decompiled from a .class file using FernFlower decompiler (from Intellij IDEA).
package io.wifi.starrailexpress.network.packet;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

public record EditNewspaperPacket(List<String> pages, Optional<String> title) implements CustomPacketPayload {
    public static final int MAX_BYTES_PER_CHAR = 4;
    public static final StreamCodec<FriendlyByteBuf, EditNewspaperPacket> STREAM_CODEC;
    public static final Type<EditNewspaperPacket> ID = new Type<>(
            ResourceLocation.tryBuild(SRE.MOD_ID, "newspaper/edit"));

    static {
        STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(8192).apply(ByteBufCodecs.list(200)), EditNewspaperPacket::pages,
                ByteBufCodecs.stringUtf8(128).apply(ByteBufCodecs::optional), EditNewspaperPacket::title,
                EditNewspaperPacket::new);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
