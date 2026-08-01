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

package net.exmo.sre.repair.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

public record RepairCombatFeedbackS2CPacket(int kind, int entityId, double x, double y, double z, String weaponId)
        implements CustomPacketPayload {
    public static final int ATTACK = 0;
    public static final int HIT = 1;
    public static final int DOWNED = 2;
    public static final int REVIVED = 3;
    public static final Type<RepairCombatFeedbackS2CPacket> ID = new Type<>(Noellesroles.id("repair_combat_feedback"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RepairCombatFeedbackS2CPacket> CODEC = StreamCodec
            .ofMember(RepairCombatFeedbackS2CPacket::encode, RepairCombatFeedbackS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(kind);
        buf.writeVarInt(entityId);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeUtf(weaponId == null ? "" : weaponId);
    }

    public static RepairCombatFeedbackS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new RepairCombatFeedbackS2CPacket(buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readUtf());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
