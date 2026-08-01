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

package net.exmo.sre.mod_whitelist.common.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

/**
 * Payload to send mod whitelist configuration from server to client
 * Sent when player joins the game to inform client about sync settings
 */
public record ModWhitelistConfigPayload(boolean syncHashValues) implements CustomPacketPayload {
	public static final Type<ModWhitelistConfigPayload> ID = new Type<>(SRE.id("mod_whitelist_config"));
	
	@SuppressWarnings("UnstableApiUsage")
	public static final StreamCodec<FriendlyByteBuf, ModWhitelistConfigPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL,
			ModWhitelistConfigPayload::syncHashValues,
			ModWhitelistConfigPayload::new
	);

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}