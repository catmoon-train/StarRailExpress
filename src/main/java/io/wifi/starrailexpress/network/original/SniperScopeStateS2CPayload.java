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

package io.wifi.starrailexpress.network.original;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

public record SniperScopeStateS2CPayload(boolean scopeAttached) implements CustomPacketPayload {
    public static final Type<SniperScopeStateS2CPayload> TYPE = new Type<>(SRE.id("sniper_scope_state_s2c"));
    public static final StreamCodec<FriendlyByteBuf, SniperScopeStateS2CPayload> STREAM_CODEC = StreamCodec.ofMember(
            SniperScopeStateS2CPayload::write,
            SniperScopeStateS2CPayload::new
    );

    private SniperScopeStateS2CPayload(FriendlyByteBuf buf) {
        this(buf.readBoolean());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBoolean(scopeAttached);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
