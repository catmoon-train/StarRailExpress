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
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 -> 客户端：广播当前职业轮换名单（JSON 序列化的 {@code RoleRosterState}）。
 */
public record RoleRosterSyncPayload(String json) implements CustomPacketPayload {
    public static final Type<RoleRosterSyncPayload> ID = new Type<>(SRE.id("role_roster_sync"));
    public static final StreamCodec<FriendlyByteBuf, RoleRosterSyncPayload> CODEC =
            CustomPacketPayload.codec(RoleRosterSyncPayload::write, RoleRosterSyncPayload::new);

    private RoleRosterSyncPayload(FriendlyByteBuf buffer) {
        this(buffer.readUtf(1_048_576));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(json, 1_048_576);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
