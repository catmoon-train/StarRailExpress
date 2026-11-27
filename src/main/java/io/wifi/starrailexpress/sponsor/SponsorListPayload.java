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

package io.wifi.starrailexpress.sponsor;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 -> 客户端：同步当前赞助者名单（每个名字对应一个 {@code <name>_plush}）与赞助页链接。
 * 客户端据此在游戏介绍 GUI 的「赞助者」栏目列出名单、渲染头像、提供赞助入口。
 * <p>
 * {@code pageUrl} 允许为空：此时客户端只显示名单，不显示可点击的赞助入口。
 */
public record SponsorListPayload(List<String> names, String pageUrl) implements CustomPacketPayload {
    public static final Type<SponsorListPayload> ID = new Type<>(SRE.id("sponsor_list_sync"));
    public static final StreamCodec<FriendlyByteBuf, SponsorListPayload> CODEC =
            CustomPacketPayload.codec(SponsorListPayload::write, SponsorListPayload::new);

    public SponsorListPayload(List<String> names) {
        this(names, "");
    }

    private SponsorListPayload(FriendlyByteBuf buffer) {
        this(read(buffer), buffer.readUtf());
    }

    private static List<String> read(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<String> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(buffer.readUtf());
        }
        return list;
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(names.size());
        for (String name : names) {
            buffer.writeUtf(name);
        }
        buffer.writeUtf(pageUrl == null ? "" : pageUrl);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
