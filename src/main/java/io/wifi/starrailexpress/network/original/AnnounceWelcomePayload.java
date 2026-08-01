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

package io.wifi.starrailexpress.network.original;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record AnnounceWelcomePayload(String role, int killers, int targets) implements CustomPacketPayload {
    public static final Type<AnnounceWelcomePayload> ID = new Type<>(SRE.id("announcewelcome"));
    public static final StreamCodec<FriendlyByteBuf, AnnounceWelcomePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, AnnounceWelcomePayload::role, ByteBufCodecs.INT, AnnounceWelcomePayload::killers,
            ByteBufCodecs.INT, AnnounceWelcomePayload::targets, AnnounceWelcomePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

}