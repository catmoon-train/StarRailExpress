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
import net.exmo.sre.mod_whitelist.common.ResourcePackInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public record ResourcePackWhitelistPayload(
		List<ResourcePackInfo> resourcePacks,
		boolean includeHashes
) implements CustomPacketPayload {
	public static final Type<ResourcePackWhitelistPayload> ID = new Type<>(SRE.id("resource_pack_whitelist"));

	@SuppressWarnings("UnstableApiUsage")
	public static final StreamCodec<FriendlyByteBuf, ResourcePackWhitelistPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.collection(ArrayList::new,
					StreamCodec.composite(
							ByteBufCodecs.STRING_UTF8,
							ResourcePackInfo::packId,
							ByteBufCodecs.STRING_UTF8,
							ResourcePackInfo::sha256,
							ResourcePackInfo::new
					)
			),
			ResourcePackWhitelistPayload::resourcePacks,
			ByteBufCodecs.BOOL,
			ResourcePackWhitelistPayload::includeHashes,
			ResourcePackWhitelistPayload::new
	);

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return ID;
	}

	public ResourcePackWhitelistPayload {
		if (resourcePacks == null) {
			throw new IllegalArgumentException("resourcePacks cannot be null");
		}
	}
}
