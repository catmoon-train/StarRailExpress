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
import org.agmas.noellesroles.Noellesroles;

public record ReasonerOpenScreenS2CPacket(
        String roleTargetName,
        String bodyTargetName,
        String taskTargetName,
        boolean deathReasonQuestionAvailable,
        boolean killerQuestionAvailable,
        boolean solvedAliveCount,
        boolean solvedRole,
        boolean solvedDeathReason,
        boolean solvedTask,
        boolean solvedKillerCount,
        int cooldownTicks) implements CustomPacketPayload {

    public static final Type<ReasonerOpenScreenS2CPacket> ID = new Type<>(Noellesroles.id("reasoner_open_screen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ReasonerOpenScreenS2CPacket> CODEC = StreamCodec
            .ofMember(ReasonerOpenScreenS2CPacket::encode, ReasonerOpenScreenS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(roleTargetName);
        buf.writeUtf(bodyTargetName);
        buf.writeUtf(taskTargetName);
        buf.writeBoolean(deathReasonQuestionAvailable);
        buf.writeBoolean(killerQuestionAvailable);
        buf.writeBoolean(solvedAliveCount);
        buf.writeBoolean(solvedRole);
        buf.writeBoolean(solvedDeathReason);
        buf.writeBoolean(solvedTask);
        buf.writeBoolean(solvedKillerCount);
        buf.writeVarInt(cooldownTicks);
    }

    public static ReasonerOpenScreenS2CPacket decode(RegistryFriendlyByteBuf buf) {
        String roleTargetName = buf.readUtf();
        String bodyTargetName = buf.readUtf();
        String taskTargetName = buf.readUtf();
        boolean deathReasonQuestionAvailable = buf.readBoolean();
        boolean killerQuestionAvailable = buf.readBoolean();
        boolean solvedAliveCount = buf.readBoolean();
        boolean solvedRole = buf.readBoolean();
        boolean solvedDeathReason = buf.readBoolean();
        boolean solvedTask = buf.readBoolean();
        boolean solvedKillerCount = buf.readBoolean();
        int cooldownTicks = buf.readVarInt();
        return new ReasonerOpenScreenS2CPacket(roleTargetName, bodyTargetName, taskTargetName,
                deathReasonQuestionAvailable, killerQuestionAvailable, 
                solvedAliveCount, solvedRole, solvedDeathReason, solvedTask, solvedKillerCount, cooldownTicks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
