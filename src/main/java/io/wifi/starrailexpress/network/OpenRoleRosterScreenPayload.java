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
 * 服务端 -> 客户端：请求打开职业轮换界面。{@code admin} 为 true 时打开管理员编辑界面，否则打开玩家查看界面。
 */
public record OpenRoleRosterScreenPayload(boolean admin) implements CustomPacketPayload {
    public static final Type<OpenRoleRosterScreenPayload> ID = new Type<>(SRE.id("open_role_roster_screen"));
    public static final StreamCodec<FriendlyByteBuf, OpenRoleRosterScreenPayload> CODEC =
            CustomPacketPayload.codec(OpenRoleRosterScreenPayload::write, OpenRoleRosterScreenPayload::new);

    private OpenRoleRosterScreenPayload(FriendlyByteBuf buffer) {
        this(buffer.readBoolean());
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeBoolean(admin);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
