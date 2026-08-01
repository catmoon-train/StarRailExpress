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

public record ToggleInsaneSkillC2SPacket(boolean toggle) implements CustomPacketPayload {
    public static final ResourceLocation TOGGLE_INSANE_SKILL_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "toggle_insane_skill");
    public static final Type<ToggleInsaneSkillC2SPacket> ID = new Type<>(TOGGLE_INSANE_SKILL_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleInsaneSkillC2SPacket> CODEC;


    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(toggle);
    }

    public static ToggleInsaneSkillC2SPacket read(FriendlyByteBuf buf) {
        return new ToggleInsaneSkillC2SPacket(buf.readBoolean());
    }


    static {
        CODEC = StreamCodec.ofMember(ToggleInsaneSkillC2SPacket::write, ToggleInsaneSkillC2SPacket::read);
    }
}