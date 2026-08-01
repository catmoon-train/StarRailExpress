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

package io.wifi.starrailexpress.client.gui;

import org.jetbrains.annotations.NotNull;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMinigameTaskComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public class MiniGameHudRenderer {
    
    private static final int MINIGAME_GOLD = 0xFFFFD36A;
    private static final int MINIGAME_TEXT_SOFT = 0xFFFFE6A3;
    private static final int MINIGAME_TRACK = 0x66FFD36A;
    private static final int SABOTAGE_RED = 0xFFFF4A4A;
    private static final int MINIGAME_NOTICE_DURATION = 70;
    private static int lastMinigamePending = -1;
    private static int lastMinigameTokens = -1;
    private static int minigameNoticeTicks = 0;
    private static Component minigameNoticeText = Component.empty();
    
    public static void render(@NotNull Player player, Font textRenderer, FakeGuiGraphics context,
            DeltaTracker tickCounter) {
        SREGameWorldComponent gameWorldComponent = SREClient.gameComponent;
        if (gameWorldComponent == null || !gameWorldComponent.isRunning() || !SREClient.isPlayerAliveAndInSurvival())
            return;
        // 小游戏任务计数（金色），渲染在普通任务列表下方。
        // 代币（货币）改到金币显示旁边渲染，见 HudStoreRenderer / StoreRenderer。
        SREPlayerMinigameTaskComponent minigameTask = SREPlayerMinigameTaskComponent.KEY.get(player);
        if (minigameTask != null) {
            tickMinigameNotice(minigameTask);
            int lineY = 6 + 10 * HudMoodRenderer.renderers.size() + 8;
            if (minigameTask.hasPendingTask() || minigameNoticeTicks > 0) {
                renderMinigameTaskHud(textRenderer, context, minigameTask, lineY);
                lineY += 12;
            }
            if (minigameTask.hasSabotageTask()) {
                renderSabotageTaskHud(textRenderer, context, minigameTask, lineY);
            }
        }
    }
    
    private static void tickMinigameNotice(SREPlayerMinigameTaskComponent minigameTask) {
        int pending = minigameTask.getPendingMinigameTasks();
        int tokens = minigameTask.getTokens();

        if (lastMinigamePending < 0 || lastMinigameTokens < 0) {
            lastMinigamePending = pending;
            lastMinigameTokens = tokens;
            return;
        }

        if (pending > lastMinigamePending) {
            minigameNoticeText = Component.translatable("subtitle.minigame_task.new");
            minigameNoticeTicks = MINIGAME_NOTICE_DURATION;
        } else if (tokens > lastMinigameTokens) {
            minigameNoticeText = Component.translatable("subtitle.minigame_task.done", tokens - lastMinigameTokens);
            minigameNoticeTicks = MINIGAME_NOTICE_DURATION;
        }

        lastMinigamePending = pending;
        lastMinigameTokens = tokens;
        if (minigameNoticeTicks > 0) {
            minigameNoticeTicks--;
        }
    }

    private static void renderMinigameTaskHud(Font textRenderer, FakeGuiGraphics context,
            SREPlayerMinigameTaskComponent minigameTask, int lineY) {
        Component title;
        if (minigameTask.hasPendingTask()) {
            var targetMg = minigameTask.getTargetMinigame();
            if (targetMg != null) {
                title = Component.translatable("hud.sre.minigame_task_specific", targetMg.displayName());
            } else {
                title = Component.translatable("hud.sre.minigame_task_any");
            }
        } else {
            title = Component.translatable("hud.sre.minigame_task_any");
        }
        boolean hasNotice = minigameNoticeTicks > 0 && minigameNoticeText != null;
        int titleWidth = textRenderer.width(title);
        int noticeWidth = hasNotice ? textRenderer.width(minigameNoticeText) : 0;
        int x = 22;
        int y = lineY;

        float noticeProgress = hasNotice ? minigameNoticeTicks / (float) MINIGAME_NOTICE_DURATION : 0f;
        float pulse = hasNotice ? 0.5f + 0.5f * Mth.sin((MINIGAME_NOTICE_DURATION - minigameNoticeTicks) * 0.35f) : 0f;

        int titleAlpha = hasNotice ? 0xFF : 0xDD;
        context.drawString(textRenderer, title, x, y, (titleAlpha << 24) | (MINIGAME_GOLD & 0x00FFFFFF), false);

        if (hasNotice) {
            int noticeAlpha = (int) (Mth.clamp(noticeProgress * 1.8f, 0.0f, 1.0f) * (190 + 45 * pulse));
            int noticeX = x + titleWidth + 8;
            context.drawString(textRenderer, "·", x + titleWidth + 3, y, noticeAlpha << 24 | 0x00FFF2BF, false);
            context.drawString(textRenderer, minigameNoticeText, noticeX, y,
                    noticeAlpha << 24 | (MINIGAME_TEXT_SOFT & 0x00FFFFFF), false);
        }

        int trackWidth = Math.max(titleWidth, Math.min(titleWidth + 8 + noticeWidth, 174));
        int trackAlpha = hasNotice ? (int) ((0.35f + 0.25f * pulse) * 255) : 0x33;
        context.fill(x, y + textRenderer.lineHeight + 1, x + trackWidth, y + textRenderer.lineHeight + 2,
                (trackAlpha << 24) | (MINIGAME_TRACK & 0x00FFFFFF));
    }

    private static void renderSabotageTaskHud(Font textRenderer, FakeGuiGraphics context,
            SREPlayerMinigameTaskComponent minigameTask, int lineY) {
        var sabotageMg = minigameTask.getSabotageMinigame();
        Component minigameName = sabotageMg != null ? sabotageMg.displayName() : Component.empty();
        Component title = Component.translatable("hud.sre.sabotage_task_specific", minigameName);
        int x = 22;
        int y = lineY;
        int color = 0xFF000000 | (SABOTAGE_RED & 0x00FFFFFF);
        context.drawString(textRenderer, title, x, y, color, false);
        int trackWidth = Math.min(Math.max(textRenderer.width(title), 64), 174);
        context.fill(x, y + textRenderer.lineHeight + 1, x + trackWidth, y + textRenderer.lineHeight + 2,
                0x88FF4A4A);
    }

}
