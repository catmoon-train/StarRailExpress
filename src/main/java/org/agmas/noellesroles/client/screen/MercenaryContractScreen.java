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

package org.agmas.noellesroles.client.screen;

import io.wifi.starrailexpress.client.SREClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.client.widget.MercenaryContractPlayerWidget;
import org.agmas.noellesroles.packet.MercenaryContractSignC2SPacket;
import org.agmas.noellesroles.role.ModRoles;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class MercenaryContractScreen extends Screen {
    private final List<MercenaryContractPlayerWidget> playerWidgets = new ArrayList<>();
    private EditBox searchWidget;

    public MercenaryContractScreen() {
        super(Component.translatable("screen.noellesroles.mercenary_contract.title"));
    }

    @Override
    protected void init() {
        super.init();
        refreshPlayers(null);
    }

    private void refreshPlayers(String searchText) {
        for (var w : playerWidgets) {
            removeWidget(w);
        }
        playerWidgets.clear();

        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            onClose();
            return;
        }

        List<PlayerInfo> candidates = new ArrayList<>();
        if (minecraft.getConnection() != null) {
            candidates.addAll(minecraft.getConnection().getOnlinePlayers());
        }
        candidates.sort(Comparator.comparing(info -> info.getProfile().getName(), String.CASE_INSENSITIVE_ORDER));
        candidates.removeIf(info -> info.getProfile().getId().equals(minecraft.player.getUUID()));

        if (SREClient.gameComponent != null && minecraft.level != null) {
            candidates.removeIf(info -> {
                var levelPlayer = minecraft.level.getPlayerByUUID(info.getProfile().getId());
                return levelPlayer != null && SREClient.gameComponent.isRole(levelPlayer, ModRoles.MERCENARY);
            });
        }

        if (searchText != null && !searchText.isBlank()) {
            String lower = searchText.toLowerCase();
            candidates.removeIf(info -> !info.getProfile().getName().toLowerCase().contains(lower));
        }

        int widgetSize = 32;
        int spacing = 8;
        int columns = Math.min(8, Math.max(candidates.size(), 1));
        int rows = Math.max(1, (int) Math.ceil(candidates.size() / 8.0));
        int totalWidth = columns * (widgetSize + spacing) - spacing;
        int totalHeight = rows * (widgetSize + spacing) - spacing;
        int startX = (width - totalWidth) / 2;
        int startY = (height - totalHeight) / 2 + 20;

        if (searchWidget == null) {
            searchWidget = new EditBox(font, startX, startY - 40, totalWidth, 20, Component.empty());
            searchWidget.setHint(Component.translatable("screen.noellesroles.search.placeholder").withStyle(ChatFormatting.GRAY));
            searchWidget.setResponder(this::refreshPlayers);
            addRenderableWidget(searchWidget);
        }

        boolean empty = candidates.isEmpty();
        searchWidget.setTextColor(empty ? Color.RED.getRGB() : Color.WHITE.getRGB());

        for (int i = 0; i < candidates.size(); i++) {
            int col = i % 8;
            int row = i / 8;
            int x = startX + col * (widgetSize + spacing);
            int y = startY + row * (widgetSize + spacing);

            PlayerInfo playerInfo = candidates.get(i);
            UUID uuid = playerInfo.getProfile().getId();
            String name = playerInfo.getProfile().getName();
            ResourceLocation skin = DefaultPlayerSkin.get(uuid).texture();
            if (playerInfo.getSkin() != null) {
                skin = playerInfo.getSkin().texture();
            }

            var widget = new MercenaryContractPlayerWidget(this, x, y, widgetSize, uuid, name, skin);
            playerWidgets.add(widget);
            addRenderableWidget(widget);
        }
    }

    public void onPlayerSelected(UUID targetUuid, String targetName) {
        if (Minecraft.getInstance().player == null) {
            return;
        }
        ClientPlayNetworking.send(new MercenaryContractSignC2SPacket(targetUuid));
        onClose();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        guiGraphics.drawCenteredString(
                font,
                Component.translatable("screen.noellesroles.mercenary_contract.title"),
                centerX,
                18,
                0xFFE6C37A
        );
        guiGraphics.drawCenteredString(
                font,
                Component.translatable("screen.noellesroles.mercenary_contract.subtitle"),
                centerX,
                34,
                0xFFFFFFFF
        );
    }
}
