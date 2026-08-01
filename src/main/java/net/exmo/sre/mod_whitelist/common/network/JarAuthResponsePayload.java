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

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import static io.wifi.starrailexpress.SRE.MOD_ID;

/**
 * C2S：JAR 密钥认证应答。
 *
 * @param hasKey   客户端 jar 是否带有嵌入密钥（未签名 jar 直接如实上报）
 * @param response HMAC-SHA256(key, digest|nonce|version) 的 hex；无密钥时为空串
 * @param version  客户端模组版本（须与服务端一致）
 */
public record JarAuthResponsePayload(boolean hasKey, String response, String version)
        implements CustomPacketPayload {

    public static final Type<JarAuthResponsePayload> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "jar_auth_response"));

    public static final StreamCodec<FriendlyByteBuf, JarAuthResponsePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, JarAuthResponsePayload::hasKey,
            ByteBufCodecs.STRING_UTF8, JarAuthResponsePayload::response,
            ByteBufCodecs.STRING_UTF8, JarAuthResponsePayload::version,
            JarAuthResponsePayload::new);

    @Override
    public Type<JarAuthResponsePayload> type() {
        return ID;
    }
}
