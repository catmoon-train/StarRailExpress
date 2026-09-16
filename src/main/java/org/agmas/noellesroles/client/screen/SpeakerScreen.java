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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.client.sound.SpeakerClientSounds;
import org.agmas.noellesroles.content.item.SpeakerItem;
import org.agmas.noellesroles.content.speaker.SpeakerTracks;
import org.agmas.noellesroles.packet.SpeakerC2SPacket;

import java.util.List;

/**
 * 音响正面操作面板。整张贴图就是机器本身，曲目走 LCD，开关走红色电源键。
 */
public class SpeakerScreen extends Screen {
    private static final ResourceLocation PANEL = ResourceLocation.fromNamespaceAndPath(
            Noellesroles.MOD_ID, "textures/gui/speaker_panel.png");
    private static final int TEX_W = 1024;
    private static final int TEX_H = 576;
    private static final int LCD_COLOR = 0xFF7DFF8A;
    private static final int LCD_DIM = 0xFF3A7A42;
    private static final int LCD_BG = 0x66001000;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int selectedIndex;
    private boolean playing;
    private float powerHover;

    public SpeakerScreen() {
        super(Component.empty());
    }

    @Override
    protected void init() {
        super.init();
        layoutPanel();
        if (minecraft != null && minecraft.player != null) {
            var stack = SpeakerItem.findInInventory(minecraft.player);
            String trackId = SpeakerItem.getTrackId(stack);
            if (trackId.isEmpty()) {
                trackId = SpeakerClientSounds.currentTrack(minecraft.player.getUUID());
            }
            selectedIndex = SpeakerTracks.indexOf(trackId);
            playing = SpeakerItem.isPlaying(stack)
                    || SpeakerClientSounds.isPlaying(minecraft.player.getUUID());
        }
    }

    private void layoutPanel() {
        panelW = Mth.clamp((int) (this.width * 0.78F), 320, 640);
        panelH = panelW * TEX_H / TEX_W;
        if (panelH > this.height - 20) {
            panelH = this.height - 20;
            panelW = panelH * TEX_W / TEX_H;
        }
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xC8080808);
        g.blit(PANEL, panelX, panelY, panelW, panelH, 0, 0, TEX_W, TEX_H, TEX_W, TEX_H);
        renderLcd(g);
        renderPowerGlow(g, mouseX, mouseY);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderLcd(GuiGraphics g) {
        int[] lcd = rect(0.325F, 0.148F, 0.350F, 0.108F);
        g.fill(lcd[0], lcd[1], lcd[0] + lcd[2], lcd[1] + lcd[3], LCD_BG);
        g.enableScissor(lcd[0], lcd[1], lcd[0] + lcd[2], lcd[1] + lcd[3]);

        SpeakerTracks.Track track = currentTrack();
        String name = track == null ? "--" : track.name().getString();
        if (name.length() > 22) {
            name = name.substring(0, 21) + ".";
        }
        Component line = Component.literal(name);
        int textX = lcd[0] + 6;
        int textY = lcd[1] + Math.max(2, (lcd[3] - 18) / 2);
        g.drawString(this.font, line, textX, textY, playing ? LCD_COLOR : LCD_DIM, false);

        String status = playing ? "PLAY" : "STBY";
        int statusX = lcd[0] + lcd[2] - this.font.width(status) - 5;
        int pulse = playing && (tickCount() / 8) % 2 == 0 ? LCD_COLOR : LCD_DIM;
        g.drawString(this.font, status, statusX, textY, pulse, false);
        g.disableScissor();
    }

    private void renderPowerGlow(GuiGraphics g, int mouseX, int mouseY) {
        int[] power = powerRect();
        boolean hover = inside(mouseX, mouseY, power);
        float target = hover ? 1.0F : 0.0F;
        powerHover += (target - powerHover) * 0.25F;
        if (powerHover > 0.02F) {
            int a = (int) (0x44 * powerHover);
            g.fill(power[0], power[1], power[0] + power[2], power[1] + power[3], (a << 24) | 0xFF6666);
        }
        if (playing) {
            int[] led = rect(0.618F, 0.575F, 0.018F, 0.032F);
            int flash = 0xCC000000 | (((tickCount() / 6) % 2 == 0) ? 0xFF2A2A : 0x7A1010);
            g.fill(led[0], led[1], led[0] + led[2], led[1] + led[3], flash);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (inside((int) mouseX, (int) mouseY, powerRect())
                    || inside((int) mouseX, (int) mouseY, rect(0.458F, 0.568F, 0.055F, 0.058F))) {
                togglePower();
                return true;
            }
            if (inside((int) mouseX, (int) mouseY, rect(0.408F, 0.568F, 0.042F, 0.058F))) {
                sendState(false);
                click();
                return true;
            }
            if (inside((int) mouseX, (int) mouseY, rect(0.348F, 0.568F, 0.052F, 0.058F))) {
                cycle(-1);
                return true;
            }
            if (inside((int) mouseX, (int) mouseY, rect(0.518F, 0.568F, 0.052F, 0.058F))) {
                cycle(1);
                return true;
            }
            if (inside((int) mouseX, (int) mouseY, rect(0.325F, 0.148F, 0.350F, 0.108F))) {
                cycle(1);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inside((int) mouseX, (int) mouseY, new int[]{panelX, panelY, panelW, panelH})) {
            cycle(scrollY > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void cycle(int delta) {
        List<SpeakerTracks.Track> tracks = SpeakerTracks.all();
        if (tracks.isEmpty()) {
            return;
        }
        selectedIndex = Math.floorMod(selectedIndex + delta, tracks.size());
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
        ClientPlayNetworking.send(new SpeakerC2SPacket(track.id(), nextPlaying));
    }

    private SpeakerTracks.Track currentTrack() {
        List<SpeakerTracks.Track> tracks = SpeakerTracks.all();
        if (tracks.isEmpty()) {
            return null;
        }
        selectedIndex = Mth.clamp(selectedIndex, 0, tracks.size() - 1);
        return tracks.get(selectedIndex);
    }

    private void click() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2F));
        }
    }

    private int[] powerRect() {
        return rect(0.385F, 0.662F, 0.230F, 0.100F);
    }

    private int[] rect(float rx, float ry, float rw, float rh) {
        return new int[]{
                panelX + Math.round(panelW * rx),
                panelY + Math.round(panelH * ry),
                Math.round(panelW * rw),
                Math.round(panelH * rh)
        };
    }

    private static boolean inside(int x, int y, int[] r) {
        return x >= r[0] && y >= r[1] && x < r[0] + r[2] && y < r[1] + r[3];
    }

    private int tickCount() {
        return minecraft != null && minecraft.player != null ? minecraft.player.tickCount : 0;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
