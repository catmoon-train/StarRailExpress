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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.packet.ConductorDoorListS2CPacket;
import org.agmas.noellesroles.packet.ConductorSelectDoorC2SPacket;

import java.util.List;

/** 船长舱门调度：从当前地图其他房间门中选择传送目标。 */
public class ConductorDoorSelectScreen extends Screen {
    private static final int VISIBLE = 8;
    private static final int BUTTON_WIDTH = 180;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 4;

    private final List<ConductorDoorListS2CPacket.DoorEntry> doors;
    private int scroll;
    private boolean selected;
    private boolean closed;

    public ConductorDoorSelectScreen(List<ConductorDoorListS2CPacket.DoorEntry> doors) {
        super(Component.translatable("screen.noellesroles.conductor.select_door"));
        this.doors = List.copyOf(doors);
    }

    @Override
    protected void init() {
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();
        int maxScroll = Math.max(0, doors.size() - VISIBLE);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        int startY = height / 2 - (VISIBLE * (BUTTON_HEIGHT + GAP)) / 2;
        int x = width / 2 - BUTTON_WIDTH / 2;
        int end = Math.min(doors.size(), scroll + VISIBLE);
        for (int i = scroll; i < end; i++) {
            ConductorDoorListS2CPacket.DoorEntry door = doors.get(i);
            int y = startY + (i - scroll) * (BUTTON_HEIGHT + GAP);
            addRenderableWidget(Button.builder(
                    Component.translatable("screen.noellesroles.conductor.door", door.name()),
                    button -> pick(door))
                    .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(x, startY + VISIBLE * (BUTTON_HEIGHT + GAP) + 8, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    private void pick(ConductorDoorListS2CPacket.DoorEntry door) {
        selected = true;
        ClientPlayNetworking.send(new ConductorSelectDoorC2SPacket(door.pos()));
        onClose();
    }

    @Override
    public void onClose() {
        if (!closed && !selected) {
            ClientPlayNetworking.send(new ConductorSelectDoorC2SPacket(null));
        }
        closed = true;
        super.onClose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = Math.max(0, doors.size() - VISIBLE);
        int next = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll);
        if (next != scroll) {
            scroll = next;
            rebuildButtons();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(font, title, width / 2, 18, 0xFFE6C37A);
        if (doors.size() > VISIBLE) {
            Component hint = Component.translatable("screen.noellesroles.conductor.scroll_hint");
            graphics.drawCenteredString(font, hint, width / 2, height - 28, 0xFFAAAAAA);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
