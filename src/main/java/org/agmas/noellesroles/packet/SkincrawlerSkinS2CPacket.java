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

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

public record SkincrawlerSkinS2CPacket(UUID skincrawlerId, UUID stolenSkinId) implements CustomPacketPayload {
    public static final Type<SkincrawlerSkinS2CPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "skincrawler_skin"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SkincrawlerSkinS2CPacket> CODEC = StreamCodec
            .of((buf, p) -> {
                buf.writeBoolean(p.stolenSkinId != null);
                if (p.stolenSkinId != null)
                    buf.writeUUID(p.skincrawlerId);
                buf.writeBoolean(p.stolenSkinId != null);
                if (p.stolenSkinId != null)
                    buf.writeUUID(p.stolenSkinId);
            }, buf -> {
                UUID sid = buf.readBoolean() ? buf.readUUID() : null;
                UUID skin = buf.readBoolean() ? buf.readUUID() : null;
                return new SkincrawlerSkinS2CPacket(sid, skin);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
