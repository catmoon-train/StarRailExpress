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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 嬉命人·调色盘：把「目标 UUID -> 渲染颜色」下发给客户端渲染层。 */
public record EmbalmerPaletteS2CPacket(Map<UUID, Integer> tints, int remainingTicks)
        implements CustomPacketPayload {
    public static final Type<EmbalmerPaletteS2CPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "embalmer_palette"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EmbalmerPaletteS2CPacket> CODEC =
            StreamCodec.of((buf, p) -> {
                buf.writeInt(p.tints.size());
                for (var e : p.tints.entrySet()) {
                    buf.writeUUID(e.getKey());
                    buf.writeInt(e.getValue());
                }
                buf.writeInt(p.remainingTicks);
            }, buf -> {
                int size = buf.readInt();
                Map<UUID, Integer> tints = new LinkedHashMap<>();
                for (int i = 0; i < size; i++) {
                    tints.put(buf.readUUID(), buf.readInt());
                }
                return new EmbalmerPaletteS2CPacket(tints, buf.readInt());
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static EmbalmerPaletteS2CPacket clear() {
        return new EmbalmerPaletteS2CPacket(Map.of(), 0);
    }
}
