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
import net.exmo.sre.mod_whitelist.common.ShaderPackInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public record ShaderPackWhitelistPayload(
		List<ShaderPackInfo> shaderPacks,
		boolean includeHashes
) implements CustomPacketPayload {
	public static final Type<ShaderPackWhitelistPayload> ID = new Type<>(SRE.id("shader_pack_whitelist"));

	@SuppressWarnings("UnstableApiUsage")
	public static final StreamCodec<FriendlyByteBuf, ShaderPackWhitelistPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.collection(ArrayList::new,
					StreamCodec.composite(
							ByteBufCodecs.STRING_UTF8,
							ShaderPackInfo::packId,
							ByteBufCodecs.STRING_UTF8,
							ShaderPackInfo::sha256,
							ShaderPackInfo::new
					)
			),
			ShaderPackWhitelistPayload::shaderPacks,
			ByteBufCodecs.BOOL,
			ShaderPackWhitelistPayload::includeHashes,
			ShaderPackWhitelistPayload::new
	);

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return ID;
	}

	public ShaderPackWhitelistPayload {
		if (shaderPacks == null) {
			throw new IllegalArgumentException("shaderPacks cannot be null");
		}
	}
}
