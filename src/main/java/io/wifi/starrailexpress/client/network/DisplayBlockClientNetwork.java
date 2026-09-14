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

package io.wifi.starrailexpress.client.network;

import io.wifi.starrailexpress.client.gui.screen.BlockDisplayBlockScreen;
import io.wifi.starrailexpress.client.gui.screen.TextDisplayBlockScreen;
import io.wifi.starrailexpress.network.DisplayBlockPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

/**
 * 展示方块的客户端网络处理：收到 {@link DisplayBlockPayload.OpenUI} 就打开对应编辑界面，
 * 并提供发送保存包的入口。保存后的数据由原版方块实体更新包同步回来，不需要额外处理。
 */
public class DisplayBlockClientNetwork {

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(DisplayBlockPayload.OpenUI.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                Minecraft minecraft = context.client();
                Level level = minecraft.level;
                if (level == null) {
                    return;
                }
                BlockPos pos = payload.pos();
                Screen screen = isBlockDisplay(level, pos)
                        ? new BlockDisplayBlockScreen(pos, payload.data())
                        : new TextDisplayBlockScreen(pos, payload.data());
                minecraft.setScreen(screen);
            });
        });
    }

    private static boolean isBlockDisplay(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof io.wifi.starrailexpress.content.block_entity.BlockDisplayBlockEntity
                || level.getBlockState(pos).getBlock() instanceof io.wifi.starrailexpress.content.block.BlockDisplayBlock;
    }

    /** 把编辑好的展示数据发回服务端。 */
    public static void sendSave(BlockPos pos, CompoundTag data) {
        if (Minecraft.getInstance().getConnection() != null) {
            ClientPlayNetworking.send(new DisplayBlockPayload.Save(pos, data));
        }
    }
}
