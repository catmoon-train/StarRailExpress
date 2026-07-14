package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.util.SRENetworkMessageUtils;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 末日日报：每个游戏日清晨出一期（{@link SixtySecondsManager#startDay} 触发 {@link #publish}），
 * 从 {@link #NEWS_COUNT} 条剧情新闻池随机选 {@link #NEWS_PER_DAY} 条组成当日刊
 * （整局不重复，抽完重置），聊天栏向全员发可点击提醒；点击（{@code /sre:60s newspaper}）经
 * {@link SRENetworkMessageUtils#sendNewspaper} 打开报纸界面，显示当日刊 + 最近几期的往期头条。
 */
public final class SixtySecondsNewspaper {
    private static final String LANG = "message.noellesroles.sixty_seconds.news.";
    /** 剧情新闻池条数（lang 键 news.n1 .. n{NEWS_COUNT}）。 */
    public static final int NEWS_COUNT = 20;
    /** 每期条数。 */
    public static final int NEWS_PER_DAY = 3;
    /** 「往期头条」最多回看的期数。 */
    private static final int HISTORY_SHOWN = 3;

    /** 一期报纸：游戏日 + 选中的新闻编号。 */
    private record Paper(int day, List<Integer> news) {
    }

    private static final class LevelState {
        final List<Paper> papers = new ArrayList<>();
        /** 本局已用过的新闻编号（避免重复；抽完重置）。 */
        final java.util.Set<Integer> used = new java.util.HashSet<>();
    }

    private static final Map<ServerLevel, LevelState> STATE = new WeakHashMap<>();

    private SixtySecondsNewspaper() {
    }

    public static void reset(ServerLevel level) {
        STATE.remove(level);
    }

    /** 出当日刊并向全员发聊天栏可点击提醒（换日时由 Manager 调用）。 */
    public static void publish(ServerLevel level, SixtySecondsState.Data data) {
        LevelState st = STATE.computeIfAbsent(level, ignored -> new LevelState());
        // 同一天重复触发（管理指令跳日）只出一期
        if (!st.papers.isEmpty() && st.papers.get(st.papers.size() - 1).day == data.dayNumber) {
            return;
        }
        List<Integer> picked = new ArrayList<>();
        for (int i = 0; i < NEWS_PER_DAY; i++) {
            if (st.used.size() >= NEWS_COUNT) {
                st.used.clear(); // 池抽干：重置（7天×3=21 > 20，末期会遇到）
            }
            int n;
            do {
                n = 1 + level.getRandom().nextInt(NEWS_COUNT);
            } while (st.used.contains(n) || picked.contains(n));
            st.used.add(n);
            picked.add(n);
        }
        st.papers.add(new Paper(data.dayNumber, picked));

        Component reminder = Component.literal("【")
                .append(Component.translatable(LANG + "reminder", data.dayNumber))
                .append(Component.literal("】"))
                .withStyle(style -> style.withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sre:60s newspaper"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable(LANG + "reminder_hint"))));
        for (ServerPlayer player : level.players()) {
            player.displayClientMessage(reminder, false);
            player.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.AMBIENT, 0.7F, 1.0F);
        }
    }

    /** {@code /sre:60s newspaper}：打开报纸界面——当日刊全文 + 最近几期往期头条。 */
    public static void open(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        LevelState st = STATE.get(level);
        if (st == null || st.papers.isEmpty()) {
            player.displayClientMessage(Component.translatable(LANG + "none")
                    .withStyle(ChatFormatting.GRAY), false);
            return;
        }
        Paper today = st.papers.get(st.papers.size() - 1);
        List<Component> lines = new ArrayList<>();
        for (int n : today.news) {
            lines.add(Component.literal("◆ ").append(Component.translatable(LANG + "n" + n)));
            lines.add(Component.empty());
        }
        // 往期头条（每期第一条，倒序最近几期）
        if (st.papers.size() > 1) {
            lines.add(Component.translatable(LANG + "history_header").withStyle(ChatFormatting.DARK_GRAY));
            for (int i = st.papers.size() - 2; i >= 0 && i >= st.papers.size() - 1 - HISTORY_SHOWN; i--) {
                Paper past = st.papers.get(i);
                lines.add(Component.translatable(LANG + "history_line", past.day,
                        Component.translatable(LANG + "n" + past.news.get(0)))
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        SRENetworkMessageUtils.sendNewspaper(player, lines,
                java.util.Optional.of(Component.translatable(LANG + "title", today.day)),
                java.util.Optional.of(Component.translatable(LANG + "author")));
    }
}
