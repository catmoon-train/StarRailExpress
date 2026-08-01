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
 * S2C：JAR 密钥认证挑战。服务端在玩家加入时下发一次性随机 nonce，
 * 客户端须用嵌入密钥 + 自身 jar 摘要计算 HMAC 应答（见 JarAuthCore）。
 */
public record JarAuthChallengePayload(String nonce) implements CustomPacketPayload {

    public static final Type<JarAuthChallengePayload> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "jar_auth_challenge"));

    public static final StreamCodec<FriendlyByteBuf, JarAuthChallengePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, JarAuthChallengePayload::nonce,
            JarAuthChallengePayload::new);

    @Override
    public Type<JarAuthChallengePayload> type() {
        return ID;
    }
}
