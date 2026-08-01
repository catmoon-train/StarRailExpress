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
 * 客户端 -> 服务端：管理员对职业轮换名单的操作请求。服务端会校验权限（OP 等级 >= 2）。
 * <ul>
 *     <li>{@code action = "set"}：用 {@code json} 中的完整名单覆盖当前配置。</li>
 *     <li>{@code action = "enable"} / {@code "disable"}：开关名单是否接管职业的启用/禁用。</li>
 *     <li>{@code action = "clear"}：清空名单。</li>
 * </ul>
 */
public record RoleRosterUpdatePayload(String action, String json) implements CustomPacketPayload {
    public static final Type<RoleRosterUpdatePayload> ID = new Type<>(SRE.id("role_roster_update"));
    public static final StreamCodec<FriendlyByteBuf, RoleRosterUpdatePayload> CODEC =
            CustomPacketPayload.codec(RoleRosterUpdatePayload::write, RoleRosterUpdatePayload::new);

    private RoleRosterUpdatePayload(FriendlyByteBuf buffer) {
        this(buffer.readUtf(32), buffer.readUtf(1_048_576));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(action, 32);
        buffer.writeUtf(json, 1_048_576);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
