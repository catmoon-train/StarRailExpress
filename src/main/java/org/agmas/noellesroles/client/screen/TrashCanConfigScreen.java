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

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.packet.TrashCanConfigC2SPacket;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class TrashCanConfigScreen extends Screen {
    private final BlockPos pos;
    private boolean whitelistEnabled;
    private boolean blacklistEnabled;
    private final List<String> whitelist;
    private final List<String> blacklist;
    private Button whitelistButton;
    private Button blacklistButton;
    private EditBox whitelistInput;
    private EditBox blacklistInput;

    public TrashCanConfigScreen(BlockPos pos, boolean whitelistEnabled, List<String> whitelist,
            boolean blacklistEnabled, List<String> blacklist) {
        super(Component.translatable("screen.noellesroles.trash_can.title"));
        this.pos = pos;
        this.whitelistEnabled = whitelistEnabled;
        this.blacklistEnabled = blacklistEnabled;
        this.whitelist = new ArrayList<>(whitelist);
        this.blacklist = new ArrayList<>(blacklist);
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int y = Math.max(24, height / 2 - 118);

        whitelistButton = Button.builder(toggleText("whitelist", whitelistEnabled), btn -> {
            whitelistEnabled = !whitelistEnabled;
            whitelistButton.setMessage(toggleText("whitelist", whitelistEnabled));
            updateInputVisibility();
        }).bounds(centerX - 132, y + 24, 264, 20).build();
        addRenderableWidget(whitelistButton);

        whitelistInput = new EditBox(font, centerX - 132, y + 50, 232, 20, Component.empty());
        whitelistInput.setHint(Component.translatable("screen.noellesroles.trash_can.item_id"));
        addRenderableWidget(whitelistInput);
        addRenderableWidget(Button.builder(Component.literal("+"), btn -> addItem(whitelistInput, whitelist))
                .bounds(centerX + 106, y + 50, 26, 20).build());

        blacklistButton = Button.builder(toggleText("blacklist", blacklistEnabled), btn -> {
            blacklistEnabled = !blacklistEnabled;
            blacklistButton.setMessage(toggleText("blacklist", blacklistEnabled));
            updateInputVisibility();
        }).bounds(centerX - 132, y + 104, 264, 20).build();
        addRenderableWidget(blacklistButton);

        blacklistInput = new EditBox(font, centerX - 132, y + 130, 232, 20, Component.empty());
        blacklistInput.setHint(Component.translatable("screen.noellesroles.trash_can.item_id"));
        addRenderableWidget(blacklistInput);
        addRenderableWidget(Button.builder(Component.literal("+"), btn -> addItem(blacklistInput, blacklist))
                .bounds(centerX + 106, y + 130, 26, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("screen.noellesroles.trash_can.save"), btn -> save())
                .bounds(centerX - 42, y + 206, 84, 20).build());
        updateInputVisibility();
    }

    private Component toggleText(String type, boolean enabled) {
        return Component.translatable("screen.noellesroles.trash_can." + type + "." + (enabled ? "yes" : "no"));
    }

    private void updateInputVisibility() {
        whitelistInput.visible = whitelistEnabled;
        blacklistInput.visible = blacklistEnabled;
        children().stream().filter(Button.class::isInstance).map(Button.class::cast).forEach(button -> {
            if ("+".equals(button.getMessage().getString())) {
                button.visible = button.getY() < blacklistButton.getY() ? whitelistEnabled : blacklistEnabled;
            }
        });
    }

    private void addItem(EditBox input, List<String> target) {
        String value = input.getValue().trim();
        if (!value.isBlank() && !target.contains(value)) {
            target.add(value);
            input.setValue("");
        }
    }

    private void save() {
        ClientPlayNetworking.send(new TrashCanConfigC2SPacket(pos, whitelistEnabled, whitelist, blacklistEnabled, blacklist));
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        int centerX = width / 2;
        int y = Math.max(24, height / 2 - 118);
        g.drawCenteredString(font, title, centerX, y, 0xFFFFFF);
        if (whitelistEnabled) {
            g.drawString(font, Component.translatable("screen.noellesroles.trash_can.whitelist.items"), centerX - 132, y + 74, 0xAAAAAA);
            drawItems(g, whitelist, centerX - 132, y + 86);
        }
        if (blacklistEnabled) {
            g.drawString(font, Component.translatable("screen.noellesroles.trash_can.blacklist.items"), centerX - 132, y + 154, 0xAAAAAA);
            drawItems(g, blacklist, centerX - 132, y + 166);
        }
    }

    private void drawItems(GuiGraphics g, List<String> items, int x, int y) {
        if (items.isEmpty()) {
            g.drawString(font, Component.translatable("screen.noellesroles.trash_can.empty"), x, y, 0x777777);
            return;
        }
        int max = Math.min(items.size(), 3);
        for (int i = 0; i < max; i++) {
            g.drawString(font, items.get(items.size() - 1 - i), x, y + i * 10, 0xDDDDDD);
        }
    }
}
