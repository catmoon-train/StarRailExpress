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

package net.exmo.sre.client.chat;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S 网络包：客户端→服务端 通知对话推进或分支选择。
 * 服务端收到后可执行当前行或所选分支绑定的 command，必要时继续打开新的对话。
 */
public record ChatDialogueAdvancePayload(
        String dialogueId,
    int lineIndex,
    int choiceIndex,
    int focusEntityId
) implements CustomPacketPayload {

    public static final Type<ChatDialogueAdvancePayload> ID =
            new Type<>(SRE.id("chat_dialogue_advance"));

    public static final StreamCodec<FriendlyByteBuf, ChatDialogueAdvancePayload> CODEC =
            CustomPacketPayload.codec(ChatDialogueAdvancePayload::write, ChatDialogueAdvancePayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(dialogueId);
        buf.writeInt(lineIndex);
        buf.writeInt(choiceIndex);
        buf.writeInt(focusEntityId);
    }

    private static ChatDialogueAdvancePayload read(FriendlyByteBuf buf) {
        return new ChatDialogueAdvancePayload(buf.readUtf(), buf.readInt(), buf.readInt(), buf.readInt());
    }

    public static ChatDialogueAdvancePayload advance(String dialogueId, int lineIndex) {
        return new ChatDialogueAdvancePayload(dialogueId, lineIndex, -1, -1);
    }

    public static ChatDialogueAdvancePayload select(String dialogueId, int lineIndex,
                                                    int choiceIndex, int focusEntityId) {
        return new ChatDialogueAdvancePayload(dialogueId, lineIndex, choiceIndex, focusEntityId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
