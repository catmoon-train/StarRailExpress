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
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

/**
 * 服务端 → 客户端：自定义修饰符「死亡后倒计时」状态。
 *
 * <p>
 * 只发给被倒计时的当事人自己：{@code active = true} 表示开始（或刷新）倒计时，
 * {@code active = false} 表示结束 / 取消。客户端据此渲染与难民同款的倒计时 HUD。
 */
public record CustomModifierCountdownPacket(String modifierId, int group, String label, int seconds,
        boolean revive, boolean active) implements CustomPacketPayload {

    public static final Type<CustomModifierCountdownPacket> ID = new Type<>(SRE.id("custom_modifier_countdown"));

    public static final StreamCodec<FriendlyByteBuf, CustomModifierCountdownPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CustomModifierCountdownPacket::modifierId,
            ByteBufCodecs.VAR_INT, CustomModifierCountdownPacket::group,
            ByteBufCodecs.STRING_UTF8, CustomModifierCountdownPacket::label,
            ByteBufCodecs.VAR_INT, CustomModifierCountdownPacket::seconds,
            ByteBufCodecs.BOOL, CustomModifierCountdownPacket::revive,
            ByteBufCodecs.BOOL, CustomModifierCountdownPacket::active,
            CustomModifierCountdownPacket::new);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
