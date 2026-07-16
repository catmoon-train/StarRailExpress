package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.cca.SREPlayerMinigameTaskComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.init.ModItems;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * 每日事件门系统：每个游戏日清晨（开日 {@link SixtySecondsBalance#DAILY_EVENT_DELAY_TICKS} 后）
 * 为每个家庭独立抽取一条<b>带剧情</b>的随机事件，共 6 类：
 * <ul>
 *   <li><b>不幸</b>（瞬发）：丢失家中容器物资 / 掉理智 / 生病 / 断电 / 门朽坏……</li>
 *   <li><b>机遇</b>（瞬发）：获得物资 / 回理智 / 回口渴 / 空投……</li>
 *   <li><b>抉择</b>：聊天栏可点击二选一（如深夜敲门 / 可疑罐头），队内任一成员点击即生效；
 *       {@link SixtySecondsBalance#DAILY_EVENT_CHOICE_TICKS} 内未决定按保守选项（选项2）自动处理。</li>
 *   <li><b>交易</b>：花金币 / 以物易物换取物资（点击者付出代价）。</li>
 *   <li><b>探险</b>：需背包里有手电筒；点击者出发，延迟结算收获与代价。</li>
 *   <li><b>劫掠</b>：洗劫随机邻队家中容器，收益归己队、全队掉理智，受害队会收到通知。</li>
 * </ul>
 * 丢失物资 = 自动在家（住宅+避难所范围盒）里的所有容器中随机抽走；同队不会连续两天抽到同一事件。
 * 玩家点击选项走 {@code /sre:60s event <token> <option>}（见 SixtySecondsStartCommand）。
 */
public final class SixtySecondsDailyEvents {
    private static final String LANG = "message.noellesroles.sixty_seconds.devent.";

    /** 事件类型：聊天栏标签 + 颜色。 */
    public enum Type {
        MISFORTUNE(ChatFormatting.RED),
        FORTUNE(ChatFormatting.GREEN),
        CHOICE(ChatFormatting.GOLD),
        TRADE(ChatFormatting.AQUA),
        EXPEDITION(ChatFormatting.LIGHT_PURPLE),
        RAID(ChatFormatting.DARK_RED),
        MODIFIER(ChatFormatting.BLUE);

        final ChatFormatting color;

        Type(ChatFormatting color) {
            this.color = color;
        }

        String tagKey() {
            return LANG + "tag." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** 瞬发事件效果。 */
    @FunctionalInterface
    private interface InstantEffect {
        void run(ServerLevel level, SixtySecondsState.TeamData team);
    }

    /**
     * 抉择事件效果：{@code clicker} 为点击者（超时自动结算时为 null，此时 option 固定为 2）。
     * 返回 false = 前置条件不满足（缺钱/缺物/缺手电筒），事件保持待决，可换人再点。
     */
    @FunctionalInterface
    private interface ChoiceEffect {
        boolean choose(ServerLevel level, SixtySecondsState.TeamData team, ServerPlayer clicker, int option);
    }

    private record EventDef(String id, Type type, int weight, InstantEffect instant, ChoiceEffect choice) {
    }

    /** 待决抉择（每队每日至多一条）。 */
    private static final class Pending {
        final int token;
        final String eventId;
        final long expireTick;

        Pending(int token, String eventId, long expireTick) {
            this.token = token;
            this.eventId = eventId;
            this.expireTick = expireTick;
        }
    }

    /** 已出发的探险（延迟结算）。 */
    private static final class Expedition {
        final int teamId;
        final UUID explorer;
        final String eventId;
        final long resolveTick;

        Expedition(int teamId, UUID explorer, String eventId, long resolveTick) {
            this.teamId = teamId;
            this.explorer = explorer;
            this.eventId = eventId;
            this.resolveTick = resolveTick;
        }
    }

    private static final class LevelState {
        long triggerTick = 0L;
        /** teamId → 待决抉择。 */
        final Map<Integer, Pending> pending = new HashMap<>();
        final List<Expedition> expeditions = new ArrayList<>();
        /** teamId → 昨日事件 id（同队不连续两天重复）。 */
        final Map<Integer, String> lastEvent = new HashMap<>();
        /** 政府空投事件的当日全局去重（多队同日抽到只投一次）。 */
        int airdropDay = 0;
        /** teamId → 当日已触发的事件定义（供回家补发）。 */
        final Map<Integer, EventDef> todayEvent = new HashMap<>();
        /** 已看到事件的玩家 UUID（避免重复推送）。 */
        final Set<UUID> seenPlayers = new HashSet<>();
    }

    private static final Map<ServerLevel, LevelState> STATE = new WeakHashMap<>();
    private static final Map<String, EventDef> EVENTS = new LinkedHashMap<>();

    static {
        registerAll();
    }

    private SixtySecondsDailyEvents() {
    }

    // ══════════════════════════ 生命周期（由 SixtySecondsManager 驱动） ══════════════════════════

    public static void reset(ServerLevel level) {
        STATE.remove(level);
    }

    /** 换日（含第 1 天）：清掉隔日残留的待决/探险。事件在傍晚由 Manager 触发。 */
    public static void onDayStart(ServerLevel level) {
        LevelState st = STATE.computeIfAbsent(level, ignored -> new LevelState());
        st.pending.clear();
        st.expeditions.clear();
        st.triggerTick = 0L; // 不再清晨触发，等傍晚
    }

    public static void tick(ServerLevel level) {
        LevelState st = STATE.get(level);
        if (st == null) {
            return;
        }
        long now = level.getGameTime();
        if (st.triggerTick != 0L && now >= st.triggerTick) {
            st.triggerTick = 0L;
            fireAll(level, st);
        }
        // 抉择超时：不再按时间超时，改为在睡觉阶段自动拒绝（见 autoRejectAll）
        // 检测玩家回家：补发未看到的事件给刚回家的成员
        tickReturnHome(level, st);
        // 探险归来结算
        if (!st.expeditions.isEmpty()) {
            Iterator<Expedition> it = st.expeditions.iterator();
            while (it.hasNext()) {
                Expedition exp = it.next();
                if (now < exp.resolveTick) {
                    continue;
                }
                it.remove();
                resolveExpedition(level, exp);
            }
        }
    }

    // ══════════════════════════ 触发与展示 ══════════════════════════

    /** 傍晚触发：为所有队伍抽取并展示当日事件。由 SixtySecondsManager 调用。 */
    public static void fireEveningEvents(ServerLevel level) {
        LevelState st = STATE.computeIfAbsent(level, ignored -> new LevelState());
        fireAll(level, st);
    }

    /** 检测玩家从室外进入庇护所时补发当日事件。 */
    private static void tickReturnHome(ServerLevel level, LevelState st) {
        if (st.todayEvent.isEmpty()) return;
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            EventDef def = st.todayEvent.get(team.teamId);
            if (def == null) continue;
            for (ServerPlayer member : onlineMembers(level, team)) {
                if (st.seenPlayers.contains(member.getUUID())) continue;
                if (!isPlayerInShelter(member, data, team)) continue;
                st.seenPlayers.add(member.getUUID());
                MutableComponent tag = Component.literal("[")
                        .append(Component.translatable(def.type.tagKey()))
                        .append(Component.literal("]"))
                        .withStyle(def.type.color);
                MutableComponent eventTitle = Component.translatable(LANG + def.id + ".title");
                net.exmo.sre.subtitle.SubtitleCommand.sendToPlayerTop(member,
                        Component.translatable(LANG + "evening_title")
                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                        Component.translatable(LANG + "evening_subtitle", data.dayNumber)
                                .withStyle(ChatFormatting.YELLOW),
                        80, false);
                member.displayClientMessage(Component.translatable(LANG + "header", data.dayNumber)
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
                member.displayClientMessage(Component.empty().append(tag).append(" ")
                        .append(eventTitle.copy().withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)), false);
                member.displayClientMessage(Component.translatable(LANG + def.id + ".story")
                        .withStyle(ChatFormatting.GRAY), false);
                playTypeSound(member, def.type);
                // 如果是抉择事件，也补发选项
                Pending pending = st.pending.get(team.teamId);
                if (pending != null && def.choice != null) {
                    MutableComponent options = Component.empty();
                    for (int i = 1; i <= 2; i++) {
                        String cmd = "/sre:60s event " + pending.token + " " + i;
                        options.append(Component.literal("【")
                                .append(Component.translatable(LANG + def.id + ".opt" + i))
                                .append(Component.literal("】"))
                                .withStyle(style -> style
                                        .withColor(ChatFormatting.YELLOW)
                                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                                Component.translatable(LANG + "click_hint")))));
                        options.append(Component.literal("  "));
                    }
                    member.displayClientMessage(options, false);
                    member.displayClientMessage(Component.translatable(LANG + "choose_hint_until_sleep")
                            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), false);
                }
            }
        }
    }

    /** 强制睡觉时自动拒绝所有未决抉择事件。 */
    public static void autoRejectAll(ServerLevel level) {
        LevelState st = STATE.get(level);
        if (st == null || st.pending.isEmpty()) {
            return;
        }
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        Iterator<Map.Entry<Integer, Pending>> it = st.pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Pending> entry = it.next();
            it.remove();
            SixtySecondsState.TeamData team = data.teams.get(entry.getKey());
            EventDef def = EVENTS.get(entry.getValue().eventId);
            if (team != null && def != null && def.choice != null) {
                msgTeam(level, team, Component.translatable(LANG + "auto_rejected",
                        Component.translatable(LANG + def.id + ".title"))
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                def.choice.choose(level, team, null, 2);
            }
        }
    }

    /** 傍晚播报家庭成员状态（在事件之前）。 */
    public static void broadcastFamilyStatus(ServerLevel level) {
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            List<ServerPlayer> members = onlineMembers(level, team);
            if (members.isEmpty()) continue;
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(LANG + "family_status")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            for (ServerPlayer member : members) {
                SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(member);
                String name = member.getGameProfile().getName();
                int max = SixtySecondsStatsComponent.MAX;
                // 饥饿
                if (stats.hunger < max * 0.3) {
                    lines.add(Component.translatable(LANG + "status_hunger_critical", name)
                            .withStyle(ChatFormatting.RED));
                } else if (stats.hunger < max * 0.6) {
                    lines.add(Component.translatable(LANG + "status_hunger_low", name)
                            .withStyle(ChatFormatting.GOLD));
                }
                // 口渴
                if (stats.thirst < max * 0.3) {
                    lines.add(Component.translatable(LANG + "status_thirst_critical", name)
                            .withStyle(ChatFormatting.RED));
                } else if (stats.thirst < max * 0.6) {
                    lines.add(Component.translatable(LANG + "status_thirst_low", name)
                            .withStyle(ChatFormatting.AQUA));
                }
                // 理智
                int sanMax = stats.sanityMax > 0 ? stats.sanityMax : max;
                if (stats.sanity < sanMax * 0.3) {
                    lines.add(Component.translatable(LANG + "status_sanity_critical", name)
                            .withStyle(ChatFormatting.DARK_PURPLE));
                } else if (stats.sanity < sanMax * 0.6) {
                    lines.add(Component.translatable(LANG + "status_sanity_low", name)
                            .withStyle(ChatFormatting.LIGHT_PURPLE));
                }
                // 污染
                if (stats.pollution > 60) {
                    lines.add(Component.translatable(LANG + "status_pollution_high", name)
                            .withStyle(ChatFormatting.DARK_GREEN));
                } else if (stats.pollution > 30) {
                    lines.add(Component.translatable(LANG + "status_pollution_low", name)
                            .withStyle(ChatFormatting.GREEN));
                }
                // 生病
                if (stats.sick) {
                    lines.add(Component.translatable(LANG + "status_sick", name)
                            .withStyle(ChatFormatting.YELLOW));
                }
            }
            // 死亡成员
            for (UUID uuid : team.members) {
                if (level.getPlayerByUUID(uuid) == null && !isMemberOnline(level, team, uuid)) {
                    // 检查是否曾经是该队成员但现在死了
                }
            }
            // 只发给在庇护所的成员
            for (ServerPlayer member : members) {
                if (!isPlayerInShelter(member, data, team)) continue;
                for (Component line : lines) {
                    member.displayClientMessage(line, false);
                }
            }
        }
    }

    private static boolean isPlayerInShelter(ServerPlayer player, SixtySecondsState.Data data,
            SixtySecondsState.TeamData team) {
        double x = player.getX(), y = player.getY(), z = player.getZ();
        return (team.shelterBox != null && team.shelterBox.contains(x, y, z))
                || (team.residentialBox != null && team.residentialBox.contains(x, y, z));
    }

    private static boolean isMemberOnline(ServerLevel level, SixtySecondsState.TeamData team, UUID uuid) {
        return level.getPlayerByUUID(uuid) instanceof ServerPlayer p && !GameUtils.isPlayerEliminated(p);
    }

    private static void fireAll(ServerLevel level, LevelState st) {
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        st.todayEvent.clear();
        st.seenPlayers.clear();
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            if (onlineMembers(level, team).isEmpty()) {
                continue;
            }
            EventDef def = pickEvent(level.getRandom(), st.lastEvent.get(team.teamId));
            if (def == null) {
                continue;
            }
            st.lastEvent.put(team.teamId, def.id);
            st.todayEvent.put(team.teamId, def);
            trigger(level, st, team, def);
        }
    }

    /** 加权抽取，排除本队昨日事件（避免连续重复）。 */
    private static EventDef pickEvent(RandomSource random, String excludeId) {
        int total = 0;
        for (EventDef def : EVENTS.values()) {
            if (!def.id.equals(excludeId)) {
                total += def.weight;
            }
        }
        if (total <= 0) {
            return null;
        }
        int r = random.nextInt(total);
        for (EventDef def : EVENTS.values()) {
            if (def.id.equals(excludeId)) {
                continue;
            }
            r -= def.weight;
            if (r < 0) {
                return def;
            }
        }
        return null;
    }

    /** 管理指令：给指定玩家所在队强制触发指定事件；未知 id / 未编队返回 false。 */
    public static boolean force(ServerPlayer player, String eventId) {
        EventDef def = EVENTS.get(eventId);
        if (def == null) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        SixtySecondsState.TeamData team = SixtySecondsState.get(level).teams
                .get(SixtySecondsStatsComponent.KEY.get(player).teamId);
        if (team == null) {
            return false;
        }
        LevelState st = STATE.computeIfAbsent(level, ignored -> new LevelState());
        st.pending.remove(team.teamId);
        trigger(level, st, team, def);
        return true;
    }

    public static java.util.Collection<String> eventIds() {
        return EVENTS.keySet();
    }

    /** 播报剧情（title + 聊天栏故事）；抉择事件再挂上待决状态与可点击选项。只显示给在庇护所的成员。 */
    private static void trigger(ServerLevel level, LevelState st, SixtySecondsState.TeamData team, EventDef def) {
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        MutableComponent tag = Component.literal("[")
                .append(Component.translatable(def.type.tagKey()))
                .append(Component.literal("]"))
                .withStyle(def.type.color);
        MutableComponent eventTitle = Component.translatable(LANG + def.id + ".title");
        for (ServerPlayer member : onlineMembers(level, team)) {
            if (!isPlayerInShelter(member, data, team)) continue;
            st.seenPlayers.add(member.getUUID());
            // title 提示
            net.exmo.sre.subtitle.SubtitleCommand.sendToPlayerTop(member,
                    Component.translatable(LANG + "evening_title")
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                    Component.translatable(LANG + "evening_subtitle", data.dayNumber)
                            .withStyle(ChatFormatting.YELLOW),
                    80, false);
            member.displayClientMessage(Component.translatable(LANG + "header", data.dayNumber)
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
            member.displayClientMessage(Component.empty().append(tag).append(" ")
                    .append(eventTitle.copy().withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)), false);
            member.displayClientMessage(Component.translatable(LANG + def.id + ".story")
                    .withStyle(ChatFormatting.GRAY), false);
            playTypeSound(member, def.type);
        }
        if (def.choice == null) {
            def.instant.run(level, team);
            return;
        }
        int token = 1 + level.getRandom().nextInt(Integer.MAX_VALUE - 1);
        st.pending.put(team.teamId, new Pending(token, def.id, Long.MAX_VALUE)); // 在睡觉前一直有效
        MutableComponent options = Component.empty();
        for (int i = 1; i <= 2; i++) {
            String cmd = "/sre:60s event " + token + " " + i;
            options.append(Component.literal("【")
                    .append(Component.translatable(LANG + def.id + ".opt" + i))
                    .append(Component.literal("】"))
                    .withStyle(style -> style
                            .withColor(ChatFormatting.YELLOW)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.translatable(LANG + "click_hint")))));
            options.append(Component.literal("  "));
        }
        Component hint = Component.translatable(LANG + "choose_hint_until_sleep")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
        for (ServerPlayer member : onlineMembers(level, team)) {
            if (!isPlayerInShelter(member, data, team)) continue;
            member.displayClientMessage(options, false);
            member.displayClientMessage(hint, false);
        }
    }

    private static void playTypeSound(ServerPlayer player, Type type) {
        switch (type) {
            case MISFORTUNE, RAID -> player.playNotifySound(SoundEvents.VILLAGER_NO,
                    SoundSource.AMBIENT, 0.7F, 0.7F);
            case FORTUNE -> player.playNotifySound(SoundEvents.PLAYER_LEVELUP,
                    SoundSource.AMBIENT, 0.5F, 1.4F);
            case TRADE -> player.playNotifySound(SoundEvents.VILLAGER_TRADE,
                    SoundSource.AMBIENT, 0.8F, 1.0F);
            case MODIFIER -> player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.AMBIENT, 0.6F, 1.1F);
            default -> player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(),
                    SoundSource.AMBIENT, 0.6F, 0.9F);
        }
    }

    // ══════════════════════════ 玩家点击选项 ══════════════════════════

    /** 由 {@code /sre:60s event <token> <option>} 调用；校验队伍 / token / 状态后结算。 */
    public static void choose(ServerPlayer player, int token, int option) {
        ServerLevel level = player.serverLevel();
        LevelState st = STATE.get(level);
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
        SixtySecondsState.TeamData team = SixtySecondsState.get(level).teams.get(stats.teamId);
        Pending pending = st == null || team == null ? null : st.pending.get(team.teamId);
        if (pending == null || pending.token != token) {
            player.displayClientMessage(Component.translatable(LANG + "expired")
                    .withStyle(ChatFormatting.GRAY), false);
            return;
        }
        if (stats.monster || stats.downed || GameUtils.isPlayerEliminated(player)) {
            player.displayClientMessage(Component.translatable(LANG + "cannot_choose")
                    .withStyle(ChatFormatting.RED), false);
            return;
        }
        EventDef def = EVENTS.get(pending.eventId);
        if (def == null || def.choice == null) {
            st.pending.remove(team.teamId);
            return;
        }
        int opt = Mth.clamp(option, 1, 2);
        if (!def.choice.choose(level, team, player, opt)) {
            return; // 前置不满足（已提示点击者），保持待决
        }
        st.pending.remove(team.teamId);
        Component chosen = Component.translatable(LANG + "chosen_by", player.getGameProfile().getName(),
                Component.translatable(LANG + def.id + ".opt" + opt)).withStyle(ChatFormatting.GRAY);
        for (ServerPlayer member : onlineMembers(level, team)) {
            if (member != player) {
                member.displayClientMessage(chosen, false);
            }
        }
    }

    // ══════════════════════════ 事件注册（40 条，带剧情） ══════════════════════════

    private static void put(EventDef def) {
        EVENTS.put(def.id, def);
    }

    private static void instant(String id, Type type, int weight, InstantEffect effect) {
        put(new EventDef(id, type, weight, effect, null));
    }

    private static void choice(String id, Type type, int weight, ChoiceEffect effect) {
        put(new EventDef(id, type, weight, null, effect));
    }

    // ══════════════════════════ 日级修正事件辅助 ══════════════════════════

    /** 为队伍设置日级修正倍率，并在结果播报中注明效果。 */
    private static void applyModifier(SixtySecondsState.TeamData team, String key, double value) {
        team.dailyModifiers.put(key, value);
    }

    /** 注册一条修正类事件（瞬发，施加日级 buff/debuff）。 */
    private static void modifierEvent(String id, Type type, int weight, String modKey, double modVal,
            InstantEffect extra) {
        instant(id, type, weight, (level, team) -> {
            applyModifier(team, modKey, modVal);
            if (extra != null) {
                extra.run(level, team);
            }
        });
    }

    private static void registerAll() {
        // ── 不幸（瞬发，15 条）────────────────────────────────────────────
        // 1. 鼠患：丢至多 2 件食物，全队 san -5
        instant("rats", Type.MISFORTUNE, 10, (level, team) -> {
            List<ItemStack> lost = loseFromHome(level, team, 2, foodFilter(level));
            teamSanity(level, team, -5);
            result(level, team, "rats", lostText(level, lost));
        });
        // 2. 水管爆裂：全队口渴 -10，丢 1 件水
        instant("burst_pipe", Type.MISFORTUNE, 10, (level, team) -> {
            List<ItemStack> lost = loseFromHome(level, team, 1, waterFilter(level));
            forEachMember(level, team, p -> addThirst(p, -10));
            result(level, team, "burst_pipe", lostText(level, lost));
        });
        // 3. 集体噩梦：全队 san -10
        instant("nightmare", Type.MISFORTUNE, 10, (level, team) -> {
            teamSanity(level, team, -10);
            result(level, team, "nightmare");
        });
        // 4. 夜贼光顾：丢 2 件任意物资
        instant("thief", Type.MISFORTUNE, 10, (level, team) -> {
            List<ItemStack> lost = loseFromHome(level, team, 2, stack -> true);
            result(level, team, "thief", lostText(level, lost));
        });
        // 5. 食物变质：丢 1 件食物，随机一人饥饿 -15
        instant("spoiled_food", Type.MISFORTUNE, 10, (level, team) -> {
            List<ItemStack> lost = loseFromHome(level, team, 1, foodFilter(level));
            ServerPlayer victim = randomMember(level, team);
            if (victim != null) {
                addHunger(victim, -15);
            }
            result(level, team, "spoiled_food",
                    Component.literal(victim == null ? "?" : victim.getGameProfile().getName()),
                    lostText(level, lost));
        });
        // 6. 灰烬风暴：全队污染 +8、san -3
        instant("ash_storm", Type.MISFORTUNE, 8, (level, team) -> {
            forEachMember(level, team, p -> {
                addPollution(p, 8);
                addSanity(p, -3);
            });
            result(level, team, "ash_storm");
        });
        // 7. 门框朽坏：门耐久 -20（不至攻破）
        instant("door_rot", Type.MISFORTUNE, 8, (level, team) -> {
            team.doorHp = Math.max(1, team.doorHp - 20);
            result(level, team, "door_rot");
        });
        // 8. 电台怪声：全队 san -6
        instant("radio_noise", Type.MISFORTUNE, 10, (level, team) -> {
            teamSanity(level, team, -6);
            result(level, team, "radio_noise");
        });
        // 9. 突发疾病：随机一名健康成员病倒（无候选则虚惊一场）
        instant("sudden_illness", Type.MISFORTUNE, 8, (level, team) -> {
            List<ServerPlayer> healthy = new ArrayList<>();
            for (ServerPlayer p : onlineMembers(level, team)) {
                SixtySecondsStatsComponent s = SixtySecondsStatsComponent.KEY.get(p);
                if (!s.sick && !s.monster && !s.downed) {
                    healthy.add(p);
                }
            }
            if (healthy.isEmpty()) {
                result(level, team, "sudden_illness.none");
                return;
            }
            ServerPlayer victim = healthy.get(level.getRandom().nextInt(healthy.size()));
            SixtySecondsSicknessSystem.makeSick(victim);
            result(level, team, "sudden_illness", Component.literal(victim.getGameProfile().getName()));
        });
        // 10. 发电机短路：立即断电
        instant("generator_short", Type.MISFORTUNE, 8, (level, team) -> {
            team.powerEndTick = Math.min(team.powerEndTick, level.getGameTime());
            result(level, team, "generator_short");
        });
        // 27. 床虱成灾：全队 san -8
        instant("bed_bugs", Type.MISFORTUNE, 8, (level, team) -> {
            teamSanity(level, team, -8);
            result(level, team, "bed_bugs");
        });
        // 28. 威胁涂鸦：全队 san -7，门耐久 -10
        instant("graffiti_warning", Type.MISFORTUNE, 8, (level, team) -> {
            teamSanity(level, team, -7);
            team.doorHp = Math.max(1, team.doorHp - 10);
            result(level, team, "graffiti_warning");
        });
        // 29. 浣熊翻家：丢 1 件食物 + 1 件材料
        instant("raccoon", Type.MISFORTUNE, 8, (level, team) -> {
            List<ItemStack> lost = loseFromHome(level, team, 1, foodFilter(level));
            lost.addAll(loseFromHome(level, team, 1, categoryFilter(level, "material")));
            result(level, team, "raccoon", lostText(level, lost));
        });
        // 30. 水源污染：全队污染 +10，丢 1 件水
        instant("contaminated_water", Type.MISFORTUNE, 8, (level, team) -> {
            List<ItemStack> lost = loseFromHome(level, team, 1, waterFilter(level));
            forEachMember(level, team, p -> addPollution(p, 10));
            result(level, team, "contaminated_water", lostText(level, lost));
        });
        // 31. 工具锈蚀：丢 1 件工具
        instant("tool_rust", Type.MISFORTUNE, 8, (level, team) -> {
            List<ItemStack> lost = loseFromHome(level, team, 1, categoryFilter(level, "tool"));
            result(level, team, "tool_rust", lostText(level, lost));
        });

        // ── 机遇（瞬发，11 条）────────────────────────────────────────────
        // 11. 墙内暗格：获得 2 件材料/工具
        instant("wall_stash", Type.FORTUNE, 10, (level, team) -> {
            List<ItemStack> gained = rollLoot(level, 2, "material", "tool");
            giveToTeam(level, team, gained);
            result(level, team, "wall_stash", itemsText(gained));
        });
        // 12. 好心人：门口留下食物 + 水各 1
        instant("kind_stranger", Type.FORTUNE, 10, (level, team) -> {
            List<ItemStack> gained = rollLoot(level, 1, "food");
            gained.addAll(rollLoot(level, 1, "water"));
            giveToTeam(level, team, gained);
            result(level, team, "kind_stranger", itemsText(gained));
        });
        // 13. 洁净降雨：全队口渴 +15
        instant("clean_rain", Type.FORTUNE, 10, (level, team) -> {
            forEachMember(level, team, p -> addThirst(p, 15));
            result(level, team, "clean_rain");
        });
        // 14. 美梦：全队 san +10
        instant("sweet_dream", Type.FORTUNE, 10, (level, team) -> {
            teamSanity(level, team, 10);
            result(level, team, "sweet_dream");
        });
        // 15. 军方广播：全队 san +8
        instant("military_broadcast", Type.FORTUNE, 10, (level, team) -> {
            teamSanity(level, team, 8);
            result(level, team, "military_broadcast");
        });
        // 16. 政府空投：探索区随机空投（当日全局仅一次，重复抽到改为 san +5）
        instant("gov_airdrop", Type.FORTUNE, 6, (level, team) -> {
            LevelState st = STATE.computeIfAbsent(level, ignored -> new LevelState());
            int day = SixtySecondsState.get(level).dayNumber;
            if (st.airdropDay != day && SixtySecondsAirdrop.dropRandom(level)) {
                st.airdropDay = day;
                result(level, team, "gov_airdrop");
            } else {
                teamSanity(level, team, 5);
                result(level, team, "gov_airdrop.seen");
            }
        });
        // 16b. 电台岛屿情报：海岛模式开启时解锁一座未知岛（海图点亮）；否则/全解锁 → san +6 兜底
        instant("island_radio_intel", Type.FORTUNE, 8, (level, team) -> {
            if (net.exmo.sre.sixtyseconds.island.SixtySecondsIslands.intelEvent(level, team)) {
                result(level, team, "island_radio_intel");
            } else {
                teamSanity(level, team, 6);
                result(level, team, "island_radio_intel.static");
            }
        });
        // 16c. 漂流瓶海图残页：同上第二条解锁途径（权重更低）
        instant("island_drift_bottle", Type.FORTUNE, 6, (level, team) -> {
            if (net.exmo.sre.sixtyseconds.island.SixtySecondsIslands.intelEvent(level, team)) {
                result(level, team, "island_drift_bottle");
            } else {
                List<ItemStack> gained = rollLoot(level, 1, "material");
                giveToTeam(level, team, gained);
                result(level, team, "island_drift_bottle.static", itemsText(gained));
            }
        });
        // 17. 流浪猫：叼来 1 件食物，全队 san +5
        instant("stray_cat", Type.FORTUNE, 10, (level, team) -> {
            List<ItemStack> gained = rollLoot(level, 1, "food");
            giveToTeam(level, team, gained);
            teamSanity(level, team, 5);
            result(level, team, "stray_cat", itemsText(gained));
        });
        // 32. 旧唱片：全队 san +12
        instant("old_records", Type.FORTUNE, 8, (level, team) -> {
            teamSanity(level, team, 12);
            result(level, team, "old_records");
        });
        // 33. 娱乐援助箱：获得 1 件随机娱乐物品（扑克/象棋/口琴/吉他/泰迪熊）
        instant("entertainment_box", Type.FORTUNE, 8, (level, team) -> {
            net.minecraft.world.item.Item[] pool = {
                    ModItems.SIXTY_SECONDS_POKER, ModItems.SIXTY_SECONDS_CHESS,
                    ModItems.SIXTY_SECONDS_HARMONICA, ModItems.SIXTY_SECONDS_GUITAR,
                    ModItems.SIXTY_SECONDS_TEDDY_BEAR };
            List<ItemStack> gained = new ArrayList<>();
            gained.add(new ItemStack(pool[level.getRandom().nextInt(pool.length)]));
            giveToTeam(level, team, gained);
            result(level, team, "entertainment_box", itemsText(gained));
        });
        // 34. 废料堆：获得 3 件材料
        instant("scrap_pile", Type.FORTUNE, 10, (level, team) -> {
            List<ItemStack> gained = rollLoot(level, 3, "material");
            giveToTeam(level, team, gained);
            result(level, team, "scrap_pile", itemsText(gained));
        });
        // 35. 救济金：每名在线成员 +5 代币
        instant("relief_fund", Type.FORTUNE, 8, (level, team) -> {
            forEachMember(level, team, p -> SREPlayerMinigameTaskComponent.KEY.get(p).addTokens(5));
            result(level, team, "relief_fund");
        });

        // ── 抉择（6 条）──────────────────────────────────────────────────
        // 18. 深夜敲门：开门 60% 好心人（+2 物资 san+5）/ 40% 劫匪（丢 1 件 san-8）；不开 san -3
        choice("door_knock", Type.CHOICE, 10, (level, team, clicker, option) -> {
            if (option == 1) {
                if (level.getRandom().nextFloat() < 0.6F) {
                    List<ItemStack> gained = rollLoot(level, 1, "food");
                    gained.addAll(rollLoot(level, 1, "medicine"));
                    giveToTeam(level, team, gained);
                    teamSanity(level, team, 5);
                    result(level, team, "door_knock.r1_good", itemsText(gained));
                } else {
                    List<ItemStack> lost = loseFromHome(level, team, 1, stack -> true);
                    teamSanity(level, team, -8);
                    result(level, team, "door_knock.r1_bad", lostText(level, lost));
                }
            } else {
                teamSanity(level, team, -3);
                result(level, team, "door_knock.r2");
            }
            return true;
        });
        // 24. 求助的幸存者：给 1 件食物（点击者背包出）→ 全队 san +10；赶走 → san -5
        choice("begging_survivor", Type.CHOICE, 10, (level, team, clicker, option) -> {
            if (option == 1) {
                ItemStack food = takeFromInventory(clicker, foodFilter(level));
                if (food == null) {
                    fail(clicker, "no_food");
                    return false;
                }
                teamSanity(level, team, 10);
                result(level, team, "begging_survivor.r1", food.getHoverName());
            } else {
                teamSanity(level, team, -5);
                result(level, team, "begging_survivor.r2");
            }
            return true;
        });
        // 25. 电台竞猜：押 5 代币，50% 赢 15 / 50% 打水漂（san -3）；不参与无事
        choice("radio_gamble", Type.CHOICE, 8, (level, team, clicker, option) -> {
            if (option == 1) {
                SREPlayerMinigameTaskComponent tokens = SREPlayerMinigameTaskComponent.KEY.get(clicker);
                if (tokens.getTokens() < 5) {
                    fail(clicker, "no_coins", 5);
                    return false;
                }
                tokens.addTokens(-5);
                if (level.getRandom().nextBoolean()) {
                    tokens.addTokens(15);
                    result(level, team, "radio_gamble.r1_win",
                            Component.literal(clicker.getGameProfile().getName()));
                } else {
                    addSanity(clicker, -3);
                    result(level, team, "radio_gamble.r1_lose",
                            Component.literal(clicker.getGameProfile().getName()));
                }
            } else {
                result(level, team, "radio_gamble.r2");
            }
            return true;
        });
        // 26. 可疑的罐头：吃 → 55% 饥饿 +30 / 45% 生病；扔掉无事
        choice("strange_can", Type.CHOICE, 10, (level, team, clicker, option) -> {
            if (option == 1) {
                ServerPlayer eater = clicker != null ? clicker : randomMember(level, team);
                if (eater == null) {
                    return true;
                }
                if (level.getRandom().nextFloat() < 0.55F) {
                    addHunger(eater, 30);
                    result(level, team, "strange_can.r1_good",
                            Component.literal(eater.getGameProfile().getName()));
                } else {
                    SixtySecondsSicknessSystem.makeSick(eater);
                    result(level, team, "strange_can.r1_bad",
                            Component.literal(eater.getGameProfile().getName()));
                }
            } else {
                result(level, team, "strange_can.r2");
            }
            return true;
        });
        // 36. 受伤的猎犬：收留（点击者消耗 1 件食物）→ 全队 san +8；赶走 → san -2
        choice("stray_dog", Type.CHOICE, 8, (level, team, clicker, option) -> {
            if (option == 1) {
                ItemStack food = takeFromInventory(clicker, foodFilter(level));
                if (food == null) {
                    fail(clicker, "no_food");
                    return false;
                }
                teamSanity(level, team, 8);
                result(level, team, "stray_dog.r1", food.getHoverName());
            } else {
                teamSanity(level, team, -2);
                result(level, team, "stray_dog.r2");
            }
            return true;
        });
        // 37. 神秘商人：花 7 代币买惊喜袋，60% 药+武器各 1 / 40% 一块破布（san -5）
        choice("mystery_bag", Type.CHOICE, 8, (level, team, clicker, option) -> {
            if (option == 1) {
                SREPlayerMinigameTaskComponent tokens = SREPlayerMinigameTaskComponent.KEY.get(clicker);
                if (tokens.getTokens() < 7) {
                    fail(clicker, "no_coins", 7);
                    return false;
                }
                tokens.addTokens(-7);
                if (level.getRandom().nextFloat() < 0.6F) {
                    List<ItemStack> gained = rollLoot(level, 1, "medicine", "weapon");
                    give(clicker, gained);
                    result(level, team, "mystery_bag.r1_good", itemsText(gained));
                } else {
                    List<ItemStack> gained = new ArrayList<>();
                    gained.add(new ItemStack(ModItems.SIXTY_SECONDS_RAG));
                    give(clicker, gained);
                    addSanity(clicker, -5);
                    result(level, team, "mystery_bag.r1_bad",
                            Component.literal(clicker.getGameProfile().getName()));
                }
            } else {
                result(level, team, "mystery_bag.r2");
            }
            return true;
        });

        // ── 交易（3 条）──────────────────────────────────────────────────
        // 19. 商队来访：10 代币 → 食物/水/药品各 1
        choice("trade_caravan", Type.TRADE, 10, (level, team, clicker, option) -> {
            if (option == 1) {
                SREPlayerMinigameTaskComponent tokens = SREPlayerMinigameTaskComponent.KEY.get(clicker);
                if (tokens.getTokens() < 10) {
                    fail(clicker, "no_coins", 10);
                    return false;
                }
                tokens.addTokens(-10);
                List<ItemStack> gained = rollLoot(level, 1, "food");
                gained.addAll(rollLoot(level, 1, "water"));
                gained.addAll(rollLoot(level, 1, "medicine"));
                give(clicker, gained);
                result(level, team, "trade_caravan.r1", itemsText(gained));
            } else {
                result(level, team, "trade_caravan.r2");
            }
            return true;
        });
        // 20. 以物易物：1 件食物（点击者背包出）→ 2 件药品
        choice("barter_stranger", Type.TRADE, 10, (level, team, clicker, option) -> {
            if (option == 1) {
                ItemStack food = takeFromInventory(clicker, foodFilter(level));
                if (food == null) {
                    fail(clicker, "no_food");
                    return false;
                }
                List<ItemStack> gained = rollLoot(level, 2, "medicine");
                give(clicker, gained);
                result(level, team, "barter_stranger.r1", food.getHoverName(), itemsText(gained));
            } else {
                result(level, team, "barter_stranger.r2");
            }
            return true;
        });
        // 38. 军火贩子：8 代币 → 8 发子弹 + 1 件武器类物资
        choice("ammo_trader", Type.TRADE, 8, (level, team, clicker, option) -> {
            if (option == 1) {
                SREPlayerMinigameTaskComponent tokens = SREPlayerMinigameTaskComponent.KEY.get(clicker);
                if (tokens.getTokens() < 8) {
                    fail(clicker, "no_coins", 8);
                    return false;
                }
                tokens.addTokens(-8);
                List<ItemStack> gained = new ArrayList<>();
                gained.add(amplify(level, new ItemStack(ModItems.SIXTY_SECONDS_AMMO, 8)));
                gained.addAll(rollLoot(level, 1, "weapon"));
                give(clicker, gained);
                result(level, team, "ammo_trader.r1", itemsText(gained));
            } else {
                result(level, team, "ammo_trader.r2");
            }
            return true;
        });

        // ── 探险（3 条，需手电筒，延迟结算）───────────────────────────────
        // 21. 隐秘地窖：3 件物资 + 污染 +10，25% 受伤 -20
        choice("hidden_cellar", Type.EXPEDITION, 8,
                (level, team, clicker, option) -> depart(level, team, clicker, option, "hidden_cellar"));
        // 22. 塌陷隧道：5 件物资 + 污染 +15，40% 受伤 -30，15% 生病
        choice("collapsed_tunnel", Type.EXPEDITION, 8,
                (level, team, clicker, option) -> depart(level, team, clicker, option, "collapsed_tunnel"));
        // 39. 屋顶水塔：2 水 + 2 材料 + 污染 +5，20% 受伤 -15
        choice("water_tower", Type.EXPEDITION, 8,
                (level, team, clicker, option) -> depart(level, team, clicker, option, "water_tower"));

        // ── 劫掠（2 条）──────────────────────────────────────────────────
        // 23. 铤而走险：洗劫随机邻队家（至多 2 件）→ 归己队，全队 san -15；受害队收到通知且 san -5
        choice("raid_neighbor", Type.RAID, 8, (level, team, clicker, option) -> {
            if (option != 1) {
                teamSanity(level, team, 3);
                result(level, team, "raid_neighbor.r2");
                return true;
            }
            SixtySecondsState.TeamData victim = randomOtherTeam(level, team);
            if (victim == null) {
                result(level, team, "raid_neighbor.r1_empty");
                teamSanity(level, team, -8);
                return true;
            }
            List<ItemStack> stolen = loseFromHome(level, victim, 2, stack -> true);
            teamSanity(level, team, -15);
            if (stolen.isEmpty()) {
                result(level, team, "raid_neighbor.r1_empty");
            } else {
                giveToTeam(level, team, copyAll(stolen));
                result(level, team, "raid_neighbor.r1", itemsText(stolen));
            }
            Component note = Component.translatable(LANG + "raid_neighbor.victim",
                    stolen.isEmpty() ? Component.translatable(LANG + "nothing") : itemsText(stolen))
                    .withStyle(ChatFormatting.RED);
            for (ServerPlayer p : onlineMembers(level, victim)) {
                p.displayClientMessage(note, false);
                addSanity(p, -5);
                p.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.AMBIENT, 0.8F, 0.6F);
            }
            return true;
        });
        // 40. 偷接电线：抽走随机邻队的供电给自己续 90s，全队 san -10，受害队收到通知
        choice("siphon_power", Type.RAID, 8, (level, team, clicker, option) -> {
            if (option != 1) {
                teamSanity(level, team, 2);
                result(level, team, "siphon_power.r2");
                return true;
            }
            SixtySecondsState.TeamData victim = randomOtherTeam(level, team);
            if (victim == null) {
                teamSanity(level, team, -5);
                result(level, team, "siphon_power.r1_empty");
                return true;
            }
            long now = level.getGameTime();
            victim.powerEndTick = Math.min(victim.powerEndTick, now);
            team.powerEndTick = Math.max(team.powerEndTick, now) + SixtySecondsBalance.POWER_PER_FUEL_TICKS;
            teamSanity(level, team, -10);
            result(level, team, "siphon_power.r1");
            Component note = Component.translatable(LANG + "siphon_power.victim").withStyle(ChatFormatting.RED);
            for (ServerPlayer p : onlineMembers(level, victim)) {
                p.displayClientMessage(note, false);
                p.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.AMBIENT, 0.8F, 0.6F);
            }
            return true;
        });

        // ── 修正（MODIFIER，4 条，施加日级 buff/debuff）─────────────────
        // 41. 士气高涨：今日 san 消耗 -50%
        modifierEvent("morale_boost", Type.MODIFIER, 10, "drain_sanity", 0.5, (level, team) -> {
            result(level, team, "morale_boost");
        });
        // 42. 加固防线：今晚门受伤 -30%，门耐久 +15
        modifierEvent("fortified_defense", Type.MODIFIER, 8, "door_damage", 0.7, (level, team) -> {
            team.doorHp = Math.min(team.doorMaxHp, team.doorHp + 15);
            result(level, team, "fortified_defense");
        });
        // 43. 墙垣松动：今晚门受伤 +40%，门耐久 -15
        modifierEvent("weakened_defense", Type.MODIFIER, 8, "door_damage", 1.4, (level, team) -> {
            team.doorHp = Math.max(1, team.doorHp - 15);
            result(level, team, "weakened_defense");
        });
        // 44. 毒雾弥漫：今日污染增速 +50%，san -5
        modifierEvent("toxic_haze", Type.MODIFIER, 8, "drain_pollution", 1.5, (level, team) -> {
            teamSanity(level, team, -5);
            result(level, team, "toxic_haze");
        });
        // 45. 节约口粮：今日饥饿消耗 -30%
        modifierEvent("rationing_food", Type.MODIFIER, 8, "drain_hunger", 0.7, (level, team) -> {
            result(level, team, "rationing_food");
        });
        // 46. 配给用水：今日口渴消耗 -30%
        modifierEvent("rationing_water", Type.MODIFIER, 8, "drain_thirst", 0.7, (level, team) -> {
            result(level, team, "rationing_water");
        });

        // ── 不幸新增（3 条）────────────────────────────────────────────
        // 47. 疫鼠群：丢 2 食物，随机一人可能生病
        instant("plague_rats", Type.MISFORTUNE, 8, (level, team) -> {
            List<ItemStack> lost = loseFromHome(level, team, 2, foodFilter(level));
            ServerPlayer victim = randomMember(level, team);
            if (victim != null && level.getRandom().nextFloat() < 0.4F) {
                SixtySecondsSicknessSystem.makeSick(victim);
                teamSanity(level, team, -5);
                result(level, team, "plague_rats.sick",
                        Component.literal(victim.getGameProfile().getName()), lostText(level, lost));
            } else {
                teamSanity(level, team, -3);
                result(level, team, "plague_rats.clean", lostText(level, lost));
            }
        });
        // 48. 燃料泄漏：丢 2 废料，断电
        instant("fuel_leak", Type.MISFORTUNE, 8, (level, team) -> {
            List<ItemStack> lost = loseFromHome(level, team, 2,
                    stack -> stack.is(ModItems.SIXTY_SECONDS_SCRAP));
            team.powerEndTick = Math.min(team.powerEndTick, level.getGameTime());
            result(level, team, "fuel_leak", lostText(level, lost));
        });
        // 49. 谣言四起：全队 san -12，今日 san 消耗 +30%
        modifierEvent("evil_rumors", Type.MISFORTUNE, 6, "drain_sanity", 1.3, (level, team) -> {
            teamSanity(level, team, -12);
            result(level, team, "evil_rumors");
        });

        // ── 机遇新增（3 条）────────────────────────────────────────────
        // 50. 幸存者藏宝：获得 3 件随机物资，san +5
        instant("survivor_cache", Type.FORTUNE, 10, (level, team) -> {
            List<ItemStack> gained = rollLoot(level, 1, "food");
            gained.addAll(rollLoot(level, 1, "water"));
            gained.addAll(rollLoot(level, 1, "material"));
            giveToTeam(level, team, gained);
            teamSanity(level, team, 5);
            result(level, team, "survivor_cache", itemsText(gained));
        });
        // 51. 净化之雨：全队污染 -30，口渴 +10
        instant("purifying_rain", Type.FORTUNE, 8, (level, team) -> {
            forEachMember(level, team, p -> {
                addPollution(p, -30);
                addThirst(p, 10);
            });
            result(level, team, "purifying_rain");
        });
        // 52. 遗弃快递：获得 2 件随机物资
        instant("abandoned_package", Type.FORTUNE, 10, (level, team) -> {
            String[] cats = { "food", "water", "medicine", "material", "tool" };
            String cat = cats[level.getRandom().nextInt(cats.length)];
            List<ItemStack> gained = rollLoot(level, 2, cat);
            giveToTeam(level, team, gained);
            result(level, team, "abandoned_package", itemsText(gained));
        });

        // ── 抉择新增（3 条）────────────────────────────────────────────
        // 53. 流浪医生：花 30 金币 → 治愈全队所有病人 + 每人回 20 健康
        choice("wandering_doctor", Type.CHOICE, 10, (level, team, clicker, option) -> {
            if (option == 1) {
                SREPlayerMinigameTaskComponent tokens = SREPlayerMinigameTaskComponent.KEY.get(clicker);
                if (tokens.getTokens() < 5) {
                    fail(clicker, "no_coins", 5);
                    return false;
                }
                tokens.addTokens(-5);
                int cured = 0;
                for (ServerPlayer p : onlineMembers(level, team)) {
                    SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(p);
                    if (stats.sick) {
                        stats.sick = false;
                        stats.health = Math.min(SixtySecondsStatsComponent.MAX,
                                stats.health + 20);
                        stats.sync();
                        cured++;
                    } else if (stats.health < SixtySecondsStatsComponent.MAX) {
                        stats.health = Math.min(SixtySecondsStatsComponent.MAX,
                                stats.health + 20);
                        stats.sync();
                    }
                }
                if (cured > 0) {
                    teamSanity(level, team, 8);
                }
                result(level, team, "wandering_doctor.r1",
                        Component.literal(clicker.getGameProfile().getName()),
                        Component.literal(String.valueOf(cured)));
            } else {
                result(level, team, "wandering_doctor.r2");
            }
            return true;
        });
        // 54. 逃兵交易：花 5 代币 → 1 武器 + 4 子弹 + san -3（来路不明）
        choice("deserters_offer", Type.CHOICE, 8, (level, team, clicker, option) -> {
            if (option == 1) {
                SREPlayerMinigameTaskComponent tokens = SREPlayerMinigameTaskComponent.KEY.get(clicker);
                if (tokens.getTokens() < 5) {
                    fail(clicker, "no_coins", 5);
                    return false;
                }
                tokens.addTokens(-5);
                List<ItemStack> gained = rollLoot(level, 1, "weapon");
                gained.add(amplify(level, new ItemStack(ModItems.SIXTY_SECONDS_AMMO, 4)));
                give(clicker, gained);
                addSanity(clicker, -3);
                result(level, team, "deserters_offer.r1", itemsText(gained),
                        Component.literal(clicker.getGameProfile().getName()));
            } else {
                result(level, team, "deserters_offer.r2");
            }
            return true;
        });
        // 55. 地图碎片：二选一 → 出发探险（3 件物资 污染+8 15%受伤）或卖掉（+20 金币）
        choice("map_fragment", Type.CHOICE, 8, (level, team, clicker, option) -> {
            if (option == 1) {
                if (!hasFlashlight(clicker)) {
                    fail(clicker, "need_flashlight");
                    return false;
                }
                LevelState st = STATE.computeIfAbsent(level, ignored -> new LevelState());
                st.expeditions.add(new Expedition(team.teamId, clicker.getUUID(), "map_fragment",
                        level.getGameTime() + SixtySecondsBalance.DAILY_EVENT_EXPEDITION_TICKS));
                result(level, team, "map_fragment.depart",
                        Component.literal(clicker.getGameProfile().getName()));
            } else {
                SREPlayerMinigameTaskComponent tokens = SREPlayerMinigameTaskComponent.KEY.get(clicker);
                tokens.addTokens(3);
                result(level, team, "map_fragment.r2",
                        Component.literal(clicker.getGameProfile().getName()));
            }
            return true;
        });

        // ── 探险新增（2 条）────────────────────────────────────────────
        // 56. 废弃医院：3 药 + 2 随机，污染 +12，30% 受伤 -20，20% 生病
        choice("abandoned_hospital", Type.EXPEDITION, 8,
                (level, team, clicker, option) -> depart(level, team, clicker, option, "abandoned_hospital"));
        // 57. 军械库废墟：2 武器 + 4 子弹，污染 +8，25% 受伤 -25
        choice("armory_ruins", Type.EXPEDITION, 7,
                (level, team, clicker, option) -> depart(level, team, clicker, option, "armory_ruins"));

        // ── 交易新增（2 条）────────────────────────────────────────────
        // 58. 废品商：6 废料 → 2 食物 + 1 水
        choice("scrap_dealer", Type.TRADE, 8, (level, team, clicker, option) -> {
            if (option == 1) {
                ItemStack taken = takeFromInventory(clicker,
                        stack -> stack.is(ModItems.SIXTY_SECONDS_SCRAP) && stack.getCount() >= 6);
                if (taken == null) {
                    fail(clicker, "no_scrap");
                    return false;
                }
                taken.shrink(5); // 已拿走 1 个，再拿走 5 个 = 共 6
                List<ItemStack> gained = rollLoot(level, 2, "food");
                gained.addAll(rollLoot(level, 1, "water"));
                give(clicker, gained);
                result(level, team, "scrap_dealer.r1", itemsText(gained));
            } else {
                result(level, team, "scrap_dealer.r2");
            }
            return true;
        });
        // 59. 水贩：4 代币 → 3 瓶水
        choice("water_peddler", Type.TRADE, 8, (level, team, clicker, option) -> {
            if (option == 1) {
                SREPlayerMinigameTaskComponent tokens = SREPlayerMinigameTaskComponent.KEY.get(clicker);
                if (tokens.getTokens() < 4) {
                    fail(clicker, "no_coins", 4);
                    return false;
                }
                tokens.addTokens(-4);
                List<ItemStack> gained = rollLoot(level, 3, "water");
                give(clicker, gained);
                result(level, team, "water_peddler.r1", itemsText(gained));
            } else {
                result(level, team, "water_peddler.r2");
            }
            return true;
        });

        // ── 劫掠新增（1 条）────────────────────────────────────────────
        // 60. 偷粮队：从邻队偷 2 件食物，全队 san -12，受害队 san -5
        choice("steal_rations", Type.RAID, 7, (level, team, clicker, option) -> {
            if (option != 1) {
                result(level, team, "steal_rations.r2");
                return true;
            }
            SixtySecondsState.TeamData victim = randomOtherTeam(level, team);
            if (victim == null) {
                result(level, team, "steal_rations.r1_empty");
                teamSanity(level, team, -8);
                return true;
            }
            List<ItemStack> stolen = loseFromHome(level, victim, 2, foodFilter(level));
            teamSanity(level, team, -12);
            if (stolen.isEmpty()) {
                result(level, team, "steal_rations.r1_empty");
            } else {
                giveToTeam(level, team, copyAll(stolen));
                result(level, team, "steal_rations.r1", itemsText(stolen));
            }
            Component note = Component.translatable(LANG + "steal_rations.victim",
                    stolen.isEmpty() ? Component.translatable(LANG + "nothing") : itemsText(stolen))
                    .withStyle(ChatFormatting.RED);
            for (ServerPlayer p : onlineMembers(level, victim)) {
                p.displayClientMessage(note, false);
                addSanity(p, -5);
                p.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.AMBIENT, 0.8F, 0.6F);
            }
            return true;
        });

        // ══════════════════════════ 新增事件（61-82） ══════════════════════════

        // ── 抉择：烟鬼 ────────────────────────────────────────────────
        choice("smoker", Type.CHOICE, 8, (level, team, clicker, option) -> {
            if (option == 1) {
                // 给香烟
                ItemStack cig = takeFromInventory(clicker,
                        stack -> itemId(stack).equals("watheextraitems:cigarette"));
                if (cig == null) {
                    fail(clicker, "no_item", Component.translatable("item.watheextraitems.cigarette"));
                    return false;
                }
                List<ItemStack> gained = rollLoot(level, 1, "food");
                gained.addAll(rollLoot(level, 1, "water"));
                gained.add(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .get(net.minecraft.resources.ResourceLocation.parse("watheextraitems:wild_rice_seeds"))));
                give(clicker, gained);
                result(level, team, "smoker.r1", itemsText(gained));
            } else if (option == 2) {
                // 给雪茄
                ItemStack cig = takeFromInventory(clicker,
                        stack -> itemId(stack).equals("watheextraitems:cigar"));
                if (cig == null) {
                    fail(clicker, "no_item", Component.translatable("item.watheextraitems.cigar"));
                    return false;
                }
                List<ItemStack> gained = new ArrayList<>();
                for (int i = 0; i < 5; i++) gained.addAll(rollLoot(level, 1, "food"));
                for (int i = 0; i < 5; i++) gained.addAll(rollLoot(level, 1, "water"));
                gained.addAll(rollLoot(level, 3, "medicine"));
                gained.add(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .get(net.minecraft.resources.ResourceLocation.parse("watheextraitems:industrial_hemp_seeds"))));
                give(clicker, gained);
                result(level, team, "smoker.r2", itemsText(gained));
            } else {
                result(level, team, "smoker.r3");
            }
            return true;
        });
        // ── 抉择：赌徒 ────────────────────────────────────────────────
        choice("gambler", Type.CHOICE, 8, (level, team, clicker, option) -> {
            if (option == 1) {
                if (level.getRandom().nextBoolean()) {
                    List<ItemStack> gained = rollLoot(level, 3, "food");
                    gained.addAll(rollLoot(level, 3, "water"));
                    give(clicker, gained);
                    result(level, team, "gambler.r1_win", itemsText(gained));
                } else {
                    // 从背包拿走 3 食物 + 3 水（优先小瓶水，不够再拿中瓶/大瓶）
                    int foodTaken = 0, waterTaken = 0;
                    var inv = clicker.getInventory();
                    // 第一遍：只拿小瓶水
                    for (int slot = 0; slot < inv.getContainerSize() && waterTaken < 3; slot++) {
                        ItemStack stack = inv.getItem(slot);
                        if (stack.isEmpty()) continue;
                        if (waterFilter(level).test(stack) && isSmallWater(stack)) {
                            stack.shrink(1);
                            waterTaken++;
                        }
                    }
                    // 第二遍：拿中瓶/大瓶水补足
                    for (int slot = 0; slot < inv.getContainerSize() && waterTaken < 3; slot++) {
                        ItemStack stack = inv.getItem(slot);
                        if (stack.isEmpty()) continue;
                        if (waterFilter(level).test(stack)) {
                            stack.shrink(1);
                            waterTaken++;
                        }
                    }
                    // 拿食物（不分大小）
                    for (int slot = 0; slot < inv.getContainerSize() && foodTaken < 3; slot++) {
                        ItemStack stack = inv.getItem(slot);
                        if (stack.isEmpty()) continue;
                        if (foodFilter(level).test(stack)) {
                            stack.shrink(1);
                            foodTaken++;
                        }
                    }
                    addSanity(clicker, -5);
                    result(level, team, "gambler.r1_lose",
                            Component.literal(clicker.getGameProfile().getName()));
                }
            } else {
                result(level, team, "gambler.r2");
            }
            return true;
        });
        // ── 抉择：酒疯子 ──────────────────────────────────────────────
        choice("drunkard", Type.CHOICE, 8, (level, team, clicker, option) -> {
            if (option == 1) {
                ItemStack alc = takeFromInventory(clicker,
                        stack -> stack.is(ModItems.SIXTY_SECONDS_ALCOHOL));
                if (alc == null) {
                    fail(clicker, "no_alcohol");
                    return false;
                }
                float roll = level.getRandom().nextFloat();
                if (roll < 0.6F) {
                    team.doorHp = Math.max(1, team.doorHp - 20);
                    result(level, team, "drunkard.r1_door");
                } else if (roll < 0.9F) {
                    List<ItemStack> gained = new ArrayList<>();
                    gained.add(new ItemStack(ModItems.SIXTY_SECONDS_GLASS_SHARD, 4));
                    give(clicker, gained);
                    result(level, team, "drunkard.r1_shards", itemsText(gained));
                } else {
                    List<ItemStack> lost = loseFromHome(level, team, 1, foodFilter(level));
                    if (lost.isEmpty()) {
                        ItemStack food = takeFromInventory(clicker, foodFilter(level));
                        if (food != null) lost.add(food);
                    }
                    result(level, team, "drunkard.r1_snack", lostText(level, lost));
                }
            } else {
                team.doorHp = Math.max(1, team.doorHp - (int)(team.doorHp * 0.5F));
                result(level, team, "drunkard.r2");
            }
            return true;
        });
        // ── 抉择：妹妹外出 ────────────────────────────────────────────
        choice("sister_outside", Type.CHOICE, 8, (level, team, clicker, option) -> {
            if (option == 1) {
                if (team.shelterSpawn != null) {
                    BlockPos door = team.doorPos != null ? team.doorPos : team.shelterSpawn;
                    for (int dx = -3; dx <= 3; dx++) {
                        for (int dz = -3; dz <= 3; dz++) {
                            BlockPos test = door.offset(dx, 0, dz);
                            if (level.getBlockState(test).isAir() && level.getBlockState(test.above()).isAir()
                                    && level.getBlockState(test.below()).isSolid()) {
                                clicker.teleportTo(test.getX() + 0.5, test.getY(), test.getZ() + 0.5);
                                break;
                            }
                        }
                    }
                }
                team.sisterOutside = true;
                team.sisterUUID = clicker.getUUID(); // 记录妹妹的 UUID
                result(level, team, "sister_outside.r1",
                        Component.literal(clicker.getGameProfile().getName()));
            } else {
                result(level, team, "sister_outside.r2");
            }
            return true;
        });
        // ── 抉择：生锈捕兽夹 ──────────────────────────────────────────
        choice("bear_trap", Type.CHOICE, 8, (level, team, clicker, option) -> {
            if (option == 1) {
                if (level.getRandom().nextBoolean()) {
                    addHealth(clicker, -15);
                    result(level, team, "bear_trap.r1_free",
                            Component.literal(clicker.getGameProfile().getName()));
                } else {
                    addHealth(clicker, -30);
                    addPollution(clicker, 5);
                    result(level, team, "bear_trap.r1_stuck",
                            Component.literal(clicker.getGameProfile().getName()));
                }
            } else {
                result(level, team, "bear_trap.r2");
            }
            return true;
        });
        // ── 抉择：废弃广播塔 ──────────────────────────────────────────
        choice("radio_tower", Type.CHOICE, 8, (level, team, clicker, option) -> {
            if (option == 1) {
                if (level.getRandom().nextFloat() < 0.6F) {
                    List<ItemStack> gained = rollLoot(level, 1, "material");
                    give(clicker, gained);
                    teamSanity(level, team, 8);
                    result(level, team, "radio_tower.r1_good", itemsText(gained));
                } else {
                    addHealth(clicker, -20);
                    result(level, team, "radio_tower.r1_bad",
                            Component.literal(clicker.getGameProfile().getName()));
                }
            } else {
                result(level, team, "radio_tower.r2");
            }
            return true;
        });
        // ── 抉择：抢修变压器 ──────────────────────────────────────────
        choice("fix_transformer", Type.CHOICE, 8, (level, team, clicker, option) -> {
            if (option == 1) {
                ItemStack scrap1 = takeFromInventory(clicker,
                        stack -> stack.is(ModItems.SIXTY_SECONDS_SCRAP));
                ItemStack scrap2 = takeFromInventory(clicker,
                        stack -> stack.is(ModItems.SIXTY_SECONDS_SCRAP));
                ItemStack scrap3 = takeFromInventory(clicker,
                        stack -> stack.is(ModItems.SIXTY_SECONDS_SCRAP));
                if (scrap1 == null || scrap2 == null || scrap3 == null) {
                    // 放回已拿走的
                    if (scrap1 != null) clicker.getInventory().placeItemBackInInventory(scrap1);
                    if (scrap2 != null) clicker.getInventory().placeItemBackInInventory(scrap2);
                    fail(clicker, "no_scrap");
                    return false;
                }
                if (level.getRandom().nextBoolean()) {
                    team.powerEndTick = Math.max(team.powerEndTick, level.getGameTime()) + 20 * 120;
                    result(level, team, "fix_transformer.r1_good");
                } else {
                    addHealth(clicker, -15);
                    result(level, team, "fix_transformer.r1_bad",
                            Component.literal(clicker.getGameProfile().getName()));
                }
            } else {
                result(level, team, "fix_transformer.r2");
            }
            return true;
        });
        // ── 抉择：血脚印 ──────────────────────────────────────────────
        choice("bloody_trail", Type.CHOICE, 8, (level, team, clicker, option) -> {
            if (option == 1) {
                if (level.getRandom().nextFloat() < 0.4F) {
                    List<ItemStack> gained = rollLoot(level, 2, "medicine");
                    give(clicker, gained);
                    teamSanity(level, team, 5);
                    result(level, team, "bloody_trail.r1_good", itemsText(gained));
                } else {
                    addHealth(clicker, -25);
                    addPollution(clicker, 8);
                    result(level, team, "bloody_trail.r1_bad",
                            Component.literal(clicker.getGameProfile().getName()));
                }
            } else {
                result(level, team, "bloody_trail.r2");
            }
            return true;
        });

        // ── 交易：工具匠 ──────────────────────────────────────────────
        choice("tool_smith", Type.TRADE, 8, (level, team, clicker, option) -> {
            if (option == 1) {
                ItemStack scrap1 = takeFromInventory(clicker,
                        stack -> stack.is(ModItems.SIXTY_SECONDS_SCRAP));
                ItemStack scrap2 = takeFromInventory(clicker,
                        stack -> stack.is(ModItems.SIXTY_SECONDS_SCRAP));
                ItemStack scrap3 = takeFromInventory(clicker,
                        stack -> stack.is(ModItems.SIXTY_SECONDS_SCRAP));
                if (scrap1 == null || scrap2 == null || scrap3 == null) {
                    if (scrap1 != null) clicker.getInventory().placeItemBackInInventory(scrap1);
                    if (scrap2 != null) clicker.getInventory().placeItemBackInInventory(scrap2);
                    fail(clicker, "no_scrap");
                    return false;
                }
                List<ItemStack> gained = rollLoot(level, 1, "tool");
                give(clicker, gained);
                result(level, team, "tool_smith.r1", itemsText(gained));
            } else {
                result(level, team, "tool_smith.r2");
            }
            return true;
        });
        // ── 交易：药贩子 ──────────────────────────────────────────────
        choice("medicine_dealer", Type.TRADE, 8, (level, team, clicker, option) -> {
            if (option == 1) {
                SREPlayerMinigameTaskComponent tokens = SREPlayerMinigameTaskComponent.KEY.get(clicker);
                if (tokens.getTokens() < 8) {
                    fail(clicker, "no_coins", 8);
                    return false;
                }
                tokens.addTokens(-8);
                List<ItemStack> gained = rollLoot(level, 3, "medicine");
                give(clicker, gained);
                result(level, team, "medicine_dealer.r1", itemsText(gained));
            } else {
                result(level, team, "medicine_dealer.r2");
            }
            return true;
        });

        // ── 探险：化工厂废墟 ──────────────────────────────────────────
        choice("chemical_plant", Type.EXPEDITION, 7,
                (level, team, clicker, option) -> depart(level, team, clicker, option, "chemical_plant"));
        // ── 探险：地铁站台 ────────────────────────────────────────────
        choice("subway_station", Type.EXPEDITION, 8,
                (level, team, clicker, option) -> depart(level, team, clicker, option, "subway_station"));

        // ── 机遇：弹药补给箱 ──────────────────────────────────────────
        instant("ammo_cache", Type.FORTUNE, 8, (level, team) -> {
            List<ItemStack> gained = new ArrayList<>();
            gained.add(amplify(level, new ItemStack(ModItems.SIXTY_SECONDS_AMMO, 8)));
            giveToTeam(level, team, gained);
            result(level, team, "ammo_cache", itemsText(gained));
        });
        // ── 机遇：野生药草 ────────────────────────────────────────────
        instant("wild_herbs", Type.FORTUNE, 8, (level, team) -> {
            forEachMember(level, team, p -> {
                addSanity(p, 8);
                addHealth(p, 10);
            });
            result(level, team, "wild_herbs");
        });
        // ── 机遇：未爆弹拆解 ──────────────────────────────────────────
        instant("dud_shell", Type.FORTUNE, 6, (level, team) -> {
            List<ItemStack> gained = rollLoot(level, 3, "material");
            gained.add(amplify(level, new ItemStack(ModItems.SIXTY_SECONDS_GUNPOWDER_PACK, 1)));
            giveToTeam(level, team, gained);
            result(level, team, "dud_shell", itemsText(gained));
        });

        // ── 不幸：天花板渗漏 ──────────────────────────────────────────
        instant("ceiling_leak", Type.MISFORTUNE, 8, (level, team) -> {
            List<ItemStack> lost = loseFromHome(level, team, 1, categoryFilter(level, "material"));
            forEachMember(level, team, p -> addPollution(p, 6));
            result(level, team, "ceiling_leak", lostText(level, lost));
        });
        // ── 不幸：沙虫入侵 ────────────────────────────────────────────
        instant("roach_infest", Type.MISFORTUNE, 8, (level, team) -> {
            List<ItemStack> lost = loseFromHome(level, team, 1, foodFilter(level));
            lost.addAll(loseFromHome(level, team, 1, waterFilter(level)));
            teamSanity(level, team, -4);
            result(level, team, "roach_infest", lostText(level, lost));
        });
        // ── 不幸：地基塌陷 ────────────────────────────────────────────
        instant("foundation_crack", Type.MISFORTUNE, 8, (level, team) -> {
            team.doorHp = Math.max(1, team.doorHp - 15);
            ServerPlayer victim = randomMember(level, team);
            if (victim != null) addHealth(victim, -10);
            result(level, team, "foundation_crack",
                    victim == null ? Component.empty() : Component.literal(victim.getGameProfile().getName()));
        });

        // ── 日级修正：消毒防疫 ────────────────────────────────────────
        modifierEvent("sanitize", Type.MODIFIER, 8, "sick_chance", 0.5, (level, team) -> {
            result(level, team, "sanitize");
        });
        // ── 日级修正：弹药管制 ────────────────────────────────────────
        modifierEvent("ammo_discipline", Type.MODIFIER, 8, "gun_cooldown", 0.8, (level, team) -> {
            result(level, team, "ammo_discipline");
        });
        // ── 日级修正：燃料稀缺 ────────────────────────────────────────
        modifierEvent("fuel_shortage", Type.MISFORTUNE, 6, "drain_hunger", 1.25, (level, team) -> {
            applyModifier(team, "drain_thirst", 1.25);
            result(level, team, "fuel_shortage");
        });
        // ── 日级修正：大雾笼罩 ────────────────────────────────────────
        modifierEvent("heavy_fog", Type.MODIFIER, 8, "drain_pollution", 1.4, (level, team) -> {
            result(level, team, "heavy_fog");
        });

        // ══════════════════════════ 新增事件 2（83-107） ══════════════════════════

        // ── 抉择：地下拳场 ────────────────────────────────────────────
        choice("underground_fight", Type.CHOICE, 7, (level, team, clicker, option) -> {
            if (option == 1) {
                float roll = level.getRandom().nextFloat();
                if (roll < 0.45F) {
                    // 赢：5 代币 + san+5，健康 -10
                    SREPlayerMinigameTaskComponent tokens = SREPlayerMinigameTaskComponent.KEY.get(clicker);
                    tokens.addTokens(5);
                    addHealth(clicker, -10);
                    addSanity(clicker, 5);
                    result(level, team, "underground_fight.r1_win",
                            Component.literal(clicker.getGameProfile().getName()));
                } else if (roll < 0.8F) {
                    // 输：健康 -25，san-5
                    addHealth(clicker, -25);
                    addSanity(clicker, -5);
                    result(level, team, "underground_fight.r1_lose",
                            Component.literal(clicker.getGameProfile().getName()));
                } else {
                    // 重伤：健康 -40，san-10，生病
                    addHealth(clicker, -40);
                    addSanity(clicker, -10);
                    SixtySecondsSicknessSystem.makeSick(clicker);
                    result(level, team, "underground_fight.r1_critical",
                            Component.literal(clicker.getGameProfile().getName()));
                }
            } else {
                result(level, team, "underground_fight.r2");
            }
            return true;
        });
        // ── 抉择：黑市情报 ────────────────────────────────────────────
        choice("blackmarket_info", Type.CHOICE, 8, (level, team, clicker, option) -> {
            if (option == 1) {
                SREPlayerMinigameTaskComponent tokens = SREPlayerMinigameTaskComponent.KEY.get(clicker);
                if (tokens.getTokens() < 6) {
                    fail(clicker, "no_coins", 6);
                    return false;
                }
                tokens.addTokens(-6);
                if (level.getRandom().nextFloat() < 0.6F) {
                    List<ItemStack> gained = rollLoot(level, 2, "weapon");
                    gained.addAll(rollLoot(level, 4, "ammo"));
                    give(clicker, gained);
                    result(level, team, "blackmarket_info.r1_good", itemsText(gained));
                } else {
                    List<ItemStack> gained = rollLoot(level, 1, "material");
                    give(clicker, gained);
                    addSanity(clicker, -3);
                    result(level, team, "blackmarket_info.r1_bad", itemsText(gained));
                }
            } else {
                result(level, team, "blackmarket_info.r2");
            }
            return true;
        });
        // ── 抉择：下水道暗道 ──────────────────────────────────────────
        choice("sewer_passage", Type.CHOICE, 8, (level, team, clicker, option) -> {
            if (option == 1) {
                if (level.getRandom().nextBoolean()) {
                    List<ItemStack> gained = rollLoot(level, 4, "material");
                    gained.addAll(rollLoot(level, 2, "medicine"));
                    give(clicker, gained);
                    addPollution(clicker, 15);
                    result(level, team, "sewer_passage.r1_good", itemsText(gained));
                } else {
                    addHealth(clicker, -20);
                    addPollution(clicker, 10);
                    addSanity(clicker, -5);
                    SixtySecondsSicknessSystem.makeSick(clicker);
                    result(level, team, "sewer_passage.r1_bad",
                            Component.literal(clicker.getGameProfile().getName()));
                }
            } else {
                result(level, team, "sewer_passage.r2");
            }
            return true;
        });
        // ── 抉择：拾荒者营地 ──────────────────────────────────────────
        choice("scavenger_camp", Type.CHOICE, 8, (level, team, clicker, option) -> {
            if (option == 1) {
                ItemStack food = takeFromInventory(clicker, foodFilter(level));
                if (food == null) {
                    fail(clicker, "no_food");
                    return false;
                }
                float roll = level.getRandom().nextFloat();
                if (roll < 0.7F) {
                    List<ItemStack> gained = rollLoot(level, 3, "material");
                    gained.addAll(rollLoot(level, 1, "tool"));
                    give(clicker, gained);
                    result(level, team, "scavenger_camp.r1_good", itemsText(gained));
                } else {
                    addHealth(clicker, -15);
                    result(level, team, "scavenger_camp.r1_bad",
                            Component.literal(clicker.getGameProfile().getName()));
                }
            } else {
                result(level, team, "scavenger_camp.r2");
            }
            return true;
        });
        // ── 抉择：流浪钢琴师 ──────────────────────────────────────────
        choice("pianist", Type.CHOICE, 8, (level, team, clicker, option) -> {
            if (option == 1) {
                // 给 1 食物听一曲
                ItemStack food = takeFromInventory(clicker, foodFilter(level));
                if (food == null) {
                    fail(clicker, "no_food");
                    return false;
                }
                teamSanity(level, team, 12);
                result(level, team, "pianist.r1");
            } else {
                result(level, team, "pianist.r2");
            }
            return true;
        });
        // ── 抉择：废弃军车 ────────────────────────────────────────────
        choice("military_truck", Type.CHOICE, 8, (level, team, clicker, option) -> {
            if (option == 1) {
                if (level.getRandom().nextFloat() < 0.55F) {
                    List<ItemStack> gained = rollLoot(level, 2, "weapon");
                    gained.addAll(rollLoot(level, 6, "ammo"));
                    gained.addAll(rollLoot(level, 2, "medicine"));
                    give(clicker, gained);
                    result(level, team, "military_truck.r1_good", itemsText(gained));
                } else {
                    // 触发警报引来变异生物
                    addHealth(clicker, -20);
                    addPollution(clicker, 5);
                    addSanity(clicker, -8);
                    result(level, team, "military_truck.r1_bad",
                            Component.literal(clicker.getGameProfile().getName()));
                }
            } else {
                result(level, team, "military_truck.r2");
            }
            return true;
        });

        // ── 交易：种子商 ──────────────────────────────────────────────
        choice("seed_merchant", Type.TRADE, 8, (level, team, clicker, option) -> {
            if (option == 1) {
                SREPlayerMinigameTaskComponent tokens = SREPlayerMinigameTaskComponent.KEY.get(clicker);
                if (tokens.getTokens() < 5) {
                    fail(clicker, "no_coins", 5);
                    return false;
                }
                tokens.addTokens(-5);
                List<ItemStack> gained = rollLoot(level, 2, "food");
                gained.addAll(rollLoot(level, 1, "medicine"));
                gained.add(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .get(net.minecraft.resources.ResourceLocation.parse("watheextraitems:wild_rice_seeds"))));
                give(clicker, gained);
                result(level, team, "seed_merchant.r1", itemsText(gained));
            } else {
                result(level, team, "seed_merchant.r2");
            }
            return true;
        });
        // ── 交易：电池商 ──────────────────────────────────────────────
        choice("battery_dealer", Type.TRADE, 8, (level, team, clicker, option) -> {
            if (option == 1) {
                int scrapCount = 0;
                var inv = clicker.getInventory();
                for (int slot = 0; slot < inv.getContainerSize(); slot++) {
                    ItemStack stack = inv.getItem(slot);
                    if (!stack.isEmpty() && stack.is(ModItems.SIXTY_SECONDS_SCRAP)) {
                        scrapCount += stack.getCount();
                    }
                }
                if (scrapCount < 4) {
                    fail(clicker, "no_scrap");
                    return false;
                }
                // 拿走 4 个废料
                int taken = 0;
                for (int slot = 0; slot < inv.getContainerSize() && taken < 4; slot++) {
                    ItemStack stack = inv.getItem(slot);
                    if (!stack.isEmpty() && stack.is(ModItems.SIXTY_SECONDS_SCRAP)) {
                        int toTake = Math.min(4 - taken, stack.getCount());
                        stack.shrink(toTake);
                        taken += toTake;
                    }
                }
                List<ItemStack> gained = new ArrayList<>();
                gained.add(amplify(level, new ItemStack(ModItems.SIXTY_SECONDS_BATTERY, 2)));
                gained.addAll(rollLoot(level, 1, "material"));
                give(clicker, gained);
                result(level, team, "battery_dealer.r1", itemsText(gained));
            } else {
                result(level, team, "battery_dealer.r2");
            }
            return true;
        });

        // ── 探险：超市废墟 ────────────────────────────────────────────
        choice("ruined_supermarket", Type.EXPEDITION, 8,
                (level, team, clicker, option) -> depart(level, team, clicker, option, "ruined_supermarket"));
        // ── 探险：警察局 ──────────────────────────────────────────────
        choice("police_station", Type.EXPEDITION, 7,
                (level, team, clicker, option) -> depart(level, team, clicker, option, "police_station"));
        // ── 探险：地下车库 ────────────────────────────────────────────
        choice("underground_garage", Type.EXPEDITION, 8,
                (level, team, clicker, option) -> depart(level, team, clicker, option, "underground_garage"));

        // ── 机遇：蜂箱 ────────────────────────────────────────────────
        instant("beehive", Type.FORTUNE, 8, (level, team) -> {
            List<ItemStack> gained = rollLoot(level, 2, "food");
            giveToTeam(level, team, gained);
            teamSanity(level, team, 5);
            result(level, team, "beehive", itemsText(gained));
        });
        // ── 机遇：太阳能板 ────────────────────────────────────────────
        instant("solar_panel", Type.FORTUNE, 7, (level, team) -> {
            List<ItemStack> gained = rollLoot(level, 2, "material");
            gained.add(amplify(level, new ItemStack(ModItems.SIXTY_SECONDS_ELECTRONICS, 2)));
            giveToTeam(level, team, gained);
            result(level, team, "solar_panel", itemsText(gained));
        });
        // ── 机遇：旧书信 ──────────────────────────────────────────────
        instant("old_letters", Type.FORTUNE, 8, (level, team) -> {
            teamSanity(level, team, 10);
            result(level, team, "old_letters");
        });

        // ── 不幸：断水 ────────────────────────────────────────────────
        instant("water_cut", Type.MISFORTUNE, 8, (level, team) -> {
            forEachMember(level, team, p -> addThirst(p, -15));
            List<ItemStack> lost = loseFromHome(level, team, 1, waterFilter(level));
            result(level, team, "water_cut", lostText(level, lost));
        });
        // ── 不幸：通风管道破裂 ────────────────────────────────────────
        instant("vent_break", Type.MISFORTUNE, 8, (level, team) -> {
            forEachMember(level, team, p -> addPollution(p, 10));
            teamSanity(level, team, -4);
            result(level, team, "vent_break");
        });
        // ── 不幸：工具箱失窃 ──────────────────────────────────────────
        instant("tool_theft", Type.MISFORTUNE, 8, (level, team) -> {
            List<ItemStack> lost = loseFromHome(level, team, 1, toolFilter(level));
            teamSanity(level, team, -5);
            result(level, team, "tool_theft", lostText(level, lost));
        });

        // ── 劫掠：偷药 ────────────────────────────────────────────────
        choice("steal_medicine", Type.RAID, 7, (level, team, clicker, option) -> {
            if (option != 1) {
                result(level, team, "steal_medicine.r2");
                return true;
            }
            SixtySecondsState.TeamData victim = randomOtherTeam(level, team);
            if (victim == null) {
                result(level, team, "steal_medicine.r1_empty");
                teamSanity(level, team, -8);
                return true;
            }
            List<ItemStack> stolen = loseFromHome(level, victim, 2, medicineFilter(level));
            teamSanity(level, team, -12);
            if (stolen.isEmpty()) {
                result(level, team, "steal_medicine.r1_empty");
            } else {
                giveToTeam(level, team, copyAll(stolen));
                result(level, team, "steal_medicine.r1", itemsText(stolen));
            }
            Component note = Component.translatable(LANG + "steal_medicine.victim",
                    stolen.isEmpty() ? Component.translatable(LANG + "nothing") : itemsText(stolen))
                    .withStyle(ChatFormatting.RED);
            for (ServerPlayer p : onlineMembers(level, victim)) {
                p.displayClientMessage(note, false);
                addSanity(p, -5);
                p.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.AMBIENT, 0.8F, 0.6F);
            }
            return true;
        });

        // ── 日级修正：轮班哨 ──────────────────────────────────────────
        modifierEvent("shift_guard", Type.MODIFIER, 8, "night_attack_chance", 0.6, (level, team) -> {
            result(level, team, "shift_guard");
        });
        // ── 日级修正：暴雨 ────────────────────────────────────────────
        modifierEvent("heavy_rain", Type.MISFORTUNE, 8, "door_damage", 1.25, (level, team) -> {
            forEachMember(level, team, p -> addSanity(p, -4));
            result(level, team, "heavy_rain");
        });
        // ── 日级修正：物资征集 ────────────────────────────────────────
        modifierEvent("supply_levy", Type.MISFORTUNE, 7, "drain_hunger", 1.15, (level, team) -> {
            applyModifier(team, "drain_thirst", 1.15);
            List<ItemStack> lost = loseFromHome(level, team, 1, foodFilter(level));
            result(level, team, "supply_levy", lostText(level, lost));
        });
        // ── 日级修正：防毒培训 ────────────────────────────────────────
        modifierEvent("decon_drill", Type.MODIFIER, 8, "drain_pollution", 0.6, (level, team) -> {
            result(level, team, "decon_drill");
        });
    }

    // ══════════════════════════ 探险（出发 → 延迟结算） ══════════════════════════

    /** 探险出发：校验手电筒 → 登记延迟结算；option 2 = 放弃。 */
    private static boolean depart(ServerLevel level, SixtySecondsState.TeamData team, ServerPlayer clicker,
            int option, String eventId) {
        if (option != 1) {
            result(level, team, eventId + ".r2");
            return true;
        }
        if (!hasFlashlight(clicker)) {
            fail(clicker, "need_flashlight");
            return false;
        }
        LevelState st = STATE.computeIfAbsent(level, ignored -> new LevelState());
        st.expeditions.add(new Expedition(team.teamId, clicker.getUUID(), eventId,
                level.getGameTime() + SixtySecondsBalance.DAILY_EVENT_EXPEDITION_TICKS));
        result(level, team, eventId + ".depart", Component.literal(clicker.getGameProfile().getName()));
        return true;
    }

    /** 每条探险线路的结算参数。 */
    private record ExpeditionProfile(int randomRolls, String[] fixedCategories, int pollution,
            float hurtChance, int hurtAmount, float sickChance) {
    }

    private static ExpeditionProfile expeditionProfile(String eventId) {
        return switch (eventId) {
            // 塌陷隧道：高风险高回报
            case "collapsed_tunnel" -> new ExpeditionProfile(5, new String[0], 15, 0.4F, -30, 0.15F);
            // 屋顶水塔：定向产出水+材料，低风险
            case "water_tower" -> new ExpeditionProfile(0,
                    new String[] { "water", "water", "material", "material" }, 5, 0.2F, -15, 0F);
            // 废弃医院：药品为主 + 随机，中高风险
            case "abandoned_hospital" -> new ExpeditionProfile(2,
                    new String[] { "medicine", "medicine", "medicine" }, 12, 0.3F, -20, 0.2F);
            // 军械库废墟：武器弹药定向，中风险
            case "armory_ruins" -> new ExpeditionProfile(0,
                    new String[] { "weapon", "weapon" }, 8, 0.25F, -25, 0F);
            // 地图碎片：小规模探险
            case "map_fragment" -> new ExpeditionProfile(3, new String[0], 8, 0.15F, -15, 0F);
            // 化工厂废墟：材料为主，高风险
            case "chemical_plant" -> new ExpeditionProfile(2,
                    new String[] { "material", "material", "material" }, 18, 0.35F, -25, 0.25F);
            // 地铁站台：食物为主，低风险
            case "subway_station" -> new ExpeditionProfile(4,
                    new String[] { "food" }, 10, 0.2F, -18, 0.1F);
            // 超市废墟：食物+材料，中低风险
            case "ruined_supermarket" -> new ExpeditionProfile(3,
                    new String[] { "food", "food", "material" }, 8, 0.2F, -15, 0.1F);
            // 警察局：武器+弹药，中风险（额外 6 发子弹在 resolve 里处理）
            case "police_station" -> new ExpeditionProfile(1,
                    new String[] { "weapon", "weapon" }, 12, 0.3F, -20, 0F);
            // 地下车库：材料+工具，低风险
            case "underground_garage" -> new ExpeditionProfile(2,
                    new String[] { "material", "material", "tool" }, 6, 0.15F, -12, 0F);
            // 隐秘地窖：均衡
            default -> new ExpeditionProfile(3, new String[0], 10, 0.25F, -20, 0F);
        };
    }

    private static void resolveExpedition(ServerLevel level, Expedition exp) {
        SixtySecondsState.TeamData team = SixtySecondsState.get(level).teams.get(exp.teamId);
        if (team == null) {
            return;
        }
        ServerPlayer explorer = level.getPlayerByUUID(exp.explorer) instanceof ServerPlayer p
                && !GameUtils.isPlayerEliminated(p) ? p : randomMember(level, team);
        if (explorer == null) {
            return;
        }
        RandomSource random = level.getRandom();
        ExpeditionProfile profile = expeditionProfile(exp.eventId);
        List<ItemStack> gained = new ArrayList<>();
        String[] categories = { "food", "water", "medicine", "material", "tool", "weapon" };
        for (int i = 0; i < profile.randomRolls; i++) {
            gained.addAll(rollLoot(level, 1, categories[random.nextInt(categories.length)]));
        }
        gained.addAll(rollLoot(level, 1, profile.fixedCategories));
        // 军械库废墟额外弹药
        if ("armory_ruins".equals(exp.eventId)) {
            gained.add(amplify(level, new ItemStack(ModItems.SIXTY_SECONDS_AMMO, 4)));
        }
        // 警察局额外弹药
        if ("police_station".equals(exp.eventId)) {
            gained.add(amplify(level, new ItemStack(ModItems.SIXTY_SECONDS_AMMO, 6)));
        }
        give(explorer, gained);
        addPollution(explorer, profile.pollution);
        result(level, team, exp.eventId + ".r1_loot",
                Component.literal(explorer.getGameProfile().getName()), itemsText(gained));
        if (random.nextFloat() < profile.hurtChance) {
            addHealth(explorer, profile.hurtAmount);
            result(level, team, exp.eventId + ".r1_hurt",
                    Component.literal(explorer.getGameProfile().getName()));
        }
        if (random.nextFloat() < profile.sickChance) {
            SixtySecondsSicknessSystem.makeSick(explorer);
            result(level, team, exp.eventId + ".r1_sick",
                    Component.literal(explorer.getGameProfile().getName()));
        }
    }

    // ══════════════════════════ 家中容器 丢失/发放 物资 ══════════════════════════

    /**
     * 在家（住宅盒 + 避难所盒）的所有容器里随机抽走至多 {@code count} 个物品堆；
     * 排除物资箱（那是世界搜刮点，不是家当）。返回被抽走的物品（可能为空）。
     */
    private static List<ItemStack> loseFromHome(ServerLevel level, SixtySecondsState.TeamData team, int count,
            Predicate<ItemStack> filter) {
        record SlotRef(Container container, int slot) {
        }
        List<SlotRef> candidates = new ArrayList<>();
        for (Container container : homeContainers(level, team)) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (!stack.isEmpty() && filter.test(stack)) {
                    candidates.add(new SlotRef(container, slot));
                }
            }
        }
        List<ItemStack> lost = new ArrayList<>();
        RandomSource random = level.getRandom();
        for (int i = 0; i < count && !candidates.isEmpty(); i++) {
            SlotRef ref = candidates.remove(random.nextInt(candidates.size()));
            ItemStack stack = ref.container.getItem(ref.slot);
            if (stack.isEmpty()) {
                continue; // 双箱共享槽位等极端情况兜底
            }
            lost.add(stack.copy());
            ref.container.setItem(ref.slot, ItemStack.EMPTY);
            ref.container.setChanged();
        }
        return lost;
    }

    /** 收集家范围盒内的所有容器方块实体（按已加载区块扫描；物资箱除外）。 */
    private static List<Container> homeContainers(ServerLevel level, SixtySecondsState.TeamData team) {
        List<Container> found = new ArrayList<>();
        Set<BlockEntity> seen = new HashSet<>();
        scanContainers(level, team.residentialBox, found, seen);
        scanContainers(level, team.shelterBox, found, seen);
        return found;
    }

    private static void scanContainers(ServerLevel level, AABB box, List<Container> out, Set<BlockEntity> seen) {
        if (box == null) {
            return;
        }
        int minCx = SectionPos.blockToSectionCoord(Mth.floor(box.minX));
        int maxCx = SectionPos.blockToSectionCoord(Mth.floor(box.maxX));
        int minCz = SectionPos.blockToSectionCoord(Mth.floor(box.minZ));
        int maxCz = SectionPos.blockToSectionCoord(Mth.floor(box.maxZ));
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue; // 家附近常驻加载；未加载的边缘区块跳过即可
                }
                for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                    BlockPos pos = entry.getKey();
                    BlockEntity be = entry.getValue();
                    if (!box.contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                            || !(be instanceof Container container)
                            || be instanceof net.exmo.sre.sixtyseconds.content.block_entity.SupplyBoxBlockEntity
                            || be instanceof net.exmo.sre.sixtyseconds.content.block_entity.RandomSupplyBoxBlockEntity
                            || !seen.add(be)) {
                        continue;
                    }
                    out.add(container);
                }
            }
        }
    }

    /** 从共享 loot 表指定类别各抽 {@code perCategory} 件（产出经 {@link #amplify} 加量）。 */
    private static List<ItemStack> rollLoot(ServerLevel level, int perCategory, String... categories) {
        List<ItemStack> out = new ArrayList<>();
        var table = net.exmo.sre.sixtyseconds.loot.SixtySecondsLootStore.get(level);
        for (String category : categories) {
            for (int i = 0; i < perCategory; i++) {
                ItemStack stack = table.roll(category, level.getRandom());
                if (!stack.isEmpty()) {
                    out.add(amplify(level, stack));
                }
            }
        }
        return out;
    }

    /**
     * 事件产出加量：普通物资 ×{@link SixtySecondsBalance#DAILY_EVENT_LOOT_MULT}、
     * 废料 ×{@link SixtySecondsBalance#DAILY_EVENT_SCRAP_MULT}。
     * 不可堆叠物品（枪械/工具等）不加量；小数部分按概率进位（1×1.5 → 50% 概率给 2）。
     */
    private static ItemStack amplify(ServerLevel level, ItemStack stack) {
        if (stack.isEmpty() || stack.getMaxStackSize() <= 1) {
            return stack;
        }
        double mult = stack.is(ModItems.SIXTY_SECONDS_SCRAP)
                ? SixtySecondsBalance.DAILY_EVENT_SCRAP_MULT
                : SixtySecondsBalance.DAILY_EVENT_LOOT_MULT;
        double exact = stack.getCount() * mult;
        int count = (int) exact;
        if (level.getRandom().nextDouble() < exact - count) {
            count++;
        }
        stack.setCount(Mth.clamp(count, 1, stack.getMaxStackSize()));
        return stack;
    }

    private static void give(ServerPlayer player, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            // copy 后再发放，防止 placeItemBackInInventory 把原栈改空，
            // 导致后续 itemsText() 显示「空气」
            player.getInventory().placeItemBackInInventory(stack.copy());
        }
    }

    /** 发给随机在线成员（放不下自动掉脚下）。 */
    private static void giveToTeam(ServerLevel level, SixtySecondsState.TeamData team, List<ItemStack> stacks) {
        ServerPlayer receiver = randomMember(level, team);
        if (receiver != null) {
            give(receiver, stacks);
        }
    }

    private static List<ItemStack> copyAll(List<ItemStack> stacks) {
        List<ItemStack> out = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            out.add(stack.copy());
        }
        return out;
    }

    /** 食物判定：有原版食物组件，或在 loot 表 food 类别里。 */
    private static Predicate<ItemStack> foodFilter(ServerLevel level) {
        Set<String> ids = categoryIds(level, "food");
        return stack -> stack.has(DataComponents.FOOD) || ids.contains(itemId(stack));
    }

    /** 水判定：loot 表 water 类别（含原版药水瓶）。 */
    private static Predicate<ItemStack> waterFilter(ServerLevel level) {
        return categoryFilter(level, "water");
    }

    /** 工具判定：loot 表 tool 类别。 */
    private static Predicate<ItemStack> toolFilter(ServerLevel level) {
        return categoryFilter(level, "tool");
    }

    /** 药品判定：loot 表 medicine 类别。 */
    private static Predicate<ItemStack> medicineFilter(ServerLevel level) {
        return categoryFilter(level, "medicine");
    }

    /** 判断是否为小瓶水（物品 id 含 small/小瓶/small_water 等）。 */
    private static boolean isSmallWater(ItemStack stack) {
        String id = itemId(stack);
        // 常见小瓶水 ID 模式
        return id.contains("water_small") || id.contains("small_water")
                || id.contains("sixty_seconds_water_small")
                || id.contains("小瓶水");
    }

    /** 通用类别判定：物品 id 在 loot 表指定类别里。 */
    private static Predicate<ItemStack> categoryFilter(ServerLevel level, String category) {
        Set<String> ids = categoryIds(level, category);
        return stack -> ids.contains(itemId(stack));
    }

    private static Set<String> categoryIds(ServerLevel level, String category) {
        Set<String> ids = new HashSet<>();
        List<net.exmo.sre.sixtyseconds.loot.SixtySecondsLootTable.Entry> entries =
                net.exmo.sre.sixtyseconds.loot.SixtySecondsLootStore.get(level).categories.get(category);
        if (entries != null) {
            for (var entry : entries) {
                ids.add(entry.itemId);
            }
        }
        return ids;
    }

    private static String itemId(ItemStack stack) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** 从玩家背包里抽走一件满足条件的物品（1 个），返回被抽走的物品；没有返回 null。 */
    private static ItemStack takeFromInventory(ServerPlayer player, Predicate<ItemStack> filter) {
        if (player == null) {
            return null;
        }
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && filter.test(stack)) {
                ItemStack taken = stack.copyWithCount(1);
                stack.shrink(1);
                inventory.setChanged();
                return taken;
            }
        }
        return null;
    }

    private static boolean hasFlashlight(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(ModItems.SIXTY_SECONDS_FLASHLIGHT)) {
                return true;
            }
        }
        return false;
    }

    // ══════════════════════════ 队伍/状态小工具 ══════════════════════════

    private static List<ServerPlayer> onlineMembers(ServerLevel level, SixtySecondsState.TeamData team) {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID uuid : team.members) {
            if (level.getPlayerByUUID(uuid) instanceof ServerPlayer player
                    && !GameUtils.isPlayerEliminated(player)) {
                out.add(player);
            }
        }
        return out;
    }

    private static void forEachMember(ServerLevel level, SixtySecondsState.TeamData team,
            java.util.function.Consumer<ServerPlayer> action) {
        for (ServerPlayer player : onlineMembers(level, team)) {
            action.accept(player);
        }
    }

    private static ServerPlayer randomMember(ServerLevel level, SixtySecondsState.TeamData team) {
        List<ServerPlayer> members = onlineMembers(level, team);
        return members.isEmpty() ? null : members.get(level.getRandom().nextInt(members.size()));
    }

    private static SixtySecondsState.TeamData randomOtherTeam(ServerLevel level, SixtySecondsState.TeamData self) {
        List<SixtySecondsState.TeamData> others = new ArrayList<>();
        for (SixtySecondsState.TeamData team : SixtySecondsState.get(level).teams.values()) {
            if (team.teamId != self.teamId && !onlineMembers(level, team).isEmpty()) {
                others.add(team);
            }
        }
        return others.isEmpty() ? null : others.get(level.getRandom().nextInt(others.size()));
    }

    private static void teamSanity(ServerLevel level, SixtySecondsState.TeamData team, int delta) {
        forEachMember(level, team, p -> addSanity(p, delta));
    }

    private static void addSanity(ServerPlayer player, int delta) {
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
        if (stats.monster) {
            return;
        }
        int before = stats.sanity;
        stats.sanity = Mth.clamp(stats.sanity + delta, 0, stats.sanityMax);
        if (stats.sanity != before) {
            stats.sync();
        }
    }

    private static void addHunger(ServerPlayer player, int delta) {
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
        int before = stats.hunger;
        stats.hunger = Mth.clamp(stats.hunger + delta, 0, SixtySecondsStatsComponent.MAX);
        if (stats.hunger != before) {
            stats.sync();
        }
    }

    private static void addThirst(ServerPlayer player, int delta) {
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
        int before = stats.thirst;
        stats.thirst = Mth.clamp(stats.thirst + delta, 0, SixtySecondsStatsComponent.MAX);
        if (stats.thirst != before) {
            stats.sync();
        }
    }

    private static void addPollution(ServerPlayer player, int delta) {
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
        int before = stats.pollution;
        stats.pollution = Mth.clamp(stats.pollution + delta, 0, SixtySecondsStatsComponent.MAX);
        if (stats.pollution != before) {
            stats.sync();
        }
    }

    /** 事件伤害保底不打空（min 5），避免绕过倒地流程凭空触发死亡。 */
    private static void addHealth(ServerPlayer player, int delta) {
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
        if (stats.downed || stats.monster) {
            return;
        }
        int before = stats.health;
        stats.health = Mth.clamp(stats.health + delta, 5, SixtySecondsStatsComponent.MAX);
        if (stats.health != before) {
            stats.sync();
        }
    }

    // ══════════════════════════ 播报小工具 ══════════════════════════

    private static void result(ServerLevel level, SixtySecondsState.TeamData team, String keySuffix,
            Object... args) {
        msgTeam(level, team, Component.translatable(LANG + keySuffix + (keySuffix.contains(".") ? "" : ".result"),
                args).withStyle(ChatFormatting.YELLOW));
    }

    private static void msgTeam(ServerLevel level, SixtySecondsState.TeamData team, Component message) {
        for (ServerPlayer player : onlineMembers(level, team)) {
            player.displayClientMessage(message, false);
        }
    }

    private static void fail(ServerPlayer clicker, String keySuffix, Object... args) {
        if (clicker != null) {
            clicker.displayClientMessage(Component.translatable(LANG + keySuffix, args)
                    .withStyle(ChatFormatting.RED), false);
        }
    }

    /** 物品清单：`名称×数量、名称×数量`。 */
    private static Component itemsText(List<ItemStack> stacks) {
        if (stacks.isEmpty()) {
            return Component.translatable(LANG + "nothing");
        }
        MutableComponent out = Component.empty();
        for (int i = 0; i < stacks.size(); i++) {
            if (i > 0) {
                out.append(Component.literal("、"));
            }
            ItemStack stack = stacks.get(i);
            out.append(stack.getHoverName());
            if (stack.getCount() > 1) {
                out.append(Component.literal("×" + stack.getCount()));
            }
        }
        return out;
    }

    /** 丢失清单（家里没东西可丢时显示占位文案）。 */
    private static Component lostText(ServerLevel level, List<ItemStack> lost) {
        return lost.isEmpty() ? Component.translatable(LANG + "nothing_lost") : itemsText(lost);
    }
}
