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
import net.minecraft.resources.ResourceLocation;

/**
 * 画板网络数据包
 * 只包含 C2S (客户端到服务端) 数据包的数据定义
 */
public class DrawingBoardPayload {

    public static final String ID = "drawing_board";

    // C2S: 客户端保存画板数据
    public record DrawBoardSavePayload(int selectedColor, byte[] pixels) implements CustomPacketPayload {
        public static final Type<DrawBoardSavePayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, ID + "_save"));
        public static final StreamCodec<FriendlyByteBuf, DrawBoardSavePayload> CODEC = StreamCodec.ofMember(
                DrawBoardSavePayload::encode,
                DrawBoardSavePayload::decode);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeByte(selectedColor);
            buf.writeByteArray(pixels);
        }

        public static DrawBoardSavePayload decode(FriendlyByteBuf buf) {
            int color = buf.readByte();
            byte[] pixels = buf.readByteArray();
            return new DrawBoardSavePayload(color, pixels);
        }
    }

    // C2S: 客户端请求识别并消耗画板
    public record DrawBoardRecognizePayload(int category) implements CustomPacketPayload {
        public static final Type<DrawBoardRecognizePayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, ID + "_recognize"));
        public static final StreamCodec<FriendlyByteBuf, DrawBoardRecognizePayload> CODEC = StreamCodec.ofMember(
                DrawBoardRecognizePayload::encode,
                DrawBoardRecognizePayload::decode);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeInt(category);
        }

        public static DrawBoardRecognizePayload decode(FriendlyByteBuf buf) {
            int category = buf.readInt();
            return new DrawBoardRecognizePayload(category);
        }
    }
}
