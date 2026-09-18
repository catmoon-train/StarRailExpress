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

import io.wifi.starrailexpress.client.gui.SREPanelStyle;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.client.sound.SpeakerClientSounds;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton;
import org.agmas.noellesroles.content.item.SpeakerItem;
import org.agmas.noellesroles.content.speaker.SpeakerTracks;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.packet.SpeakerC2SPacket;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 便携音响操作面板：左列表右播放器，控件按网格对齐。
 */
public class SpeakerScreen extends Screen {
    private static final int PAD = 10;
    private static final int GAP = 8;
    private static final int ROW_H = 20;
    private static final int SEARCH_H = 18;
    private static final int BTN_H = 20;
    private static final int TITLE_H = 22;
    private static final int LCD_H = 36;
    private static final int VOLUME_H = 16;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int leftX;
    private int leftW;
    private int listX;
    private int listY;
    private int listW;
    private int listH;
    private int rightX;
    private int rightW;
    private int lcdX;
    private int lcdY;
    private int lcdW;
    private int volumeX;
    private int volumeY;
    private int volumeW;

    private int selectedIndex;
    private boolean playing;
    private int volume = SpeakerItem.DEFAULT_VOLUME;
    private int scrollOffset;
    private boolean draggingVolume;
    private String searchText = "";
    private EditBox searchBox;
    private ModernButton playButton;
    private final List<Integer> filtered = new ArrayList<>();

    public SpeakerScreen() {
        super(Component.translatable("gui.noellesroles.speaker.title"));
    }

    @Override
    protected void init() {
        super.init();
        layoutPanel();
        loadState();
        rebuildFilter();
        ensureVisible();

        searchBox = new EditBox(this.font, leftX, panelY + TITLE_H + 2, leftW, SEARCH_H, Component.empty());
        searchBox.setMaxLength(40);
        searchBox.setValue(searchText);
        searchBox.setHint(SREPanelStyle.hint(Component.translatable("gui.noellesroles.speaker.search")));
        searchBox.setResponder(text -> {
            searchText = text == null ? "" : text;
            rebuildFilter();
            scrollOffset = 0;
        });
        addRenderableWidget(searchBox);

        int btnY = volumeY + VOLUME_H + 10;
        int btnW = Math.max(44, (rightW - GAP * 3) / 4);
        int x = rightX;
        addRenderableWidget(ModernButton.builder(Component.translatable("gui.noellesroles.speaker.prev"), b -> cycle(-1))
                .bounds(x, btnY, btnW, BTN_H)
                .accentBar()
                .build());
        x += btnW + GAP;
        playButton = ModernButton.builder(playLabel(), b -> togglePower())
                .bounds(x, btnY, btnW, BTN_H)
                .accentColor(playing ? SREPanelStyle.RED : SREPanelStyle.GREEN)
                .accentBar()
                .build();
        addRenderableWidget(playButton);
        x += btnW + GAP;
        addRenderableWidget(ModernButton.builder(Component.translatable("gui.noellesroles.speaker.next"), b -> cycle(1))
                .bounds(x, btnY, btnW, BTN_H)
                .accentBar()
                .build());
        x += btnW + GAP;
        addRenderableWidget(ModernButton.builder(Component.translatable("gui.noellesroles.speaker.shuffle"), b -> shuffle())
                .bounds(x, btnY, Math.min(btnW, rightX + rightW - x), BTN_H)
                .accentColor(SREPanelStyle.BLUE)
                .accentBar()
                .build());
    }

    private void layoutPanel() {
        panelW = Math.min(700, (int) (this.width * 0.9F));
        panelH = Mth.clamp((int) (this.height * 0.78F), 250, 360);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;

        leftW = Mth.clamp((int) (panelW * 0.42F), 180, 280);
        leftX = panelX + PAD;
        rightX = leftX + leftW + GAP;
        rightW = panelX + panelW - PAD - rightX;

        listX = leftX;
        listY = panelY + TITLE_H + 2 + SEARCH_H + 4;
        listW = leftW;
        listH = panelY + panelH - PAD - 16 - listY;

        lcdX = rightX;
        lcdY = panelY + TITLE_H + 2;
        lcdW = rightW;
        volumeX = rightX;
        volumeY = lcdY + LCD_H + 28;
        volumeW = rightW;
    }

    private void loadState() {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        var stack = SpeakerItem.findInInventory(minecraft.player);
        String trackId = SpeakerItem.getTrackId(stack);
        if (trackId.isEmpty()) {
            trackId = SpeakerClientSounds.currentTrack(minecraft.player.getUUID());
        }
        selectedIndex = SpeakerTracks.indexOf(trackId);
        playing = SpeakerItem.isPlaying(stack) || SpeakerClientSounds.isPlaying(minecraft.player.getUUID());
        volume = stack.isEmpty()
                ? SpeakerClientSounds.currentVolume(minecraft.player.getUUID())
                : SpeakerItem.getVolume(stack);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, this.width, this.height, SREPanelStyle.SCREEN_BG_TOP, SREPanelStyle.SCREEN_BG_BOTTOM);
        SREPanelStyle.drawPanel(g, panelX, panelY, panelW, panelH);
        SREPanelStyle.drawPanel(g, leftX - 2, panelY + TITLE_H, leftW + 4, panelH - TITLE_H - PAD,
                0xAA1A1008, 0xAA120A04);
        SREPanelStyle.drawPanel(g, rightX - 2, panelY + TITLE_H, rightW + 4, panelH - TITLE_H - PAD,
                0xAA1A1008, 0xAA0B1722);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(this.font, this.title, panelX + PAD, panelY + 7, SREPanelStyle.GOLD, false);
        Component status = Component.translatable(playing
                        ? "gui.noellesroles.speaker.playing"
                        : "gui.noellesroles.speaker.standby")
                .withStyle(playing ? ChatFormatting.GREEN : ChatFormatting.GRAY);
        g.drawString(this.font, status, panelX + panelW - PAD - this.font.width(status), panelY + 7,
                playing ? SREPanelStyle.GREEN : SREPanelStyle.MUTED, false);

        renderList(g, mouseX, mouseY);
        renderNowPlaying(g);
        renderVolume(g, mouseX, mouseY);

        Component hint = Component.translatable("gui.noellesroles.speaker.hint");
        g.drawString(this.font, hint, panelX + PAD, panelY + panelH - 14, SREPanelStyle.MUTED, false);
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        int visible = visibleRows();
        int maxScroll = Math.max(0, filtered.size() - visible);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        g.enableScissor(listX, listY, listX + listW, listY + listH);
        if (filtered.isEmpty()) {
            g.drawString(this.font, Component.translatable("gui.noellesroles.speaker.empty"),
                    listX + 4, listY + 6, SREPanelStyle.MUTED, false);
        } else {
            for (int i = 0; i < visible; i++) {
                int idx = scrollOffset + i;
                if (idx >= filtered.size()) {
                    break;
                }
                int trackIndex = filtered.get(idx);
                int y = listY + i * ROW_H;
                boolean selected = trackIndex == selectedIndex;
                boolean hover = mouseX >= listX && mouseX < listX + listW && mouseY >= y && mouseY < y + ROW_H - 1;
                if (selected) {
                    g.fillGradient(listX, y, listX + listW - SREPanelStyle.SCROLL_WIDTH - 2, y + ROW_H - 1,
                            SREPanelStyle.SELECTED_TOP, SREPanelStyle.SELECTED_BOTTOM);
                } else if (hover) {
                    g.fill(listX, y, listX + listW - SREPanelStyle.SCROLL_WIDTH - 2, y + ROW_H - 1,
                            SREPanelStyle.ROW_HOVER);
                }
                SpeakerTracks.Track track = SpeakerTracks.all().get(trackIndex);
                boolean hasIcon = track.icon() != null && track.icon() != net.minecraft.world.item.Items.AIR;
                int textX = listX + (hasIcon ? 20 : 4);
                int textW = listW - (hasIcon ? 34 : 18);
                if (hasIcon) {
                    g.renderItem(new net.minecraft.world.item.ItemStack(track.icon()), listX + 1, y + 2);
                }
                String name = this.font.plainSubstrByWidth(track.name().getString(), textW);
                int color = selected ? SREPanelStyle.TITLE : (hover ? SREPanelStyle.TEXT : SREPanelStyle.BODY);
                g.drawString(this.font, name, textX, y + 6, color, false);
            }
        }
        g.disableScissor();

        if (filtered.size() > visible) {
            int trackH = listH;
            int thumbH = Math.max(SREPanelStyle.SCROLL_MIN_THUMB, trackH * visible / filtered.size());
            int thumbY = listY + (int) ((trackH - thumbH) * (scrollOffset / (float) maxScroll));
            boolean hoverBar = mouseX >= listX + listW - SREPanelStyle.SCROLL_WIDTH && mouseX < listX + listW
                    && mouseY >= listY && mouseY < listY + listH;
            SREPanelStyle.drawScrollbar(g, listX + listW - SREPanelStyle.SCROLL_WIDTH, listY, listH,
                    thumbY, thumbH, hoverBar);
        }
    }

    private void renderNowPlaying(GuiGraphics g) {
        g.fill(lcdX, lcdY, lcdX + lcdW, lcdY + LCD_H, 0xCC001000);
        g.renderOutline(lcdX, lcdY, lcdW, LCD_H, playing ? 0xFF3A7A42 : SREPanelStyle.CARD_BORDER);
        g.fill(lcdX + 1, lcdY + 1, lcdX + lcdW - 1, lcdY + 2, playing ? 0x663AFF4A : 0x2200FF00);

        SpeakerTracks.Track track = currentTrack();
        String name = track == null ? "--" : track.name().getString();
        int lcdColor = playing ? 0xFF7DFF8A : 0xFF3A7A42;
        g.drawString(this.font, this.font.plainSubstrByWidth(name, lcdW - 36), lcdX + 6, lcdY + 6, lcdColor, false);
        int total = SpeakerTracks.all().size();
        Component index = Component.translatable("gui.noellesroles.speaker.track_index",
                selectedIndex + 1, Math.max(total, 1));
        g.drawString(this.font, index, lcdX + 6, lcdY + 20, 0xFF3A7A42, false);

        if (minecraft != null) {
            var icon = track != null && track.icon() != net.minecraft.world.item.Items.AIR
                    ? track.icon() : ModItems.SPEAKER;
            g.renderItem(new net.minecraft.world.item.ItemStack(icon), lcdX + lcdW - 22, lcdY + 8);
        }
    }

    private void renderVolume(GuiGraphics g, int mouseX, int mouseY) {
        Component label = Component.translatable("gui.noellesroles.speaker.volume", volume);
        g.drawString(this.font, label, volumeX, volumeY - 11, SREPanelStyle.BODY, false);

        int barY = volumeY;
        int barH = VOLUME_H;
        boolean hover = inside(mouseX, mouseY, volumeX, barY, volumeW, barH);
        g.fill(volumeX, barY, volumeX + volumeW, barY + barH, 0xFF120A04);
        g.renderOutline(volumeX, barY, volumeW, barH, hover || draggingVolume ? SREPanelStyle.GOLD : SREPanelStyle.CARD_BORDER);
        int fill = Math.round((volumeW - 2) * (volume / 100.0F));
        if (fill > 0) {
            g.fillGradient(volumeX + 1, barY + 1, volumeX + 1 + fill, barY + barH - 1,
                    0xFF3A7A42, 0xFF7DFF8A);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && inside((int) mouseX, (int) mouseY, volumeX, volumeY, volumeW, VOLUME_H)) {
            draggingVolume = true;
            setVolumeFromMouse(mouseX);
            return true;
        }
        if (button == 0 && inside((int) mouseX, (int) mouseY, listX, listY, listW, listH)) {
            int row = ((int) mouseY - listY) / ROW_H;
            int idx = scrollOffset + row;
            if (idx >= 0 && idx < filtered.size()) {
                selectedIndex = filtered.get(idx);
                click();
                if (playing) {
                    sendState(true);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingVolume && button == 0) {
            setVolumeFromMouse(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingVolume && button == 0) {
            draggingVolume = false;
            sendState(playing);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inside((int) mouseX, (int) mouseY, volumeX, volumeY, volumeW, VOLUME_H)) {
            int next = Mth.clamp(volume + (scrollY > 0 ? 5 : -5), 0, 100);
            if (next != volume) {
                volume = next;
                sendState(playing);
            }
            return true;
        }
        if (inside((int) mouseX, (int) mouseY, listX, listY, listW, listH)
                || inside((int) mouseX, (int) mouseY, leftX, panelY, leftW, panelH)) {
            scrollOffset -= scrollY > 0 ? 1 : -1;
            int maxScroll = Math.max(0, filtered.size() - visibleRows());
            scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox != null && searchBox.isFocused()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_SPACE) {
            togglePower();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_UP) {
            cycle(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_DOWN) {
            cycle(1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void setVolumeFromMouse(double mouseX) {
        int next = Mth.clamp((int) Math.round((mouseX - volumeX) * 100.0 / Math.max(1, volumeW)), 0, 100);
        if (next != volume) {
            volume = next;
            if (playing) {
                sendState(true);
            }
        }
    }

    private void cycle(int delta) {
        List<SpeakerTracks.Track> tracks = SpeakerTracks.all();
        if (tracks.isEmpty()) {
            return;
        }
        selectedIndex = Math.floorMod(selectedIndex + delta, tracks.size());
        rebuildFilter();
        ensureVisible();
        click();
        if (playing) {
            sendState(true);
        }
    }

    private void shuffle() {
        List<SpeakerTracks.Track> tracks = SpeakerTracks.all();
        if (tracks.size() < 2) {
            return;
        }
        int next = selectedIndex;
        while (next == selectedIndex) {
            next = minecraft != null && minecraft.level != null
                    ? minecraft.level.random.nextInt(tracks.size())
                    : (selectedIndex + 1) % tracks.size();
        }
        selectedIndex = next;
        rebuildFilter();
        ensureVisible();
        click();
        if (playing) {
            sendState(true);
        }
    }

    private void togglePower() {
        boolean next = !playing;
        if (next && currentTrack() == null) {
            return;
        }
        sendState(next);
        click();
    }

    private void sendState(boolean nextPlaying) {
        SpeakerTracks.Track track = currentTrack();
        if (track == null) {
            return;
        }
        this.playing = nextPlaying;
        if (playButton != null) {
            playButton.setMessage(playLabel());
        }
        ClientPlayNetworking.send(new SpeakerC2SPacket(track.id(), nextPlaying, volume));
    }

    private Component playLabel() {
        return Component.translatable(playing ? "gui.noellesroles.speaker.stop" : "gui.noellesroles.speaker.play");
    }

    private SpeakerTracks.Track currentTrack() {
        List<SpeakerTracks.Track> tracks = SpeakerTracks.all();
        if (tracks.isEmpty()) {
            return null;
        }
        selectedIndex = Mth.clamp(selectedIndex, 0, tracks.size() - 1);
        return tracks.get(selectedIndex);
    }

    private void rebuildFilter() {
        filtered.clear();
        List<SpeakerTracks.Track> tracks = SpeakerTracks.all();
        String query = searchText.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < tracks.size(); i++) {
            String name = tracks.get(i).name().getString().toLowerCase(Locale.ROOT);
            if (query.isEmpty() || name.contains(query)) {
                filtered.add(i);
            }
        }
    }

    private void ensureVisible() {
        int pos = filtered.indexOf(selectedIndex);
        if (pos < 0) {
            return;
        }
        int visible = visibleRows();
        if (pos < scrollOffset) {
            scrollOffset = pos;
        } else if (pos >= scrollOffset + visible) {
            scrollOffset = pos - visible + 1;
        }
    }

    private int visibleRows() {
        return Math.max(1, listH / ROW_H);
    }

    private void click() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2F));
        }
    }

    private static boolean inside(int x, int y, int rx, int ry, int rw, int rh) {
        return x >= rx && y >= ry && x < rx + rw && y < ry + rh;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
