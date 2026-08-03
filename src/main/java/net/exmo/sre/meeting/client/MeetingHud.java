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

package net.exmo.sre.meeting.client;

import net.exmo.sre.meeting.MeetingManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * 紧急会议 HUD（复古车票风格，见 docs/ui_style.md）：
 * <ul>
 * <li>开场：上下黑色遮幅滑入 + 大标题冲击式入场（缩放回弹 + 微震屏）+ 副标题渐显；</li>
 * <li>讨论：顶部计时面板（渐变底 + 金色进度条），发言者名牌横排（金框脉冲），
 * 底部发言键提示与自己“发言中”状态；</li>
 * <li>结束：整体淡出。</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public final class MeetingHud {

    private static final int PANEL_TOP = 0xD81A1008;
    private static final int PANEL_BOTTOM = 0xD820140A;
    private static final int BORDER = 0xFF8B6914;
    private static final int DECO = 0x33FFE8C0;
    private static final int GOLD = 0xFFD4AF37;
    private static final int TEXT = 0xFFFFF4DC;
    private static final int MUTED = 0xFF9E8B6E;
    private static final int RED = 0xFFE06B65;
    private static final int GREEN = 0xFF72C17B;

    private static final long INTRO_TITLE_MS = 650;
    private static final long FADE_OUT_MS = 400;

    private MeetingHud() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register(MeetingHud::render);
    }

    private static void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.options.hideGui) {
            return;
        }
        int phase = MeetingClientHandler.phase;
        long sincePhase = Util.getMillis() - MeetingClientHandler.phaseChangeMillis;

        if (phase == MeetingManager.PHASE_INTRO) {
            renderIntro(g, client, sincePhase);
        } else if (phase == MeetingManager.PHASE_DISCUSS) {
            renderDiscussion(g, client);
        } else if (MeetingClientHandler.lastPhase != MeetingManager.PHASE_NONE && sincePhase < FADE_OUT_MS) {
            // 结束淡出：一条渐隐的金色横线收尾
            float fade = 1.0F - sincePhase / (float) FADE_OUT_MS;
            int alpha = (int) (0x66 * fade) << 24;
            int cy = g.guiHeight() / 2;
            g.fill(0, cy, g.guiWidth(), cy + 1, alpha | (GOLD & 0xFFFFFF));
        }
    }

    // ==================== 开场动画 ====================

    private static void renderIntro(GuiGraphics g, Minecraft client, long sincePhase) {
        int w = g.guiWidth();
        int h = g.guiHeight();

        // 遮幅滑入
        float barT = MeetingClientHandler.easeOutCubic(Mth.clamp(sincePhase / 450.0F, 0.0F, 1.0F));
        int barH = (int) (h * 0.16F * barT);
        g.fillGradient(0, 0, w, barH, 0xF0100A05, 0xC0100A05);
        g.fillGradient(0, h - barH, w, h, 0xC0100A05, 0xF0100A05);
        if (barH > 2) {
            g.fill(0, barH - 1, w, barH, 0x66D4AF37);
            g.fill(0, h - barH, w, h - barH + 1, 0x66D4AF37);
        }

        boolean bodyFound = !MeetingClientHandler.victimName.isEmpty();
        Component title = Component.translatable(bodyFound ? "meeting.sre.title.body" : "meeting.sre.title.emergency");
        if (MeetingClientHandler.reporterName == null) {
            MeetingClientHandler.reporterName = "meeting.sre.subtitle.a_player";
        }
        Component reporterNameComponent = MeetingClientHandler.reporterName.equals("meeting.sre.subtitle.a_player")
                ? Component.translatable("meeting.sre.subtitle.a_player")
                : Component.literal(MeetingClientHandler.reporterName);
        Component subtitle = bodyFound
                ? Component.translatable("meeting.sre.subtitle.body",
                        reporterNameComponent, MeetingClientHandler.victimName)
                : Component.translatable("meeting.sre.subtitle.emergency", reporterNameComponent);

        // 标题冲击式入场：过冲缩放（3.6 → 2.4）+ 微震屏
        float titleT = Mth.clamp(sincePhase / (float) INTRO_TITLE_MS, 0.0F, 1.0F);
        float eased = MeetingClientHandler.easeOutCubic(titleT);
        float scale = 3.6F - eased * 1.2F;
        float shake = sincePhase < 500 ? (1.0F - sincePhase / 500.0F) * 2.5F : 0.0F;
        float shakeX = shake * Mth.sin(sincePhase * 0.9F);
        float shakeY = shake * Mth.cos(sincePhase * 1.3F);
        int titleAlpha = (int) (255 * Mth.clamp(sincePhase / 180.0F, 0.0F, 1.0F));

        var font = client.font;
        g.pose().pushPose();
        g.pose().translate(w / 2.0F + shakeX, h * 0.36F + shakeY, 0);
        g.pose().scale(scale, scale, 1.0F);
        int titleW = font.width(title);
        int titleColor = (titleAlpha << 24) | (RED & 0xFFFFFF);
        g.drawString(font, title, -titleW / 2, -font.lineHeight / 2, titleColor, true);
        g.pose().popPose();

        // 标题下的金色装饰双线
        if (titleT > 0.35F) {
            float lineT = MeetingClientHandler.easeOutCubic((titleT - 0.35F) / 0.65F);
            int lineHalf = (int) (w * 0.18F * lineT);
            int ly = (int) (h * 0.36F) + 18;
            g.fill(w / 2 - lineHalf, ly, w / 2 + lineHalf, ly + 1, GOLD);
            g.fill(w / 2 - lineHalf / 2, ly + 3, w / 2 + lineHalf / 2, ly + 4, 0x88D4AF37);
        }

        // 副标题渐显
        if (sincePhase > 500) {
            int subAlpha = (int) (255 * Mth.clamp((sincePhase - 500) / 300.0F, 0.0F, 1.0F));
            int subColor = (subAlpha << 24) | (TEXT & 0xFFFFFF);
            g.drawCenteredString(font, subtitle, w / 2, (int) (h * 0.36F) + 28, subColor);
        }
    }

    // ==================== 讨论阶段 ====================

    private static void renderDiscussion(GuiGraphics g, Minecraft client) {
        int w = g.guiWidth();
        var font = client.font;

        // 顶部计时面板
        int panelW = Math.min(240, (int) (w * 0.5F));
        int panelH = 34;
        int px = (w - panelW) / 2;
        int py = 8;
        g.fillGradient(px, py, px + panelW, py + panelH, PANEL_TOP, PANEL_BOTTOM);
        g.renderOutline(px, py, panelW, panelH, BORDER);
        g.fill(px + 1, py + 1, px + panelW - 1, py + 2, DECO);

        long remainingTicks = Math.max(0, MeetingClientHandler.phaseEndGameTime - client.level.getGameTime());
        int seconds = (int) (remainingTicks / 20);
        String timeText = String.format("%d:%02d", seconds / 60, seconds % 60);
        int timeColor = seconds <= 10 && client.level.getGameTime() % 20 < 10 ? RED
                : seconds <= 30 ? 0xFFFFAA33 : TEXT;
        g.drawCenteredString(font,
                Component.translatable("meeting.sre.discussion").withStyle(ChatFormatting.BOLD), w / 2, py + 6, GOLD);
        g.drawCenteredString(font, Component.literal(timeText), w / 2, py + 18, timeColor);

        // 讨论时间进度条（面板底部内侧 1px 金条）
        float progress = Mth.clamp(remainingTicks / (float) MeetingClientHandler.discussTotalTicks, 0.0F, 1.0F);
        int barX0 = px + 4;
        int barX1 = px + panelW - 4;
        int barY = py + panelH - 4;
        g.fill(barX0, barY, barX1, barY + 1, 0x33000000);
        int fillX = barX0 + Math.round((barX1 - barX0) * progress);
        if (fillX > barX0) {
            g.fill(barX0, barY, fillX, barY + 1, GOLD);
        }

        // 发言者名牌（面板正下方横排，金框脉冲）
        int speakersY = py + panelH + 4;
        renderSpeakers(g, client, speakersY);
        Component reporterNameComponent = MeetingClientHandler.reporterName.equals("meeting.sre.subtitle.a_player")
                ? Component.translatable("meeting.sre.subtitle.a_player")
                : Component.literal(MeetingClientHandler.reporterName);
        // 报告信息（发言人名牌下方；过宽时自动缩放，避免溢出屏幕）
        if (!MeetingClientHandler.victimName.isEmpty()) {
            Component reportInfo = Component.translatable("meeting.sre.subtitle.body",
                    reporterNameComponent, MeetingClientHandler.victimName);
            drawReportInfo(g, client, reportInfo, w / 2, speakersY + 18, TEXT, w - 20);
        }

        // 底部：发言键提示 / 自己发言中的状态
        boolean self = client.player != null
                && MeetingClientHandler.participants.contains(client.player.getUUID());
        if (self) {
            Component hint;
            int color;
            if (MeetingClientHandler.isSpeakingToggled()) {
                hint = Component.translatable("meeting.sre.speaking");
                float pulse = 0.6F + 0.4F * Mth.sin((Util.getMillis() % 900L) / 900.0F * Mth.TWO_PI);
                color = ((int) (0xFF * pulse) << 24) | (GREEN & 0xFFFFFF);
            } else {
                hint = Component.translatable("meeting.sre.speak_hint",
                        MeetingClientHandler.speakKey.getTranslatedKeyMessage());
                color = MUTED;
            }
            g.drawCenteredString(font, hint, w / 2, g.guiHeight() - 44, color);
        }

        // 跳过会议已迁移至分号键，不再渲染按钮
    }

    // ==================== 跳过会议按钮 ====================

    // private static void renderSkipButton(GuiGraphics g, Minecraft client) {
    // int[] r = MeetingClientHandler.skipButtonRect();
    // int bx = r[0], by = r[1], bw = r[2], bh = r[3];
    // boolean voted = MeetingClientHandler.skipVoted;

    // // 按钮底色与边框
    // int fill = voted ? 0xAA3A2A12 : 0xAA1A1008;
    // g.fill(bx, by, bx + bw, by + bh, fill);
    // g.renderOutline(bx, by, bw, bh, voted ? GREEN : BORDER);

    // var font = client.font;
    // Component label = Component.translatable(voted ?
    // "meeting.sre.skip_button_voted"
    // : "meeting.sre.skip_button");
    // g.drawCenteredString(font, label, bx + bw / 2, by + (bh - font.lineHeight) /
    // 2,
    // voted ? GREEN : TEXT);

    // // 进度：按钮旁边显示「已有多少人跳过会议」（超过存活玩家二分之一即跳过）
    // int alive = MeetingClientHandler.skipAliveCount;
    // int need = alive / 2 + 1;
    // Component progress = Component.translatable("meeting.sre.skip_progress",
    // MeetingClientHandler.skipCount, need);
    // g.drawString(font, progress, bx + bw + 6, by + (bh - font.lineHeight) / 2,
    // MUTED);
    // }

    /** 居中绘制报告信息：文本过宽时按 maxWidth 等比缩小，避免溢出屏幕。 */
    private static void drawReportInfo(GuiGraphics g, Minecraft client, Component text, int cx, int y,
            int color, int maxWidth) {
        var font = client.font;
        int textW = font.width(text);
        float scale = 1.0F;
        if (textW > maxWidth && textW > 0) {
            scale = (float) maxWidth / textW;
        }
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(scale, scale, 1.0F);
        g.drawCenteredString(font, text, 0, 0, color);
        g.pose().popPose();
    }

    private static void renderSpeakers(GuiGraphics g, Minecraft client, int y) {
        if (MeetingClientHandler.speakers.isEmpty()) {
            return;
        }
        var font = client.font;
        int gap = 6;
        int totalW = 0;
        var names = new java.util.ArrayList<Component>();
        for (UUID uuid : MeetingClientHandler.speakers) {
            Player speaker = client.level.getPlayerByUUID(uuid);
            if (speaker == null) {
                continue;
            }
            Component name = speaker.getName();
            names.add(name);
            totalW += font.width(name) + 12 + gap;
        }
        if (names.isEmpty()) {
            return;
        }
        totalW -= gap;
        int x = (g.guiWidth() - totalW) / 2;
        float pulse = 0.55F + 0.45F * Mth.sin((Util.getMillis() % 1100L) / 1100.0F * Mth.TWO_PI);
        int borderColor = (((int) (0xFF * pulse)) << 24) | (GOLD & 0xFFFFFF);
        for (Component name : names) {
            int cardW = font.width(name) + 12;
            g.fillGradient(x, y, x + cardW, y + 14, PANEL_TOP, PANEL_BOTTOM);
            g.renderOutline(x, y, cardW, 14, borderColor);
            g.drawString(font, name, x + 6, y + 3, TEXT, false);
            x += cardW + 6;
        }
    }

}
