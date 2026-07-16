package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.util.SRENetworkMessageUtils;
import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 末日日报：每 GameDay 清晨出一期报纸，内容包含末日新闻、天气播报、
 * 全区动态、生存小贴士、PVP 播报、特殊通报、幸存者投稿等。
 * 同时向每队的邮箱方块投递报纸 + 附件物品。
 */
public final class SixtySecondsNewspaper {
    private static final String LANG = "message.noellesroles.sixty_seconds.news.";
    public static final int NEWS_COUNT = 20;
    public static final int NEWS_PER_DAY = 3;
    private static final int HISTORY_SHOWN = 3;

    /** 邮箱方块注册表：teamId → [pos1, pos2, ...] */
    private static final Map<ServerLevel, Map<Integer, List<BlockPos>>> MAILBOX_REGISTRY = new WeakHashMap<>();

    private static final String[] TIPS = {
            "tip_umbrella", "tip_gasmask", "tip_cold", "tip_door",
            "tip_water", "tip_planter", "tip_stove", "tip_sleep",
            "tip_search", "tip_radio", "tip_flashlight", "tip_barricade",
            "tip_scrap", "tip_shower"
    };

    private record Paper(int day, List<Integer> news) {}

    private static final class LevelState {
        final List<Paper> papers = new ArrayList<>();
        final java.util.Set<Integer> used = new java.util.HashSet<>();
        final Map<Integer, List<String>> drafts = new HashMap<>();
    }

    private static final Map<ServerLevel, LevelState> STATE = new WeakHashMap<>();

    private static final Map<Integer, ItemStack> NEWS_ATTACHMENTS = new HashMap<>();
    static {
        NEWS_ATTACHMENTS.put(1, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_MRE, 1));
        NEWS_ATTACHMENTS.put(7, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_MEDICINE, 1));
        NEWS_ATTACHMENTS.put(16, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_SEEDS_PACK, 1));
        NEWS_ATTACHMENTS.put(18, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_RADIO, 1));
    }

    private SixtySecondsNewspaper() {}

    // ── 邮箱注册 ──
    public static void registerMailbox(ServerLevel level, int teamId, BlockPos pos) {
        MAILBOX_REGISTRY.computeIfAbsent(level, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(teamId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(pos);
    }

    public static void unregisterMailbox(ServerLevel level, BlockPos pos) {
        Map<Integer, List<BlockPos>> map = MAILBOX_REGISTRY.get(level);
        if (map == null) return;
        for (List<BlockPos> list : map.values()) {
            list.remove(pos);
        }
    }

    public static void reset(ServerLevel level) {
        STATE.remove(level);
        MAILBOX_REGISTRY.remove(level);
    }

    /** 收集稿纸投稿内容（每天清晨由 Manager 调用预处理） */
    public static void collectDrafts(ServerLevel level, SixtySecondsState.Data data) {
        LevelState st = STATE.computeIfAbsent(level, ignored -> new LevelState());
        st.drafts.clear();
        Map<Integer, List<BlockPos>> teamMailboxes = MAILBOX_REGISTRY.get(level);
        if (teamMailboxes == null) return;

        for (Map.Entry<Integer, List<BlockPos>> entry : teamMailboxes.entrySet()) {
            int teamId = entry.getKey();
            for (BlockPos pos : entry.getValue()) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof net.exmo.sre.sixtyseconds.content.block_entity.SixtySecondsMailboxBlockEntity mb) {
                    for (int i = 0; i < mb.getContainerSize(); i++) {
                        ItemStack stack = mb.getItem(i);
                        if (stack.is(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_DRAFT_PAPER)) {
                            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
                            if (customData != null) {
                                String text = customData.copyTag().getString("DraftText");
                                if (!text.isEmpty()) {
                                    st.drafts.computeIfAbsent(teamId, k -> new ArrayList<>()).add(text);
                                }
                            }
                            mb.setItem(i, ItemStack.EMPTY);
                        }
                    }
                }
            }
        }
    }

    /** 出当日刊并投递到各队邮箱 */
    public static void publish(ServerLevel level, SixtySecondsState.Data data) {
        LevelState st = STATE.computeIfAbsent(level, ignored -> new LevelState());
        if (!st.papers.isEmpty() && st.papers.get(st.papers.size() - 1).day == data.dayNumber) return;

        List<Integer> picked = new ArrayList<>();
        for (int i = 0; i < NEWS_PER_DAY; i++) {
            if (st.used.size() >= NEWS_COUNT) st.used.clear();
            int n;
            do { n = 1 + level.getRandom().nextInt(NEWS_COUNT); }
            while (st.used.contains(n) || picked.contains(n));
            st.used.add(n);
            picked.add(n);
        }
        st.papers.add(new Paper(data.dayNumber, picked));

        for (Map.Entry<Integer, SixtySecondsState.TeamData> entry : data.teams.entrySet()) {
            int teamId = entry.getKey();
            SixtySecondsState.TeamData team = entry.getValue();
            List<Component> content = buildPaperContent(level, data, teamId, team, picked);
            deliverToTeamMailbox(level, teamId, content, picked);
        }

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

    private static List<Component> buildPaperContent(ServerLevel level, SixtySecondsState.Data data,
            int teamId, SixtySecondsState.TeamData team, List<Integer> picked) {
        List<Component> lines = new ArrayList<>();

        // 1. 末日日报新闻
        lines.add(Component.literal("≡≡ 末日日报 ≡≡").withStyle(ChatFormatting.DARK_RED));
        lines.add(Component.empty());
        for (int n : picked) {
            lines.add(Component.literal("◆ ").append(Component.translatable(LANG + "n" + n)));
            lines.add(Component.empty());
        }

        // 2. 天气播报
        lines.add(Component.literal("━━ 天气播报 ━━").withStyle(ChatFormatting.GOLD));
        lines.add(Component.empty());
        String weatherKey = SixtySecondsEventSystem.activeEventKey(level);
        if (weatherKey != null) {
            lines.add(Component.translatable("message.noellesroles.sixty_seconds.weather_active")
                    .append(Component.translatable(weatherKey)));
        } else {
            lines.add(Component.translatable("message.noellesroles.sixty_seconds.weather_clear"));
        }
        lines.add(Component.empty());

        // 3. 全区动态
        lines.add(Component.literal("━━ 全区动态 ━━").withStyle(ChatFormatting.DARK_AQUA));
        lines.add(Component.empty());
        // 死亡通报（基于存活玩家数量）
        long aliveCount = level.players().stream()
                .filter(p -> !GameUtils.isPlayerEliminated(p))
                .count();
        long totalPlayers = data.teams.values().stream()
                .mapToLong(t -> t.members.size())
                .sum();
        long deceased = totalPlayers - aliveCount;
        if (deceased > 0) {
            lines.add(Component.translatable("message.noellesroles.sixty_seconds.zone_deaths",
                    Math.max(0, deceased)));
        } else {
            lines.add(Component.translatable("message.noellesroles.sixty_seconds.zone_peaceful"));
        }
        lines.add(Component.translatable("message.noellesroles.sixty_seconds.zone_alive",
                data.teams.size()));
        lines.add(Component.empty());

        // 4. 生存小贴士
        lines.add(Component.literal("━━ 生存小贴士 ━━").withStyle(ChatFormatting.GREEN));
        lines.add(Component.empty());
        String tipKey = TIPS[level.getRandom().nextInt(TIPS.length)];
        lines.add(Component.translatable("message.noellesroles.sixty_seconds." + tipKey));
        lines.add(Component.empty());

        // 5. PVP紧急播报
        if (data.dayNumber >= 5) {
            lines.add(Component.literal("⚠ 紧急播报 ⚠").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            lines.add(Component.empty());
            lines.add(Component.translatable("message.noellesroles.sixty_seconds.pvp_broadcast_"
                    + (data.dayNumber == 5 ? "day5" : "ongoing"))
                    .withStyle(ChatFormatting.RED));
            lines.add(Component.empty());
        }

        // 6. 特殊通报（50%概率）
        if (level.getRandom().nextDouble() < 0.5) {
            lines.add(Component.literal("━━ 特别报道 ━━").withStyle(ChatFormatting.LIGHT_PURPLE));
            lines.add(Component.empty());
            int reportType = level.getRandom().nextInt(3);
            switch (reportType) {
                case 0 -> lines.add(Component.translatable(
                        "message.noellesroles.sixty_seconds.report_richest"));
                case 1 -> lines.add(Component.translatable(
                        "message.noellesroles.sixty_seconds.report_kills"));
                case 2 -> lines.add(Component.translatable(
                        "message.noellesroles.sixty_seconds.report_survivors"));
            }
            lines.add(Component.empty());
        }

        // 7. 幸存者投稿
        LevelState st = STATE.get(level);
        if (st != null && !st.drafts.isEmpty()) {
            List<String> allDrafts = st.drafts.values().stream()
                    .flatMap(List::stream)
                    .collect(Collectors.toList());
            if (!allDrafts.isEmpty()) {
                lines.add(Component.literal("━━ 幸存者投稿 ━━").withStyle(ChatFormatting.YELLOW));
                lines.add(Component.empty());
                int idx = 1;
                for (String draft : allDrafts) {
                    String preview = draft.length() > 40 ? draft.substring(0, 40) + "…" : draft;
                    lines.add(Component.literal("§7[" + idx + "] §f" + preview));
                    idx++;
                }
                lines.add(Component.empty());
            }
        }

        lines.add(Component.literal("━━━━━━━━━━━").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("message.noellesroles.sixty_seconds.news.footer", data.dayNumber)
                .withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    private static void deliverToTeamMailbox(ServerLevel level, int teamId,
            List<Component> paperContent, List<Integer> newsIds) {
        Map<Integer, List<BlockPos>> teamMailboxes = MAILBOX_REGISTRY.get(level);
        if (teamMailboxes == null) return;
        List<BlockPos> mailboxes = teamMailboxes.get(teamId);
        if (mailboxes == null || mailboxes.isEmpty()) return;

        ItemStack newspaper = new ItemStack(org.agmas.noellesroles.init.ModItems.NEWSPAPER, 1);

        for (BlockPos pos : mailboxes) {
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof net.exmo.sre.sixtyseconds.content.block_entity.SixtySecondsMailboxBlockEntity mb)) continue;

            for (int i = 0; i < mb.getContainerSize(); i++) {
                ItemStack stack = mb.getItem(i);
                if (stack.is(org.agmas.noellesroles.init.ModItems.NEWSPAPER)
                        || stack.is(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_DRAFT_PAPER)) {
                    mb.setItem(i, ItemStack.EMPTY);
                }
            }
            mb.setItem(0, newspaper.copy());

            int slot = 1;
            for (int nid : newsIds) {
                ItemStack att = NEWS_ATTACHMENTS.get(nid);
                if (att != null && slot < mb.getContainerSize()) {
                    mb.setItem(slot, att.copy());
                    slot++;
                }
            }
            mb.setChanged();
        }
    }

    /** 打开报纸界面 */
    public static void open(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        LevelState st = STATE.get(level);
        if (st == null || st.papers.isEmpty()) {
            player.displayClientMessage(Component.translatable(LANG + "none")
                    .withStyle(ChatFormatting.GRAY), false);
            return;
        }
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        int teamId = SixtySecondsStatsComponent.KEY.get(player).teamId;
        SixtySecondsState.TeamData team = data.teams.get(teamId);
        Paper today = st.papers.get(st.papers.size() - 1);
        List<Component> lines = buildPaperContent(level, data, teamId, team, today.news);

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
