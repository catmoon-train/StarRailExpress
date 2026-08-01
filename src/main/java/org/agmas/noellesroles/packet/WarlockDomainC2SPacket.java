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

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

/**
 * 咒术师·领域展开技能包。
 * 客户端在背包 {@code LimitedInventoryScreen} 点选一名已被诅咒且存活的目标，请求对其展开领域。
 */
public record WarlockDomainC2SPacket(UUID target) implements CustomPacketPayload {
    public static final ResourceLocation WARLOCK_DOMAIN_PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "warlock_domain");
    public static final CustomPacketPayload.Type<WarlockDomainC2SPacket> ID = new CustomPacketPayload.Type<>(WARLOCK_DOMAIN_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, WarlockDomainC2SPacket> CODEC;

    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.target);
    }

    public static WarlockDomainC2SPacket read(FriendlyByteBuf buf) {
        return new WarlockDomainC2SPacket(buf.readUUID());
    }

    static {
        CODEC = StreamCodec.ofMember(WarlockDomainC2SPacket::write, WarlockDomainC2SPacket::read);
    }
}
