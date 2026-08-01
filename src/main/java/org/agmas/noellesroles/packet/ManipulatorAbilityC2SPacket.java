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

/**
 * 操纵师附身期间，请求以被操控目标的身份释放目标自身的技能。
 * 无需负载：服务端从操纵师组件中读取当前目标，冷却记在目标身上。
 */
public record ManipulatorAbilityC2SPacket() implements CustomPacketPayload {
    public static final ResourceLocation PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "manipulator_ability");
    public static final CustomPacketPayload.Type<ManipulatorAbilityC2SPacket> ID =
            new CustomPacketPayload.Type<>(PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ManipulatorAbilityC2SPacket> CODEC =
            StreamCodec.unit(new ManipulatorAbilityC2SPacket());

    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
