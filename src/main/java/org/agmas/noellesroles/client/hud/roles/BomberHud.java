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

package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.util.SREItemUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.content.item.BombItem;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;

public class BomberHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.BOMBER_ID, (context, deltaTracker) -> {
            final Minecraft client = Minecraft.getInstance();
            final LocalPlayer player = client.player;
            ItemStack mainHandItem = player.getMainHandItem();

            // 计算背包中的炸弹数量
            int bombCount = SREItemUtils.countItem(client.player, ModItems.BOMB);
            if (client.player.getOffhandItem().is(ModItems.BOMB)) {
                bombCount += client.player.getOffhandItem().getCount();
            }

            // 渲染位置 - 右下角
            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = screenWidth - 150; // 距离右边缘
            int y = screenHeight - 50; // 距离底部

            Font textRenderer = client.font;

            // 显示炸弹数量
            Component countText = Component.translatable("hud.noellesroles.bomber.count", bombCount);
            context.drawString(textRenderer, countText, x, y, 0xFFFFFF);

            // 显示购买消耗
            Component costText = Component.translatable("hud.noellesroles.bomber.cost");
            context.drawString(textRenderer, costText, x, y + 12, 0xFFFF00);
            if (mainHandItem.is(ModItems.BOMB)) {
                if (!SREClient.gameComponent.isKillerTeam(player)) {
                    String text = "!!!BOMB!!!";

                    int width = client.getWindow().getGuiScaledWidth();
                    int height = client.getWindow().getGuiScaledHeight();

                    context.drawCenteredString(client.font, text, width / 2, height / 2 - 20, 0xFF0000);
                    return;
                }
                CustomData customData = mainHandItem.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
                CompoundTag tag = customData.copyTag();

                if (tag.contains(BombItem.TIMER_KEY)) {
                    int timer = tag.getInt(BombItem.TIMER_KEY);
                    String text = String.format("%.1fs", timer / 20.0f);

                    int width = client.getWindow().getGuiScaledWidth();
                    int height = client.getWindow().getGuiScaledHeight();

                    context.drawCenteredString(client.font, text, width / 2, height / 2 - 20, 0xFF0000);
                }
            }
        });
    }
}