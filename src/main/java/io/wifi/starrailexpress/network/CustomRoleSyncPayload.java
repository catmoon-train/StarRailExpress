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
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 自定义职业同步 Payload（服务端 → 客户端）
 * 支持分块传输：当 JSON 超过 Minecraft writeUtf 的 32767 字节限制时自动分块
 */
public class CustomRoleSyncPayload implements CustomPacketPayload {
    public static final ResourceLocation PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, "custom_role_sync");
    public static final Type<CustomRoleSyncPayload> TYPE = new Type<>(PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, CustomRoleSyncPayload> CODEC;

    /** 每块最大字符数（留安全余量，远小于 32767） */
    public static final int MAX_CHUNK_CHARS = 30000;

    private final int hash;
    private final int totalChunks;
    private final int chunkIndex;
    private final String chunkData;

    public CustomRoleSyncPayload(int hash, int totalChunks, int chunkIndex, String chunkData) {
        this.hash = hash;
        this.totalChunks = totalChunks;
        this.chunkIndex = chunkIndex;
        this.chunkData = chunkData;
    }

    public int hash() { return hash; }
    public int totalChunks() { return totalChunks; }
    public int chunkIndex() { return chunkIndex; }
    public String chunkData() { return chunkData; }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(hash);
        buf.writeInt(totalChunks);
        buf.writeInt(chunkIndex);
        buf.writeUtf(chunkData);
    }

    public static CustomRoleSyncPayload read(FriendlyByteBuf buf) {
        int hash = buf.readInt();
        int totalChunks = buf.readInt();
        int chunkIndex = buf.readInt();
        String chunkData = buf.readUtf();
        return new CustomRoleSyncPayload(hash, totalChunks, chunkIndex, chunkData);
    }

    static {
        CODEC = StreamCodec.ofMember(CustomRoleSyncPayload::write, CustomRoleSyncPayload::read);
    }
}
