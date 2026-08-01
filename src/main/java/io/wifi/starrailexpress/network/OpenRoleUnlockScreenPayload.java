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

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 → 客户端：打开职业解锁进度界面
 * 同时携带当前解锁数据（全局场次 + 强制解锁列表）供 GUI 展示。
 */
public record OpenRoleUnlockScreenPayload(
        int globalGamesPlayed,
        List<String> forceUnlockedRoles
) implements CustomPacketPayload {

    public static final Type<OpenRoleUnlockScreenPayload> ID =
            new Type<>(SRE.id("open_role_unlock_screen"));

    public static final StreamCodec<FriendlyByteBuf, OpenRoleUnlockScreenPayload> CODEC =
            CustomPacketPayload.codec(
                    OpenRoleUnlockScreenPayload::encode,
                    OpenRoleUnlockScreenPayload::decode);

    public static void encode(OpenRoleUnlockScreenPayload payload, FriendlyByteBuf buf) {
        buf.writeInt(payload.globalGamesPlayed());
        List<String> ids = payload.forceUnlockedRoles();
        buf.writeInt(ids.size());
        for (String id : ids) {
            buf.writeUtf(id);
        }
    }

    public static OpenRoleUnlockScreenPayload decode(FriendlyByteBuf buf) {
        int games = buf.readInt();
        int count = buf.readInt();
        List<String> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(buf.readUtf());
        }
        return new OpenRoleUnlockScreenPayload(games, ids);
    }

    @Override
    public Type<OpenRoleUnlockScreenPayload> type() {
        return ID;
    }
}
