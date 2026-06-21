package io.wifi.starrailexpress.api.replay;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

public final class ReplayFormatter {
    private static final int DEFAULT_SCREEN_MAX_LINES = 18;

    private final GameReplayManager manager;

    public ReplayFormatter(GameReplayManager manager) {
        this.manager = manager;
    }

    public Component formatChat(GameReplayData replayData, boolean includeHidden) {
        return manager.generateReplayFromData(replayData, includeHidden);
    }

    public Component formatScreen(GameReplayData replayData, List<ReplayTimelineEvent> events, int maxLines) {
        MutableComponent text = Component.empty();
        for (Component line : formatScreenLines(replayData, events, maxLines)) {
            text.append(line).append("\n");
        }
        return text;
    }

    public List<Component> formatScreenLines(GameReplayData replayData, List<ReplayTimelineEvent> events, int maxLines) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("sre.replay.header").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (replayData.getWinningTeam() != null) {
            lines.add(Component.translatable("sre.replay.winning_team", replayData.getWinningTitle())
                    .withStyle(ChatFormatting.WHITE));
        }
        lines.add(Component.literal("━━━━━━━━━━━━━━━").withStyle(ChatFormatting.DARK_GRAY));

        int limit = maxLines <= 0 ? DEFAULT_SCREEN_MAX_LINES : maxLines;
        int shown = 0;
        int hiddenByLimit = 0;
        for (ReplayTimelineEvent event : events) {
            if (event.hidden()) {
                continue;
            }
            // 回放屏幕不展示商店购买（Store Buy）事件
            if (event.type() == ReplayEventTypes.EventType.STORE_BUY) {
                continue;
            }
            if (shown >= limit) {
                hiddenByLimit++;
                continue;
            }
            lines.add(Component.literal(ReplayDisplayUtils.formatTime(event.relativeTimestamp())).withStyle(ChatFormatting.GRAY)
                    .append(" ")
                    .append(event.text()));
            shown++;
        }
        if (hiddenByLimit > 0) {
            lines.add(Component.literal("... +" + hiddenByLimit).withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

}
