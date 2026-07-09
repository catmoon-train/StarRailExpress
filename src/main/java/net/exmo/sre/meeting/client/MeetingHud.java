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
    /** 投票结果展示时长（毫秒）。 */
    private static final long VOTE_RESULT_DISPLAY_MS = 6000;
    /** 投票结果淡出时长（毫秒）。 */
    private static final long VOTE_RESULT_FADE_MS = 500;

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

        // 投票结果展示（优先于其他阶段）
        if (MeetingClientHandler.showVoteResult) {
            long sinceResult = Util.getMillis() - MeetingClientHandler.voteResultReceiveMillis;
            if (sinceResult < VOTE_RESULT_DISPLAY_MS + VOTE_RESULT_FADE_MS) {
                float fade = 1.0F;
                if (sinceResult > VOTE_RESULT_DISPLAY_MS) {
                    fade = 1.0F - (sinceResult - VOTE_RESULT_DISPLAY_MS) / (float) VOTE_RESULT_FADE_MS;
                }
                renderVoteResult(g, client, fade);
            } else {
                MeetingClientHandler.showVoteResult = false;
            }
            // 投票结果展示期间不显示其他 HUD
            return;
        }

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
        Component subtitle = bodyFound
                ? Component.translatable("meeting.sre.subtitle.body",
                        MeetingClientHandler.reporterName, MeetingClientHandler.victimName)
                : Component.translatable("meeting.sre.subtitle.emergency", MeetingClientHandler.reporterName);

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
        renderSpeakers(g, client, py + panelH + 4);

        // 报告信息（面板上方）
        if (!MeetingClientHandler.victimName.isEmpty()) {
            Component reportInfo = Component.translatable("meeting.sre.subtitle.body",
                    MeetingClientHandler.reporterName, MeetingClientHandler.victimName);
            g.drawCenteredString(font, reportInfo, w / 2, py - 14, TEXT);
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

    // ==================== 投票结果展示 ====================

    private static void renderVoteResult(GuiGraphics g, Minecraft client, float fade) {
        int w = g.guiWidth();
        int h = g.guiHeight();
        var font = client.font;

        // 半透明遮罩
        int overlayAlpha = (int) (0xD0 * fade);
        g.fill(0, 0, w, h, (overlayAlpha << 24) | 0x100A05);

        // 上方 / 下方遮幅
        int barH = (int) (h * 0.12F);
        g.fillGradient(0, 0, w, barH, (int) (0xF0 * fade) << 24 | (0x100A05 & 0xFFFFFF),
                (int) (0xA0 * fade) << 24 | (0x100A05 & 0xFFFFFF));
        g.fillGradient(0, h - barH, w, h, (int) (0xA0 * fade) << 24 | (0x100A05 & 0xFFFFFF),
                (int) (0xF0 * fade) << 24 | (0x100A05 & 0xFFFFFF));

        // 标题：无人被驱逐 / XXX被驱逐了
        String expelledName = MeetingClientHandler.voteResultExpelledName;
        Component title;
        int titleColor;
        if (expelledName.isEmpty()) {
            title = Component.translatable("meeting.vote.result.none_expelled");
            titleColor = GOLD;
        } else {
            title = Component.translatable("meeting.vote.result.expelled", expelledName);
            titleColor = RED;
        }

        // 标题冲击式入场（0~400ms）
        long sinceResult = Util.getMillis() - MeetingClientHandler.voteResultReceiveMillis;
        float titleT = Mth.clamp(sinceResult / 400.0F, 0.0F, 1.0F);
        float eased = MeetingClientHandler.easeOutCubic(titleT);
        float scale = 2.2F - eased * 1.0F;
        int titleAlpha = (int) (255 * fade * Mth.clamp(sinceResult / 150.0F, 0.0F, 1.0F));

        g.pose().pushPose();
        g.pose().translate(w / 2.0F, h * 0.22F, 0);
        g.pose().scale(scale, scale, 1.0F);
        int titleW = font.width(title);
        g.drawString(font, title, -titleW / 2, -font.lineHeight / 2, (titleAlpha << 24) | (titleColor & 0xFFFFFF), true);
        g.pose().popPose();

        // 金色装饰线
        if (titleT > 0.3F) {
            float lineT = Mth.clamp((titleT - 0.3F) / 0.7F, 0.0F, 1.0F);
            int lineHalf = (int) (w * 0.16F * lineT);
            int ly = (int) (h * 0.22F) + 14;
            int lineAlpha = (int) (255 * fade);
            g.fill(w / 2 - lineHalf, ly, w / 2 + lineHalf, ly + 1, (lineAlpha << 24) | (GOLD & 0xFFFFFF));
            g.fill(w / 2 - lineHalf / 2, ly + 3, w / 2 + lineHalf / 2, ly + 4,
                    (int) (lineAlpha * 0.53) << 24 | (GOLD & 0xFFFFFF));
        }

        // 投票结果列表（500ms 后渐显）
        if (sinceResult > 500) {
            var entries = MeetingClientHandler.voteResultEntries;
            if (!entries.isEmpty()) {
                // 按票数降序排序
                var sorted = new java.util.ArrayList<>(entries);
                sorted.sort((a, b) -> Integer.compare(b.voteCount(), a.voteCount()));

                int maxVotes = sorted.isEmpty() ? 1 : sorted.get(0).voteCount();

                int entryH = 18;
                int entryGap = 4;
                int maxVisible = Math.min(sorted.size(), 12);
                int listH = maxVisible * (entryH + entryGap);
                int listY = (int) (h * 0.22F) + 42;
                int listX = w / 2 - 160;
                int listW = 320;

                // 列表背景
                g.fillGradient(listX - 8, listY - 6, listX + listW + 8, listY + listH + 6,
                        (int) (0xA0 * fade) << 24 | (0x100A05 & 0xFFFFFF),
                        (int) (0x60 * fade) << 24 | (0x100A05 & 0xFFFFFF));

                // 表头
                Component headerVotes = Component.translatable("meeting.vote.result.header_votes");
                Component headerName = Component.translatable("meeting.vote.result.header_name");
                int headerAlpha = (int) (180 * fade);
                g.drawString(font, headerName, listX, listY - 12, (headerAlpha << 24) | (MUTED & 0xFFFFFF), false);
                g.drawString(font, headerVotes, listX + listW - font.width(headerVotes), listY - 12,
                        (headerAlpha << 24) | (MUTED & 0xFFFFFF), false);

                for (int i = 0; i < maxVisible; i++) {
                    var entry = sorted.get(i);
                    int ey = listY + i * (entryH + entryGap);

                    // 行背景
                    if (i % 2 == 0) {
                        g.fill(listX - 4, ey, listX + listW + 4, ey + entryH,
                                (int) (0x22 * fade) << 24 | 0xFFFFFF);
                    }

                    // 被驱逐的玩家高亮
                    boolean isExpelled = entry.playerName().equals(expelledName);
                    int nameColor = isExpelled ? RED : TEXT;
                    int nameAlpha = (int) (255 * fade);
                    g.drawString(font, entry.playerName(), listX, ey + 3, (nameAlpha << 24) | (nameColor & 0xFFFFFF),
                            false);

                    // 票数 + 进度条
                    String voteStr = String.valueOf(entry.voteCount());
                    int barW = 100;
                    int barFill = maxVotes > 0 ? (int) (barW * (float) entry.voteCount() / maxVotes) : 0;
                    int barX = listX + listW - barW - font.width(voteStr) - 8;
                    int barY = ey + 8;

                    g.drawString(font, voteStr, listX + listW - font.width(voteStr), ey + 3,
                            (nameAlpha << 24) | (isExpelled ? RED : MUTED) & 0xFFFFFF, false);

                    g.fill(barX, barY, barX + barW, barY + 3,
                            (int) (0x40 * fade) << 24 | 0xFFFFFF);
                    if (barFill > 0) {
                        int barColor = isExpelled ? RED : GOLD;
                        g.fill(barX, barY, barX + barFill, barY + 3,
                                (int) (220 * fade) << 24 | (barColor & 0xFFFFFF));
                    }
                }
            }
        }
    }

}
