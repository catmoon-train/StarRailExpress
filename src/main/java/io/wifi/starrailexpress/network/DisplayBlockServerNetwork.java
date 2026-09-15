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

import io.wifi.starrailexpress.content.block.DisplayBlockBase;
import io.wifi.starrailexpress.content.block_entity.DisplayAnimation;
import io.wifi.starrailexpress.content.block_entity.DisplayBlockEntityBase;
import io.wifi.starrailexpress.util.EditorGuard;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * 展示方块的服务端网络处理。
 *
 * <p>权限在服务端判定两次：打开界面时（{@link DisplayBlockBase#canEdit}）和保存时，
 * 客户端的 UI 只是体验，改不动数据。
 */
public class DisplayBlockServerNetwork {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(DisplayBlockPayload.Save.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                if (!EditorGuard.canEditAt(player, payload.pos())) {
                    return;
                }
                BlockPos pos = payload.pos();
                if (player.level().getBlockEntity(pos) instanceof DisplayBlockEntityBase display) {
                    CompoundTag incoming = payload.data();
                    // 单次动画以"保存这一刻"为起点，这样它是确定性的、也随时能通过再保存一次重播。
                    if (DisplayAnimation.isEnabled(incoming) && !DisplayAnimation.isLooping(incoming)) {
                        incoming.putLong(DisplayAnimation.TAG_START, player.level().getGameTime());
                    }
                    // 内容没变就不广播：省掉一次全量 NBT 发包，也不把区块标脏。
                    if (display.setDisplayData(incoming)) {
                        display.syncToClients();
                    }
                }
            });
        });
    }

    /** 发给单个玩家：打开编辑界面并带上当前展示数据。 */
    public static void sendOpenUI(ServerPlayer player, BlockPos pos, DisplayBlockEntityBase display) {
        ServerPlayNetworking.send(player, new DisplayBlockPayload.OpenUI(pos, display.getDisplayData()));
    }
}
