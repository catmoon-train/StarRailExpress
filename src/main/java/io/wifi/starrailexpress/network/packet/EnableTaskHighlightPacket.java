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
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record EnableTaskHighlightPacket(boolean enable) implements CustomPacketPayload {
    public static final Type<EnableTaskHighlightPacket> ID = new Type<>(
            ResourceLocation.tryBuild(SRE.MOD_ID, "enable_task_highlight"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EnableTaskHighlightPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, EnableTaskHighlightPacket::enable,
            EnableTaskHighlightPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
