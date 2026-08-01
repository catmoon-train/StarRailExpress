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

package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BreakArmorPayload(double x, double y, double z) implements CustomPacketPayload {
    public static final ResourceLocation BREAK_ARMOR_ID = ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID,
            "break_armor");
    public static final Type<BreakArmorPayload> ID = new Type<>(BREAK_ARMOR_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, BreakArmorPayload> CODEC;

    public BreakArmorPayload(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeDouble(this.x);
        buf.writeDouble(this.y);
        buf.writeDouble(this.z);

    }

    public static BreakArmorPayload read(FriendlyByteBuf buf) {
        return new BreakArmorPayload(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    static {
        CODEC = StreamCodec.ofMember(BreakArmorPayload::write, BreakArmorPayload::read);
    }
}