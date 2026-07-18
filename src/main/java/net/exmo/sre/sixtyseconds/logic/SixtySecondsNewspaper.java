package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.util.SRENetworkMessageUtils;
import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
    public static final int NEWS_COUNT = 100;
    public static final int NEWS_PER_DAY = 3;
    private static final int HISTORY_SHOWN = 3;

    /** 邮箱方块注册表：teamId → [pos1, pos2, ...] */
    private static final Map<ServerLevel, Map<Integer, List<BlockPos>>> MAILBOX_REGISTRY = new WeakHashMap<>();
    /** 当天被溢出清理过物品的队伍（早上通知后清除） */
    private static final Set<Integer> CLEARED_TEAMS = new java.util.HashSet<>();

    private static final String[] TIPS = {
            "tip_umbrella", "tip_gasmask", "tip_cold", "tip_door",
            "tip_water", "tip_planter", "tip_stove", "tip_sleep",
            "tip_search", "tip_radio", "tip_flashlight", "tip_barricade",
            "tip_scrap", "tip_shower",
            // 第二批贴士（仅描述代码中实际存在的系统）
            "tip_antibiotics", "tip_purification", "tip_compost",
            "tip_bandage", "tip_sanity", "tip_painkillers",
            "tip_generator", "tip_crafting", "tip_spike_trap",
            "tip_teamwork", "tip_monster", "tip_trade",
            "tip_tech", "tip_planter_fertilizer", "tip_airdrop"
    };

    /** 邻里八卦池（每期随机一条） */
    private static final String[] GOSSIP = {
            "gossip_1", "gossip_2", "gossip_3", "gossip_4",
            "gossip_5", "gossip_6", "gossip_7", "gossip_8",
            "gossip_9", "gossip_10", "gossip_11", "gossip_12"
    };

    private record Paper(int day, List<Integer> news) {}

    private static final class LevelState {
        final List<Paper> papers = new ArrayList<>();
        final java.util.Set<Integer> used = new java.util.HashSet<>();
        final Map<Integer, List<String>> drafts = new HashMap<>();
    }

    private static final Map<ServerLevel, LevelState> STATE = new ConcurrentHashMap<>();

    private static final Map<Integer, ItemStack> NEWS_ATTACHMENTS = new HashMap<>();
    static {
        // 原有附件
        NEWS_ATTACHMENTS.put(1, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_MRE, 1));
        NEWS_ATTACHMENTS.put(7, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_MEDICINE, 1));
        NEWS_ATTACHMENTS.put(8, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_RADIO, 1));
        NEWS_ATTACHMENTS.put(16, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_SEEDS_PACK, 1));
        NEWS_ATTACHMENTS.put(18, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_RADIO, 1));
        // 第二批附件：生存/资源类新闻附带物资
        NEWS_ATTACHMENTS.put(22, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_SCRAP, 2));
        NEWS_ATTACHMENTS.put(23, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_CANNED_FOOD, 1));
        NEWS_ATTACHMENTS.put(24, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_WIRE, 1));
        NEWS_ATTACHMENTS.put(25, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_SCRAP, 1));
        NEWS_ATTACHMENTS.put(27, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_CANNED_FOOD, 2));
        NEWS_ATTACHMENTS.put(30, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_SEEDS_PACK, 1));
        NEWS_ATTACHMENTS.put(32, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_MEDICINE, 1));
        NEWS_ATTACHMENTS.put(33, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_PLASTIC, 2));
        NEWS_ATTACHMENTS.put(34, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_CANNED_SOUP, 1));
        NEWS_ATTACHMENTS.put(38, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_MEDICINE, 1));
        NEWS_ATTACHMENTS.put(39, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_PURIFICATION_TABLET, 1));
        NEWS_ATTACHMENTS.put(40, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_ELECTRONICS, 1));
        // 社会/人性类新闻附带物资
        NEWS_ATTACHMENTS.put(49, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_DRAFT_PAPER, 1));
        NEWS_ATTACHMENTS.put(52, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_COIN, 3));
        NEWS_ATTACHMENTS.put(58, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_RADIO, 1));
        NEWS_ATTACHMENTS.put(60, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_SEEDS_PACK, 1));
        // 希望/奇闻类新闻附带物资
        NEWS_ATTACHMENTS.put(82, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_ELECTRONICS, 1));
        NEWS_ATTACHMENTS.put(85, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_CANNED_FOOD, 2));
        NEWS_ATTACHMENTS.put(89, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_CANNED_SOUP, 2));
        NEWS_ATTACHMENTS.put(90, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_ANTIBIOTICS, 1));
        NEWS_ATTACHMENTS.put(94, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_HARMONICA, 1));
        NEWS_ATTACHMENTS.put(99, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_SEEDS_PACK, 1));
        NEWS_ATTACHMENTS.put(100, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_RADIO, 1));
    }

    /**
     * 末日日报物资发放：仅当天被选中的特定新闻 ID 会附带物资。
     * 附件通过 NEWS_ATTACHMENTS 映射定义（放入 deliverToTeamMailbox 处理），
     * 不是每条新闻都发物资，也不是根据天气事件发放。。
     */

    private SixtySecondsNewspaper() {}

    // ── 邮箱注册 ──
    public static void registerMailbox(ServerLevel level, int teamId, BlockPos pos) {
        MAILBOX_REGISTRY.computeIfAbsent(level, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(teamId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(pos);
    }

    /**
     * 队伍 → 邮箱坐标。一个邮箱都还没注册时返回<b>空表而不是 null</b>——
     * 开局第一天清晨 {@link SixtySecondsHotlineSystem#processDeliveries} 就会遍历它。
     * 只读；登记邮箱走 {@link #registerMailbox}。
     */
    public static Map<Integer, List<BlockPos>> getMailboxRegistry(ServerLevel level) {
        return MAILBOX_REGISTRY.getOrDefault(level, Map.of());
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
        CLEARED_TEAMS.clear();
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
        doPublish(level, data, st);
    }

    /** 强制发布（跳过当日已发布的守卫，用于测试指令） */
    public static void forcePublish(ServerLevel level, SixtySecondsState.Data data) {
        LevelState st = STATE.computeIfAbsent(level, ignored -> new LevelState());
        doPublish(level, data, st);
    }

    private static void doPublish(ServerLevel level, SixtySecondsState.Data data, LevelState st) {

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

        // 通知邮箱溢出被清物品的队伍
        for (int teamId : CLEARED_TEAMS) {
            for (ServerPlayer p : level.players()) {
                if (!GameUtils.isPlayerEliminated(p)
                        && SixtySecondsStatsComponent.KEY.get(p).teamId == teamId) {
                    p.displayClientMessage(Component.translatable(
                            "message.noellesroles.sixty_seconds.mailbox_tampered"), false);
                }
            }
        }
        CLEARED_TEAMS.clear();
    }

    private static List<Component> buildPaperContent(ServerLevel level, SixtySecondsState.Data data,
            int teamId, SixtySecondsState.TeamData team, List<Integer> picked) {
        List<Component> sections = new ArrayList<>();

        // 1. 末日日报新闻
        var headline = new StringBuilder();
        headline.append("≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡\n");
        for (int n : picked) {
            headline.append("◆ ");
            headline.append(Component.translatable(LANG + "n" + n).getString());
            headline.append("\n\n");
        }
        headline.append("≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡");
        sections.add(Component.literal(headline.toString()).withStyle(ChatFormatting.DARK_RED));

        // 2. 天气播报
        StringBuilder weather = new StringBuilder();
        weather.append(Component.translatable(LANG + "section_weather").getString()).append("\n\n");
        String weatherKey = SixtySecondsEventSystem.activeEventKey(level);
        if (weatherKey != null) {
            weather.append(Component.translatable("message.noellesroles.sixty_seconds.weather_active").getString());
            weather.append(Component.translatable(weatherKey).getString());
        } else {
            weather.append(Component.translatable("message.noellesroles.sixty_seconds.weather_clear").getString());
        }
        sections.add(Component.literal(weather.toString()).withStyle(ChatFormatting.GOLD));

        // 3. 全区动态
        StringBuilder zone = new StringBuilder();
        zone.append(Component.translatable(LANG + "section_zone").getString()).append("\n\n");
        long aliveCount = level.players().stream()
                .filter(p -> !GameUtils.isPlayerEliminated(p)).count();
        long totalPlayers = data.teams.values().stream().mapToLong(t -> t.members.size()).sum();
        long deceased = totalPlayers - aliveCount;

        // 根据天数动态变化全区动态描述
        String zoneKey;
        if (data.dayNumber <= 5) {
            zoneKey = "zone_order_stable";
        } else if (data.dayNumber <= 10) {
            zoneKey = "zone_security_decay";
        } else if (data.dayNumber <= 20) {
            zoneKey = "zone_no_mans_land";
        } else {
            zoneKey = "zone_ecosystem_rebuild";
        }

        // 检查是否有进行中的事件覆盖
        String activeWeatherKey = SixtySecondsEventSystem.activeEventKey(level);
        if (activeWeatherKey != null) {
            zoneKey = "zone_event_active";
        }

        if (deceased > 10) {
            zone.append(Component.translatable("message.noellesroles.sixty_seconds.zone_mass_casualty").getString());
        } else if (deceased > 0) {
            zone.append(Component.translatable("message.noellesroles.sixty_seconds.zone_deaths", Math.max(0, deceased)).getString());
        } else {
            if (data.dayNumber >= 5 && deceased == 0) {
                // 已过第5天但无人死亡，添加紧张氛围描述
            }
            zone.append(Component.translatable("message.noellesroles.sixty_seconds.zone_peaceful").getString());
        }
        zone.append("\n");
        zone.append(Component.translatable("message.noellesroles.sixty_seconds.zone_alive", data.teams.size()).getString());
        zone.append("\n");
        zone.append(Component.translatable("message.noellesroles.sixty_seconds." + zoneKey).getString());
        sections.add(Component.literal(zone.toString()).withStyle(ChatFormatting.DARK_AQUA));

        // 4. 生存小贴士
        String tipKey = TIPS[level.getRandom().nextInt(TIPS.length)];
        StringBuilder tips = new StringBuilder();
        tips.append(Component.translatable(LANG + "section_tips").getString()).append("\n\n");
        tips.append(Component.translatable("message.noellesroles.sixty_seconds." + tipKey).getString());
        sections.add(Component.literal(tips.toString()).withStyle(ChatFormatting.GREEN));

        // 5. PVP紧急播报
        if (data.dayNumber >= 5) {
            StringBuilder pvp = new StringBuilder();
            pvp.append(Component.translatable(LANG + "section_emergency").getString()).append("\n\n");
            pvp.append(Component.translatable("message.noellesroles.sixty_seconds.pvp_broadcast_"
                    + (data.dayNumber == 5 ? "day5" : "ongoing")).getString());
            sections.add(Component.literal(pvp.toString()).withStyle(ChatFormatting.RED));
        }

        // 6. 特殊通报（50%概率）
        if (level.getRandom().nextDouble() < 0.5) {
            StringBuilder report = new StringBuilder();
            report.append(Component.translatable(LANG + "section_report").getString()).append("\n\n");
            int reportType = level.getRandom().nextInt(3);
            switch (reportType) {
                case 0 -> {
                    int richest = -1, maxCoins = 0;
                    for (Map.Entry<Integer, SixtySecondsState.TeamData> te : data.teams.entrySet()) {
                        int coins = countTeamMailboxCoins(level, te.getKey());
                        if (coins > maxCoins) { maxCoins = coins; richest = te.getKey(); }
                    }
                    report.append(Component.translatable("message.noellesroles.sixty_seconds.report_richest", richest, maxCoins).getString());
                }
                case 1 -> {
                    int topKiller = -1, maxKills = 0;
                    for (Map.Entry<Integer, SixtySecondsState.TeamData> te : data.teams.entrySet()) {
                        int kills = 0;
                        for (UUID mid : te.getValue().members) {
                            ServerPlayer mp = level.getServer().getPlayerList().getPlayer(mid);
                            if (mp != null) { kills += SixtySecondsStatsComponent.KEY.get(mp).playerKills; }
                        }
                        if (kills > maxKills) { maxKills = kills; topKiller = te.getKey(); }
                    }
                    report.append(Component.translatable("message.noellesroles.sixty_seconds.report_kills", topKiller, maxKills).getString());
                }
                case 2 -> {
                    List<Integer> alive = new ArrayList<>();
                    for (Map.Entry<Integer, SixtySecondsState.TeamData> te : data.teams.entrySet()) {
                        long online = level.players().stream()
                            .filter(p -> SixtySecondsStatsComponent.KEY.get(p).teamId == te.getKey() && !GameUtils.isPlayerEliminated(p)).count();
                        if (online > 0) alive.add(te.getKey());
                    }
                    String teams = alive.stream().map(String::valueOf).collect(Collectors.joining(" "));
                    report.append(Component.translatable("message.noellesroles.sixty_seconds.report_survivors", alive.size(), teams).getString());
                }
            }
            sections.add(Component.literal(report.toString()).withStyle(ChatFormatting.LIGHT_PURPLE));
        }

        // 7. 幸存者投稿
        LevelState st = STATE.get(level);
        if (st != null && !st.drafts.isEmpty()) {
            List<String> allDrafts = st.drafts.values().stream().flatMap(List::stream).collect(Collectors.toList());
            if (!allDrafts.isEmpty()) {
                StringBuilder drafts = new StringBuilder();
                drafts.append(Component.translatable(LANG + "section_drafts").getString()).append("\n\n");
                int idx = 1;
                for (String draft : allDrafts) {
                    String preview = draft.length() > 40 ? draft.substring(0, 40) + "…" : draft;
                    drafts.append(Component.translatable(LANG + "draft_entry", idx, preview).getString()).append("\n");
                    idx++;
                }
                sections.add(Component.literal(drafts.toString()).withStyle(ChatFormatting.YELLOW));
            }
        }

        // 8. 市民热线栏目（60%概率出现，最多2条）
        List<SixtySecondsHotlineSystem.HotlineEntry> hotlines = SixtySecondsHotlineSystem.getDailyHotlines(level);
        if (!hotlines.isEmpty() && level.getRandom().nextDouble() < 0.6) {
            int count = Math.min(hotlines.size(), 1 + level.getRandom().nextInt(2));
            StringBuilder hotlineSb = new StringBuilder();
            hotlineSb.append(Component.translatable("message.noellesroles.sixty_seconds.news.hotline_header").getString()).append("\n\n");
            for (int i = 0; i < count; i++) {
                SixtySecondsHotlineSystem.HotlineEntry entry = hotlines.get(i);
                String typeKey = switch (entry.type()) {
                    case EXPRESS -> "message.noellesroles.sixty_seconds.hotline_type_express";
                    case SHOP -> "message.noellesroles.sixty_seconds.hotline_type_shop";
                    case RESCUE -> "message.noellesroles.sixty_seconds.hotline_type_rescue";
                    case INTEL -> "message.noellesroles.sixty_seconds.hotline_type_intel";
                    case WEATHER -> "message.noellesroles.sixty_seconds.hotline_type_weather";
                    case COUNSEL -> "message.noellesroles.sixty_seconds.hotline_type_counsel";
                    case HIRE -> "message.noellesroles.sixty_seconds.hotline_type_hire";
                    case BLACK_MARKET -> "message.noellesroles.sixty_seconds.hotline_type_black_market";
                    case RECYCLE -> "message.noellesroles.sixty_seconds.hotline_type_recycle";
                    case POVERTY_RELIEF -> "message.noellesroles.sixty_seconds.hotline_type_poverty_relief";
                    case REPORT -> "message.noellesroles.sixty_seconds.hotline_type_report";
                };
                hotlineSb.append(Component.translatable("message.noellesroles.sixty_seconds.news.hotline_entry",
                        Component.translatable(typeKey), entry.number()).getString()).append("\n");
            }
            sections.add(Component.literal(hotlineSb.toString()).withStyle(ChatFormatting.AQUA));
        }

        // 9. 邻里八卦（70%概率出现，随机一条）
        if (level.getRandom().nextDouble() < 0.7) {
            String gossipKey = GOSSIP[level.getRandom().nextInt(GOSSIP.length)];
            StringBuilder gossip = new StringBuilder();
            gossip.append(Component.translatable(LANG + "section_gossip").getString()).append("\n\n");
            gossip.append(Component.translatable("message.noellesroles.sixty_seconds." + gossipKey).getString());
            sections.add(Component.literal(gossip.toString()).withStyle(ChatFormatting.LIGHT_PURPLE));
        }

        // 尾页：分隔线 + 日期
        StringBuilder footer = new StringBuilder();
        footer.append(Component.translatable(LANG + "section_divider").getString()).append("\n");
        footer.append(Component.translatable(LANG + "footer", data.dayNumber).getString());
        sections.add(Component.literal(footer.toString()).withStyle(ChatFormatting.DARK_GRAY));

        return sections;
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

            // 先清除旧报纸和稿纸
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

            // 如果满了（附件填满），清除非报纸的普通物品腾空间
            if (slot >= mb.getContainerSize()) {
                boolean clearedAny = false;
                for (int i = 1; i < mb.getContainerSize(); i++) {
                    ItemStack s = mb.getItem(i);
                    if (!s.isEmpty() && !s.is(org.agmas.noellesroles.init.ModItems.NEWSPAPER)
                            && !s.is(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_COIN)
                            && !s.is(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_EXPRESS_PACKAGE)
                            && !s.is(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_SCRAP)) {
                        mb.setItem(i, ItemStack.EMPTY);
                        clearedAny = true;
                    }
                }
                if (clearedAny) {
                    CLEARED_TEAMS.add(teamId);
                }
            }
            mb.setChanged();
        }
    }

    private static int countTeamMailboxCoins(ServerLevel level, int teamId) {
        Map<Integer, List<BlockPos>> map = MAILBOX_REGISTRY.get(level);
        if (map == null) return 0;
        List<BlockPos> boxes = map.get(teamId);
        if (boxes == null) return 0;
        int total = 0;
        for (BlockPos pos : boxes) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof net.exmo.sre.sixtyseconds.content.block_entity.SixtySecondsMailboxBlockEntity mb) {
                for (int i = 0; i < mb.getContainerSize(); i++) {
                    ItemStack s = mb.getItem(i);
                    if (s.is(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_COIN)) total += s.getCount();
                }
            }
        }
        return total;
    }

    /** 生成一份模拟报纸（不依赖游戏状态，直接打开给玩家看） */
    public static void openMock(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        var rand = level.getRandom();

        // 随机选取 3 条新闻 ID
        List<Integer> picked = new ArrayList<>();
        for (int i = 0; i < NEWS_PER_DAY; i++) {
            int n = 1 + rand.nextInt(NEWS_COUNT);
            while (picked.contains(n)) n = 1 + rand.nextInt(NEWS_COUNT);
            picked.add(n);
        }

        List<Component> sections = new ArrayList<>();

        // 1. 新闻
        var headline = new StringBuilder();
        headline.append("≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡\n");
        for (int n : picked) {
            headline.append("◆ ");
            headline.append(Component.translatable(LANG + "n" + n).getString());
            headline.append("\n\n");
        }
        headline.append("≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡");
        sections.add(Component.literal(headline.toString()).withStyle(ChatFormatting.DARK_RED));

        // 2. 天气播报
        StringBuilder weather = new StringBuilder();
        weather.append(Component.translatable(LANG + "section_weather").getString()).append("\n\n");
        String weatherKey = SixtySecondsEventSystem.activeEventKey(level);
        if (weatherKey != null) {
            weather.append(Component.translatable("message.noellesroles.sixty_seconds.weather_active").getString());
            weather.append(Component.translatable(weatherKey).getString());
        } else {
            weather.append(Component.translatable("message.noellesroles.sixty_seconds.weather_clear").getString());
        }
        sections.add(Component.literal(weather.toString()).withStyle(ChatFormatting.GOLD));

        // 3. 全区动态
        StringBuilder zone = new StringBuilder();
        zone.append(Component.translatable(LANG + "section_zone").getString()).append("\n\n");
        zone.append(Component.translatable("message.noellesroles.sixty_seconds.zone_peaceful").getString());
        zone.append("\n");
        zone.append(Component.translatable("message.noellesroles.sixty_seconds.zone_alive", 4).getString());
        zone.append("\n");
        zone.append(Component.translatable("message.noellesroles.sixty_seconds.zone_order_stable").getString());
        sections.add(Component.literal(zone.toString()).withStyle(ChatFormatting.DARK_AQUA));

        // 4. 生存小贴士
        String tipKey = TIPS[rand.nextInt(TIPS.length)];
        StringBuilder tips = new StringBuilder();
        tips.append(Component.translatable(LANG + "section_tips").getString()).append("\n\n");
        tips.append(Component.translatable("message.noellesroles.sixty_seconds." + tipKey).getString());
        sections.add(Component.literal(tips.toString()).withStyle(ChatFormatting.GREEN));

        // 5. PVP紧急播报（模拟固定出现）
        StringBuilder pvp = new StringBuilder();
        pvp.append(Component.translatable(LANG + "section_emergency").getString()).append("\n\n");
        pvp.append(Component.translatable("message.noellesroles.sixty_seconds.pvp_broadcast_ongoing").getString());
        sections.add(Component.literal(pvp.toString()).withStyle(ChatFormatting.RED));

        // 6. 特殊通报（随机3种之一）
        StringBuilder report = new StringBuilder();
        report.append(Component.translatable(LANG + "section_report").getString()).append("\n\n");
        int reportType = rand.nextInt(3);
        switch (reportType) {
            case 0 -> report.append(Component.translatable("message.noellesroles.sixty_seconds.report_richest", 2, 42).getString());
            case 1 -> report.append(Component.translatable("message.noellesroles.sixty_seconds.report_kills", 3, 5).getString());
            case 2 -> report.append(Component.translatable("message.noellesroles.sixty_seconds.report_survivors", 4, "1 2 3 4").getString());
        }
        sections.add(Component.literal(report.toString()).withStyle(ChatFormatting.LIGHT_PURPLE));

        // 7. 幸存者投稿（模拟一条）
        StringBuilder drafts = new StringBuilder();
        drafts.append(Component.translatable(LANG + "section_drafts").getString()).append("\n\n");
        drafts.append(Component.translatable(LANG + "draft_entry", 1,
                Component.translatable("message.noellesroles.sixty_seconds.gossip_8").getString()).getString());
        sections.add(Component.literal(drafts.toString()).withStyle(ChatFormatting.YELLOW));

        // 8. 邻里八卦
        String gossipKey = GOSSIP[rand.nextInt(GOSSIP.length)];
        StringBuilder gossip = new StringBuilder();
        gossip.append(Component.translatable(LANG + "section_gossip").getString()).append("\n\n");
        gossip.append(Component.translatable("message.noellesroles.sixty_seconds." + gossipKey).getString());
        sections.add(Component.literal(gossip.toString()).withStyle(ChatFormatting.LIGHT_PURPLE));

        // 9. 尾页
        StringBuilder footer = new StringBuilder();
        footer.append(Component.translatable(LANG + "section_divider").getString()).append("\n");
        footer.append(Component.translatable(LANG + "footer", 7).getString());
        sections.add(Component.literal(footer.toString()).withStyle(ChatFormatting.DARK_GRAY));

        SRENetworkMessageUtils.sendNewspaper(player, sections,
                java.util.Optional.of(Component.translatable(LANG + "title", 7)),
                java.util.Optional.of(Component.translatable(LANG + "author")));
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
        List<Component> sections = buildPaperContent(level, data, teamId, team, today.news);

        // 往期头条：合并为一个 Component
        if (st.papers.size() > 1) {
            StringBuilder history = new StringBuilder();
            history.append(Component.translatable(LANG + "history_header").getString()).append("\n");
            for (int i = st.papers.size() - 2; i >= 0 && i >= st.papers.size() - 1 - HISTORY_SHOWN; i--) {
                Paper past = st.papers.get(i);
                history.append(Component.translatable(LANG + "history_line", past.day,
                        Component.translatable(LANG + "n" + past.news.get(0))).getString()).append("\n");
            }
            sections.add(Component.literal(history.toString()).withStyle(ChatFormatting.DARK_GRAY));
        }
        SRENetworkMessageUtils.sendNewspaper(player, sections,
                java.util.Optional.of(Component.translatable(LANG + "title", today.day)),
                java.util.Optional.of(Component.translatable(LANG + "author")));
    }
}
