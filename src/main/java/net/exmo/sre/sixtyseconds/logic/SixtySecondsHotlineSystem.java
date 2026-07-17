package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsEventSystem;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.agmas.noellesroles.init.ModBlocks;
import org.agmas.noellesroles.init.ModItems;

import java.util.*;

/**
 * 热线电话系统 - 快递/购物/救援三大热线，支持多步交互、超时挂断、队列投递。
 */
public final class SixtySecondsHotlineSystem {
    private static final Map<ServerLevel, HotlineData> HOTLINE_DATA = new WeakHashMap<>();
    private static final long TIMEOUT_MS = 40_000L;
    /** 待处理的快递/购物/救援队列：teamId → 待投递物品 */
    private static final Map<ServerLevel, Map<Integer, List<ItemStack>>> PENDING_DELIVERIES = new WeakHashMap<>();

    private SixtySecondsHotlineSystem() {}

    // ═══════════════════════════════════════════════════════════
    // 每日生成
    // ═══════════════════════════════════════════════════════════

    public static void generateDailyHotlines(ServerLevel level) {
        HotlineData data = HOTLINE_DATA.computeIfAbsent(level, k -> new HotlineData());
        data.dailyHotlines.clear();
        data.dialedToday.clear();
        data.playersDialedToday.clear();
        data.activeCall = null;
        data.shopItems.clear();

        var rand = level.getRandom();
        // 所有热线均为概率出现，没有任何热线是必出的
        if (rand.nextDouble() < 0.45) // 快递热线 45%
            data.dailyHotlines.add(new HotlineEntry(pad6(rand.nextInt(1_000_000)), HotlineType.EXPRESS));
        if (rand.nextDouble() < 0.45) // 购物热线 45%
            data.dailyHotlines.add(new HotlineEntry(pad6(rand.nextInt(1_000_000)), HotlineType.SHOP));
        if (rand.nextDouble() < 0.25) // 救援热线 25%
            data.dailyHotlines.add(new HotlineEntry(pad6(rand.nextInt(1_000_000)), HotlineType.RESCUE));
        if (rand.nextDouble() < 0.55) // 情报热线 55%
            data.dailyHotlines.add(new HotlineEntry(pad6(rand.nextInt(1_000_000)), HotlineType.INTEL));
        if (rand.nextDouble() < 0.45) // 天气预报 45%
            data.dailyHotlines.add(new HotlineEntry(pad6(rand.nextInt(1_000_000)), HotlineType.WEATHER));
        if (rand.nextDouble() < 0.30) // 心理辅导 30%
            data.dailyHotlines.add(new HotlineEntry(pad6(rand.nextInt(1_000_000)), HotlineType.COUNSEL));
        if (rand.nextDouble() < 0.30) // 雇佣 30%
            data.dailyHotlines.add(new HotlineEntry(pad6(rand.nextInt(1_000_000)), HotlineType.HIRE));
        if (rand.nextDouble() < 0.20) // 黑市 20%
            data.dailyHotlines.add(new HotlineEntry(pad6(rand.nextInt(1_000_000)), HotlineType.BLACK_MARKET));
        if (rand.nextDouble() < 0.15) // 回收 15%（小概率）
            data.dailyHotlines.add(new HotlineEntry(pad6(rand.nextInt(1_000_000)), HotlineType.RECYCLE));
        if (rand.nextDouble() < 0.15) // 贫困救济 15%（小概率）
            data.dailyHotlines.add(new HotlineEntry(pad6(rand.nextInt(1_000_000)), HotlineType.POVERTY_RELIEF));
        if (rand.nextDouble() < 0.35) // 匿名举报 35%
            data.dailyHotlines.add(new HotlineEntry(pad6(rand.nextInt(1_000_000)), HotlineType.REPORT));

        // 预生成购物商品（仅在购物热线出现时才需要，但提前生成不影响性能）
        data.shopItems = generateShopItems(level);
    }

    // ═══════════════════════════════════════════════════════════
    // 拨号
    // ═══════════════════════════════════════════════════════════

    public static String handleDial(ServerPlayer player, String number) {
        ServerLevel level = player.serverLevel();
        HotlineData data = HOTLINE_DATA.get(level);
        if (data == null) return "invalid";

        // 每天只能拨打一次热线（任意号码）
        if (data.playersDialedToday.contains(player.getUUID())) return "daily_limit";
        if (data.dialedToday.contains(number)) return "already_dialed";

        for (HotlineEntry entry : data.dailyHotlines) {
            if (entry.number.equals(number)) {
                data.dialedToday.add(number);
                data.playersDialedToday.add(player.getUUID());
                data.activeCall = new ActiveCall(player.getUUID(), entry.type, System.currentTimeMillis(), 0);
                return "connected_" + entry.type.name().toLowerCase();
            }
        }
        return "invalid";
    }

    /** 获取当前呼入的热线类型（用于无 state 传递时判断） */
    public static HotlineType getActiveCallType(ServerPlayer player) {
        HotlineData data = HOTLINE_DATA.get(player.serverLevel());
        if (data == null || data.activeCall == null) return null;
        if (!data.activeCall.playerId.equals(player.getUUID())) return null;
        return data.activeCall.type;
    }

    // ═══════════════════════════════════════════════════════════
    // 超时检查 & 挂断
    // ═══════════════════════════════════════════════════════════

    public static void tick(ServerLevel level) {
        if (level.getGameTime() % 100 != 0) return; // 每5秒检查一次
        HotlineData data = HOTLINE_DATA.get(level);
        if (data == null || data.activeCall == null) return;
        if (System.currentTimeMillis() - data.activeCall.startTime > TIMEOUT_MS) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(data.activeCall.playerId);
            if (player != null)
                player.displayClientMessage(tl("hotline.timeout"), false);
            data.activeCall = null;
        }
    }

    public static boolean isTimeout(ServerPlayer player) {
        HotlineData data = HOTLINE_DATA.get(player.serverLevel());
        if (data == null || data.activeCall == null) return true;
        if (!data.activeCall.playerId.equals(player.getUUID())) return true;
        return System.currentTimeMillis() - data.activeCall.startTime > TIMEOUT_MS;
    }

    // ═══════════════════════════════════════════════════════════
    // 快递热线
    // ═══════════════════════════════════════════════════════════

    public static void handleExpressGreeting(ServerPlayer player) {
        player.displayClientMessage(tl("hotline.express.greeting").withStyle(ChatFormatting.GOLD), false);
        player.displayClientMessage(
            btn("hotline.express.send", "/sre:60s hotline express send", ChatFormatting.GREEN), false);
        player.displayClientMessage(
            btn("hotline.express.cancel", "/sre:60s hotline express cancel", ChatFormatting.GRAY), false);
    }

    public static void handleExpressCancel(ServerPlayer player) {
        if (isTimeout(player)) { timeout(player); return; }
        player.displayClientMessage(tl("hotline.express.bye").withStyle(ChatFormatting.GOLD), false);
        hangup(player);
    }

    public static void handleExpressSend(ServerPlayer player) {
        if (isTimeout(player)) { timeout(player); return; }
        ServerLevel level = player.serverLevel();
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        player.displayClientMessage(tl("hotline.express.select_team").withStyle(ChatFormatting.GOLD), false);

        // 每行5个队伍
        List<Integer> teamIds = new ArrayList<>(data.teams.keySet());
        Collections.sort(teamIds);
        for (int i = 0; i < teamIds.size(); i += 5) {
            var line = Component.empty();
            for (int j = i; j < Math.min(i + 5, teamIds.size()); j++) {
                int tid = teamIds.get(j);
                line.append(Component.literal("[" + tid + "] ")
                    .setStyle(Style.EMPTY
                        .withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                            "/sre:60s hotline express team " + tid))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.translatable("message.noellesroles.hotline.express.team_hover", tid)))));
            }
            player.displayClientMessage(line, false);
        }
        // 更新step
        getData(level).activeCall = new ActiveCall(player.getUUID(), HotlineType.EXPRESS, System.currentTimeMillis(), 1);
    }

    public static void handleExpressTeam(ServerPlayer player, int targetTeam) {
        if (isTimeout(player)) { timeout(player); return; }
        HotlineData data = getData(player.serverLevel());
        data.pendingCourierTeam = targetTeam;
        player.displayClientMessage(tl("hotline.express.instructions").withStyle(ChatFormatting.GOLD), false);
        player.displayClientMessage(tl("hotline.express.package_reminder").withStyle(ChatFormatting.YELLOW), false);
        hangup(player);
    }

    // ═══════════════════════════════════════════════════════════
    // 购物热线
    // ═══════════════════════════════════════════════════════════

    public static void handleShopGreeting(ServerPlayer player) {
        if (isTimeout(player)) { timeout(player); return; }
        ServerLevel level = player.serverLevel();
        HotlineData data = getData(level);
        List<ShopItem> items = data.shopItems;

        player.displayClientMessage(tl("hotline.shop.greeting").withStyle(ChatFormatting.GOLD), false);
        player.displayClientMessage(Component.translatable("message.noellesroles.hotline.shop.title")
            .withStyle(ChatFormatting.YELLOW), false);

        for (int i = 0; i < items.size(); i++) {
            ShopItem si = items.get(i);
            String name = si.item.getHoverName().getString();
            Component line = Component.literal("[" + (i + 1) + "] " + name + " x" + si.item.getCount()
                + " - " + si.price + " ").withStyle(ChatFormatting.WHITE)
                .append(Component.translatable("message.noellesroles.hotline.shop.coin").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("  "))
                .append(Component.translatable("message.noellesroles.hotline.shop.buy")
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                            "/sre:60s hotline shop buy " + i))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.translatable("message.noellesroles.hotline.shop.buy_hover")))));
            player.displayClientMessage(line, false);
        }
        player.displayClientMessage(
            btn("hotline.shop.cancel", "/sre:60s hotline shop cancel", ChatFormatting.GRAY), false);
    }

    public static void handleShopBuy(ServerPlayer player, int index) {
        if (isTimeout(player)) { timeout(player); return; }
        HotlineData data = getData(player.serverLevel());
        if (index < 0 || index >= data.shopItems.size()) return;

        ShopItem si = data.shopItems.get(index);
        if (data.shopPurchased.contains(index)) {
            player.displayClientMessage(tl("hotline.shop.already_bought").withStyle(ChatFormatting.RED), false);
            return;
        }

        // 购物商品加入队列，次日清晨扣费投递
        data.shopPurchased.add(index);
        data.pendingShopPurchases.computeIfAbsent(
            SixtySecondsStatsComponent.KEY.get(player).teamId,
            k -> new ArrayList<>()).add(si);

        player.displayClientMessage(tl("hotline.shop.confirmed").withStyle(ChatFormatting.GREEN), false);
        player.displayClientMessage(tl("hotline.shop.put_coin").withStyle(ChatFormatting.YELLOW), false);
    }

    public static void handleShopCancel(ServerPlayer player) {
        player.displayClientMessage(tl("hotline.shop.bye").withStyle(ChatFormatting.GOLD), false);
        hangup(player);
    }

    // ═══════════════════════════════════════════════════════════
    // 救援热线
    // ═══════════════════════════════════════════════════════════

    public static void handleRescueGreeting(ServerPlayer player) {
        if (isTimeout(player)) { timeout(player); return; }
        player.displayClientMessage(tl("hotline.rescue.greeting").withStyle(ChatFormatting.GOLD), false);
        player.displayClientMessage(
            btn("hotline.rescue.request", "/sre:60s hotline rescue request", ChatFormatting.GREEN), false);
        player.displayClientMessage(
            btn("hotline.rescue.cancel", "/sre:60s hotline rescue cancel", ChatFormatting.GRAY), false);
    }

    public static void handleRescueCancel(ServerPlayer player) {
        player.displayClientMessage(tl("hotline.rescue.bye").withStyle(ChatFormatting.GOLD), false);
        hangup(player);
    }

    public static void handleRescueRequest(ServerPlayer player) {
        if (isTimeout(player)) { timeout(player); return; }
        int teamId = SixtySecondsStatsComponent.KEY.get(player).teamId;

        // 评估全队最差状态
        List<ServerPlayer> teammates = new ArrayList<>();
        for (ServerPlayer p : player.serverLevel().players()) {
            if (SixtySecondsStatsComponent.KEY.get(p).teamId == teamId)
                teammates.add(p);
        }

        ItemStack aid = assessRescueAid(teammates);
        if (aid.isEmpty()) {
            player.displayClientMessage(tl("hotline.rescue.no_need").withStyle(ChatFormatting.GRAY), false);
            hangup(player);
            return;
        }

        // 加入救援队列
        getData(player.serverLevel()).pendingRescueRequests
            .computeIfAbsent(teamId, k -> new ArrayList<>()).add(aid);

        player.displayClientMessage(tl("hotline.rescue.bye").withStyle(ChatFormatting.GOLD), false);
        hangup(player);
    }

    private static ItemStack assessRescueAid(List<ServerPlayer> teammates) {
        if (teammates.isEmpty()) return ItemStack.EMPTY;

        // 优先级：生病 > 健康 > 理智 > 污染 > 口渴 > 饥饿
        for (ServerPlayer p : teammates) {
            SixtySecondsStatsComponent s = SixtySecondsStatsComponent.KEY.get(p);
            if (s.sick) return new ItemStack(ModItems.SIXTY_SECONDS_ANTIBIOTICS, 1);
        }

        // 找各维度最差的
        int worstHealth = Integer.MAX_VALUE, worstSanity = Integer.MAX_VALUE;
        int worstPollution = 0, worstThirst = Integer.MAX_VALUE, worstHunger = Integer.MAX_VALUE;
        for (ServerPlayer p : teammates) {
            SixtySecondsStatsComponent s = SixtySecondsStatsComponent.KEY.get(p);
            worstHealth = Math.min(worstHealth, s.health);
            worstSanity = Math.min(worstSanity, s.sanity);
            worstPollution = Math.max(worstPollution, s.pollution);
            worstThirst = Math.min(worstThirst, s.thirst);
            worstHunger = Math.min(worstHunger, s.hunger);
        }

        int max = SixtySecondsStatsComponent.MAX;
        if (worstHealth < max * 0.4) return new ItemStack(ModItems.SIXTY_SECONDS_PAINKILLERS, 2);
        if (worstPollution > max * 0.6) return new ItemStack(ModItems.SIXTY_SECONDS_PURIFICATION_TABLET, 2);
        if (worstSanity < max * 0.3) return new ItemStack(ModItems.SIXTY_SECONDS_SEDATIVE, 2);
        if (worstThirst < max * 0.3) return new ItemStack(ModItems.SIXTY_SECONDS_WATER_HIGH, 2);
        if (worstHunger < max * 0.3) return new ItemStack(ModItems.SIXTY_SECONDS_CANNED_FOOD, 2);

        // 给点基础食品
        return new ItemStack(ModItems.SIXTY_SECONDS_CANNED_FOOD, 2);
    }

    // ═══════════════════════════════════════════════════════════
    // 情报热线
    // ═══════════════════════════════════════════════════════════

    public static void handleIntelGreeting(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        long aliveCount = level.players().stream()
                .filter(p -> !GameUtils.isPlayerEliminated(p)).count();
        String weatherKey = SixtySecondsEventSystem.activeEventKey(level);
        String weatherDesc = weatherKey != null
                ? Component.translatable(weatherKey).getString()
                : Component.translatable("message.noellesroles.sixty_seconds.weather_clear").getString();

        player.displayClientMessage(tl("hotline.intel.greeting").withStyle(ChatFormatting.GOLD), false);
        player.displayClientMessage(Component.translatable(
                "message.noellesroles.hotline.intel.report",
                data.dayNumber, data.teams.size(), aliveCount, weatherDesc)
                .withStyle(ChatFormatting.YELLOW), false);
        hangup(player);
    }

    // ═══════════════════════════════════════════════════════════
    // 天气预报热线
    // ═══════════════════════════════════════════════════════════

    public static void handleWeatherGreeting(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        var rand = level.getRandom();
        SixtySecondsState.Data data = SixtySecondsState.get(level);

        // 当前天气
        String currentKey = SixtySecondsEventSystem.activeEventKey(level);
        String currentDesc = currentKey != null
                ? Component.translatable(currentKey).getString()
                : Component.translatable("message.noellesroles.sixty_seconds.weather_clear").getString();

        // 未来3天预报：如果尚未安排，则随机生成并存入调度系统
        for (int i = 1; i <= 3; i++) {
            int targetDay = data.dayNumber + i;
            if (SixtySecondsEventSystem.getScheduledForDay(level, targetDay) == null) {
                // 50%概率安排一个天气事件，50%概率晴朗
                if (rand.nextDouble() < 0.5) {
                    SixtySecondsEventSystem.EventType[] pool =
                            SixtySecondsEventSystem.FORECASTABLE_TYPES;
                    SixtySecondsEventSystem.EventType type =
                            pool[rand.nextInt(pool.length)];
                    SixtySecondsEventSystem.scheduleForDay(level, targetDay, type);
                } else {
                    SixtySecondsEventSystem.scheduleForDay(level, targetDay, null);
                }
            }
        }

        // 读取已安排的预报
        String[] forecastNames = new String[3];
        for (int i = 0; i < 3; i++) {
            String key = SixtySecondsEventSystem.getScheduledEventKey(level, data.dayNumber + i + 1);
            forecastNames[i] = key != null
                    ? Component.translatable(key).getString()
                    : Component.translatable("message.noellesroles.sixty_seconds.weather_clear").getString();
        }

        player.displayClientMessage(tl("hotline.weather.greeting").withStyle(ChatFormatting.GOLD), false);
        player.displayClientMessage(Component.translatable(
                "message.noellesroles.hotline.weather.current", currentDesc)
                .withStyle(ChatFormatting.YELLOW), false);
        player.displayClientMessage(Component.translatable(
                "message.noellesroles.hotline.weather.forecast",
                data.dayNumber + 1, forecastNames[0],
                data.dayNumber + 2, forecastNames[1],
                data.dayNumber + 3, forecastNames[2])
                .withStyle(ChatFormatting.AQUA), false);
        hangup(player);
    }

    // ═══════════════════════════════════════════════════════════
    // 心理辅导热线
    // ═══════════════════════════════════════════════════════════

    public static void handleCounselGreeting(ServerPlayer player) {
        if (isTimeout(player)) { timeout(player); return; }
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
        HotlineData data = getData(player.serverLevel());
        if (data.counselUsedToday.contains(player.getUUID())) {
            player.displayClientMessage(tl("hotline.counsel.already_used").withStyle(ChatFormatting.RED), false);
            hangup(player);
            return;
        }
        stats.sanity = Math.min(stats.sanityMax, stats.sanity + 15);
        stats.sync();
        data.counselUsedToday.add(player.getUUID());
        player.displayClientMessage(tl("hotline.counsel.greeting").withStyle(ChatFormatting.GOLD), false);
        player.displayClientMessage(tl("hotline.counsel.effect").withStyle(ChatFormatting.GREEN), false);
        hangup(player);
    }

    // ═══════════════════════════════════════════════════════════
    // 雇佣热线
    // ═══════════════════════════════════════════════════════════

    public static void handleHireGreeting(ServerPlayer player) {
        if (isTimeout(player)) { timeout(player); return; }
        player.displayClientMessage(tl("hotline.hire.greeting").withStyle(ChatFormatting.GOLD), false);
        player.displayClientMessage(
                btn("hotline.hire.confirm", "/sre:60s hotline hire confirm", ChatFormatting.GREEN), false);
        player.displayClientMessage(
                btn("hotline.hire.cancel", "/sre:60s hotline hire cancel", ChatFormatting.GRAY), false);
    }

    public static void handleHireConfirm(ServerPlayer player) {
        if (isTimeout(player)) { timeout(player); return; }
        int teamId = SixtySecondsStatsComponent.KEY.get(player).teamId;
        int cost = 8; // 雇佣费用8游戏币
        int coins = countCoinsInMailbox(player.serverLevel(), teamId);
        if (coins < cost) {
            player.displayClientMessage(tl("hotline.hire.insufficient").withStyle(ChatFormatting.RED), false);
            hangup(player);
            return;
        }
        removeCoinsFromMailbox(player.serverLevel(), teamId, cost);
        // 修复大门耐久15点
        SixtySecondsState.Data stateData = SixtySecondsState.get(player.serverLevel());
        SixtySecondsState.TeamData team = stateData.teams.get(teamId);
        if (team != null) {
            boolean wasBroken = team.doorHp <= 0;
            team.doorHp = Math.min(team.doorMaxHp, team.doorHp + 15);
            if (wasBroken && team.doorHp > 0) {
                team.doorBroken = false;
            }
        }
        // 随机一件近战武器，次日发放
        var rand = player.serverLevel().getRandom();
        ItemStack weapon = switch (rand.nextInt(6)) {
            case 0 -> new ItemStack(ModItems.SIXTY_SECONDS_KNIFE, 1);
            case 1 -> new ItemStack(ModItems.SIXTY_SECONDS_FIRE_AXE, 1);
            case 2 -> new ItemStack(ModItems.SIXTY_SECONDS_HATCHET, 1);
            case 3 -> new ItemStack(ModItems.SIXTY_SECONDS_SPIKED_BAT, 1);
            case 4 -> new ItemStack(ModItems.SIXTY_SECONDS_MACHETE, 1);
            default -> new ItemStack(ModItems.SIXTY_SECONDS_PIPE, 1);
        };
        scheduleDelivery(player.serverLevel(), teamId, weapon);
        player.displayClientMessage(tl("hotline.hire.confirmed").withStyle(ChatFormatting.GREEN), false);
        hangup(player);
    }

    public static void handleHireCancel(ServerPlayer player) {
        player.displayClientMessage(tl("hotline.hire.bye").withStyle(ChatFormatting.GOLD), false);
        hangup(player);
    }

    // ═══════════════════════════════════════════════════════════
    // 黑市热线
    // ═══════════════════════════════════════════════════════════

    public static void handleBlackMarketGreeting(ServerPlayer player) {
        if (isTimeout(player)) { timeout(player); return; }
        player.displayClientMessage(tl("hotline.black_market.greeting").withStyle(ChatFormatting.GOLD), false);
        player.displayClientMessage(
                btn("hotline.black_market.buy", "/sre:60s hotline black_market buy", ChatFormatting.GREEN), false);
        player.displayClientMessage(
                btn("hotline.black_market.cancel", "/sre:60s hotline black_market cancel", ChatFormatting.GRAY), false);
    }

    public static void handleBlackMarketBuy(ServerPlayer player) {
        if (isTimeout(player)) { timeout(player); return; }
        int teamId = SixtySecondsStatsComponent.KEY.get(player).teamId;
        int cost = 8;
        int coins = countCoinsInMailbox(player.serverLevel(), teamId);
        if (coins < cost) {
            player.displayClientMessage(tl("hotline.black_market.insufficient").withStyle(ChatFormatting.RED), false);
            hangup(player);
            return;
        }
        removeCoinsFromMailbox(player.serverLevel(), teamId, cost);
        // 随机违禁品物资包，次日发放
        ItemStack contraband;
        var rand = player.serverLevel().getRandom();
        if (rand.nextDouble() < 0.08) {
            contraband = new ItemStack(ModItems.SIXTY_SECONDS_PRECIOUS_PARTS, 2);
        } else {
            contraband = switch (rand.nextInt(11)) {
                case 0 -> new ItemStack(ModItems.SIXTY_SECONDS_GUNPOWDER_PACK, 4);
                case 1 -> new ItemStack(ModItems.SIXTY_SECONDS_GEAR, 4);
                case 2 -> new ItemStack(ModItems.SIXTY_SECONDS_CHEMICALS, 4);
                case 3 -> new ItemStack(ModItems.SIXTY_SECONDS_ELECTRONICS, 4);
                case 4 -> new ItemStack(ModItems.SIXTY_SECONDS_SCRAP, 6);
                case 5 -> new ItemStack(ModItems.SIXTY_SECONDS_GLASS_SHARD, 6);
                case 6 -> new ItemStack(ModItems.SIXTY_SECONDS_WIRE, 4);
                case 7 -> new ItemStack(Items.BONE_MEAL, 4);
                case 8 -> new ItemStack(ModItems.SIXTY_SECONDS_SCRAP_METAL, 6);
                case 9 -> new ItemStack(Items.LEATHER, 4);
                default -> new ItemStack(Items.PAPER, 4);
            };
        }
        scheduleDelivery(player.serverLevel(), teamId, contraband);
        player.displayClientMessage(tl("hotline.black_market.confirmed").withStyle(ChatFormatting.GREEN), false);
        hangup(player);
    }

    public static void handleBlackMarketCancel(ServerPlayer player) {
        player.displayClientMessage(tl("hotline.black_market.bye").withStyle(ChatFormatting.GOLD), false);
        hangup(player);
    }

    // ═══════════════════════════════════════════════════════════
    // 回收热线
    // ═══════════════════════════════════════════════════════════

    public static void handleRecycleGreeting(ServerPlayer player) {
        if (isTimeout(player)) { timeout(player); return; }
        player.displayClientMessage(tl("hotline.recycle.greeting").withStyle(ChatFormatting.GOLD), false);
        player.displayClientMessage(
                btn("hotline.recycle.confirm", "/sre:60s hotline recycle confirm", ChatFormatting.GREEN), false);
        player.displayClientMessage(
                btn("hotline.recycle.cancel", "/sre:60s hotline recycle cancel", ChatFormatting.GRAY), false);
    }

    public static void handleRecycleConfirm(ServerPlayer player) {
        if (isTimeout(player)) { timeout(player); return; }
        int teamId = SixtySecondsStatsComponent.KEY.get(player).teamId;
        // 从邮箱中回收废料，3废料 = 1游戏币
        List<BlockPos> boxes = SixtySecondsNewspaper.getMailboxRegistry(player.serverLevel()).get(teamId);
        if (boxes == null || boxes.isEmpty()) {
            player.displayClientMessage(tl("hotline.recycle.no_mailbox").withStyle(ChatFormatting.RED), false);
            hangup(player);
            return;
        }
        int totalScrap = 0;
        for (BlockPos pos : boxes) {
            BlockEntity be = player.serverLevel().getBlockEntity(pos);
            if (be instanceof net.exmo.sre.sixtyseconds.content.block_entity.SixtySecondsMailboxBlockEntity mb) {
                for (int i = 0; i < mb.getContainerSize(); i++) {
                    ItemStack s = mb.getItem(i);
                    if (s.is(ModItems.SIXTY_SECONDS_SCRAP)) {
                        totalScrap += s.getCount();
                        mb.setItem(i, ItemStack.EMPTY);
                    }
                }
            }
        }
        int coins = totalScrap / 6;
        if (coins > 0) {
            scheduleDelivery(player.serverLevel(), teamId,
                    new ItemStack(ModItems.SIXTY_SECONDS_COIN, coins));
            player.displayClientMessage(Component.translatable(
                    "message.noellesroles.hotline.recycle.confirmed", totalScrap, coins)
                    .withStyle(ChatFormatting.GREEN), false);
        } else {
            player.displayClientMessage(tl("hotline.recycle.nothing").withStyle(ChatFormatting.GRAY), false);
        }
        hangup(player);
    }

    public static void handleRecycleCancel(ServerPlayer player) {
        player.displayClientMessage(tl("hotline.recycle.bye").withStyle(ChatFormatting.GOLD), false);
        hangup(player);
    }

    // ═══════════════════════════════════════════════════════════
    // 贫困救济热线
    // ═══════════════════════════════════════════════════════════

    public static void handlePovertyReliefGreeting(ServerPlayer player) {
        if (isTimeout(player)) { timeout(player); return; }
        int teamId = SixtySecondsStatsComponent.KEY.get(player).teamId;
        // 次日发放救济金
        scheduleDelivery(player.serverLevel(), teamId,
                new ItemStack(ModItems.SIXTY_SECONDS_COIN, 5));
        player.displayClientMessage(tl("hotline.poverty_relief.greeting").withStyle(ChatFormatting.GOLD), false);
        player.displayClientMessage(tl("hotline.poverty_relief.confirmed").withStyle(ChatFormatting.GREEN), false);
        hangup(player);
    }

    // ═══════════════════════════════════════════════════════════
    // 匿名举报热线
    // ═══════════════════════════════════════════════════════════

    public static void handleReportGreeting(ServerPlayer player) {
        if (isTimeout(player)) { timeout(player); return; }
        HotlineData data = getData(player.serverLevel());
        if (data.reportUsedToday.contains(player.getUUID())) {
            player.displayClientMessage(tl("hotline.report.already_used").withStyle(ChatFormatting.RED), false);
            hangup(player);
            return;
        }
        int teamId = SixtySecondsStatsComponent.KEY.get(player).teamId;
        data.reportUsedToday.add(player.getUUID());
        // 举报奖励：次日发放3游戏币
        scheduleDelivery(player.serverLevel(), teamId,
                new ItemStack(ModItems.SIXTY_SECONDS_COIN, 3));
        player.displayClientMessage(tl("hotline.report.greeting").withStyle(ChatFormatting.GOLD), false);
        player.displayClientMessage(tl("hotline.report.reward").withStyle(ChatFormatting.GREEN), false);
        hangup(player);
    }

    // ═══════════════════════════════════════════════════════════
    // 次日清晨：处理快递/购物/救援的投递与扣费
    // ═══════════════════════════════════════════════════════════

    public static void processDeliveries(ServerLevel level, SixtySecondsState.Data stateData) {
        HotlineData data = HOTLINE_DATA.get(level);
        if (data == null) return;

        // 1. 快递包裹投递（隔天到）
        for (Map.Entry<Integer, SixtySecondsState.TeamData> entry : stateData.teams.entrySet()) {
            int teamId = entry.getKey();
            // 查找发给该队的快递
            for (Map.Entry<Integer, List<BlockPos>> mbEntry :
                    SixtySecondsNewspaper.getMailboxRegistry(level).entrySet()) {
                int senderTeam = mbEntry.getKey();
                for (BlockPos pos : mbEntry.getValue()) {
                    BlockEntity be = level.getBlockEntity(pos);
                    if (!(be instanceof net.exmo.sre.sixtyseconds.content.block_entity.SixtySecondsMailboxBlockEntity mb))
                        continue;
                    for (int i = 0; i < mb.getContainerSize(); i++) {
                        ItemStack stack = mb.getItem(i);
                        if (stack.is(ModItems.SIXTY_SECONDS_EXPRESS_PACKAGE)) {
                            // 检查是否有待处理的快递目标
                            // 移除包裹，隔天投递给目标队
                            mb.setItem(i, ItemStack.EMPTY);
                            int target = data.pendingCourierTeam;
                            if (target > 0 && target != senderTeam) {
                                // 隔天投递到目标队邮箱
                                scheduleDelivery(level, target, stack.copy());
                            }
                        }
                    }
                }
            }
        }

        // 2. 购物投递：扣游戏币，不够则不发
        for (Map.Entry<Integer, List<ShopItem>> entry : data.pendingShopPurchases.entrySet()) {
            int teamId = entry.getKey();
            int totalCost = entry.getValue().stream().mapToInt(si -> si.price).sum();
            int coinsInBox = countCoinsInMailbox(level, teamId);

            if (coinsInBox < totalCost) {
                // 通知该队所有在线玩家
                for (ServerPlayer p : level.players()) {
                    if (SixtySecondsStatsComponent.KEY.get(p).teamId == teamId) {
                        p.displayClientMessage(tl("hotline.shop.insufficient").withStyle(ChatFormatting.RED), false);
                    }
                }
                continue;
            }

            // 扣币 + 投递
            removeCoinsFromMailbox(level, teamId, totalCost);
            for (ShopItem si : entry.getValue()) {
                deliverToTeam(level, teamId, si.item.copy());
            }
        }

        // 3. 救援投递
        for (Map.Entry<Integer, List<ItemStack>> entry : data.pendingRescueRequests.entrySet()) {
            for (ItemStack stack : entry.getValue()) {
                deliverToTeam(level, entry.getKey(), stack.copy());
            }
        }

        // 4. 通用次日投递（雇佣武器/黑市物资/回收兑换币/贫困救济金/举报奖励）
        Map<Integer, List<ItemStack>> pending = PENDING_DELIVERIES.remove(level);
        if (pending != null) {
            for (Map.Entry<Integer, List<ItemStack>> entry : pending.entrySet()) {
                for (ItemStack stack : entry.getValue()) {
                    deliverToTeam(level, entry.getKey(), stack.copy());
                }
            }
        }

        // 清理
        data.pendingCourierTeam = 0;
        data.pendingShopPurchases.clear();
        data.pendingRescueRequests.clear();
        data.activeCall = null;
    }

    // ═══════════════════════════════════════════════════════════
    // 购物商品生成
    // ═══════════════════════════════════════════════════════════

    private static List<ShopItem> generateShopItems(ServerLevel level) {
        RandomSource rand = level.getRandom();
        List<ShopItem> items = new ArrayList<>();
        int count = 3 + rand.nextInt(4); // 3~6

        // ── 材料池 ──（用可变 ArrayList：下方按概率 add 贵金属/酿造器件；Arrays.asList 定长会 UOE 崩服）
        List<MaterialEntry> materials = new ArrayList<>(Arrays.asList(
            m(ModItems.SIXTY_SECONDS_SCRAP, 1, 3),
            m(ModItems.SIXTY_SECONDS_PLASTIC, 1, 3),
            m(ModItems.SIXTY_SECONDS_GLASS_SHARD, 1, 3),
            m(Items.STRING, 1, 3),
            m(Items.LEATHER, 1, 3),
            m(Items.PAPER, 1, 3),
            m(Items.OAK_PLANKS, 1, 3),
            m(ModItems.SIXTY_SECONDS_WIRE, 1, 3),
            m(ModItems.SIXTY_SECONDS_GEAR, 1, 3),
            m(ModItems.SIXTY_SECONDS_DUCT_TAPE, 1, 3),
            m(ModItems.SIXTY_SECONDS_ELECTRONICS, 1, 3),
            m(ModItems.SIXTY_SECONDS_SCRAP_METAL, 1, 3),
            m(ModItems.SIXTY_SECONDS_COPPER_SCRAP, 1, 3),
            m(ModItems.SIXTY_SECONDS_RAG, 1, 3),
            m(ModItems.SIXTY_SECONDS_CHEMICALS, 1, 3),
            m(ModItems.SIXTY_SECONDS_GUNPOWDER_PACK, 1, 3),
            m(ModItems.SIXTY_SECONDS_NAILS, 1, 3)
        ));

        // 小概率贵金属/酿造器件
        if (rand.nextDouble() < 0.15)
            materials.add(m(ModItems.SIXTY_SECONDS_PRECIOUS_PARTS, 5, 8));
        if (rand.nextDouble() < 0.08)
            materials.add(m(ModItems.SIXTY_SECONDS_BREWING_PARTS, 4, 7));

        // ── 工具池 ──
        List<ToolEntry> tools = Arrays.asList(
            t(ModItems.SIXTY_SECONDS_KNIFE, 3, 6),
            t(ModItems.SIXTY_SECONDS_HARMONICA, 3, 6),
            t(ModItems.SIXTY_SECONDS_FLASHLIGHT, 3, 6),
            t(ModItems.SIXTY_SECONDS_ROPE, 3, 6),
            t(Items.FISHING_ROD, 3, 6),
            t(ModItems.SIXTY_SECONDS_CROWBAR, 3, 6),
            t(ModItems.SIXTY_SECONDS_CLAW_HOOK, 3, 6),
            t(ModItems.SIXTY_SECONDS_PLIERS, 3, 6),
            t(ModItems.SIXTY_SECONDS_GRAPPLING_HOOK, 3, 6),
            t(ModItems.SIXTY_SECONDS_UMBRELLA, 3, 6),
            t(Items.TORCH, 3, 6),
            t(ModItems.SIXTY_SECONDS_CLOCK, 3, 6),
            t(ModItems.SIXTY_SECONDS_RADIO, 3, 6),
            t(ModItems.SIXTY_SECONDS_COMPASS, 3, 6),
            t(Items.WRITABLE_BOOK, 3, 6)
        );

        // ── 食物池（15%概率出现一项） ──
        List<FoodEntry> foods = Arrays.asList(
            f(ModItems.SIXTY_SECONDS_BISCUIT, 1, 3),
            f(ModItems.SIXTY_SECONDS_MRE, 4, 6),
            f(ModItems.SIXTY_SECONDS_CANNED_FOOD, 3, 5),
            f(ModItems.SIXTY_SECONDS_CANNED_SOUP, 3, 5)
        );

        // ── 水（15%概率出现一项） ──
        List<FoodEntry> waters = Arrays.asList(
            f(ModItems.SIXTY_SECONDS_WATER_SMALL, 1, 3),
            f(ModItems.SIXTY_SECONDS_WATER_MEDIUM, 2, 4),
            f(ModItems.SIXTY_SECONDS_PURIFIED_WATER, 3, 5),
            f(ModItems.SIXTY_SECONDS_THERMOS, 5, 7)
        );

        // ── 种子池（20%概率出现一项） ──
        List<SeedEntry> seeds = new ArrayList<>(Arrays.asList(
            s(ModItems.SIXTY_SECONDS_SEEDS_PACK, 3, 6),
            s(ModItems.SIXTY_SECONDS_WILD_RICE_SEEDS, 3, 6),
            s(ModItems.SIXTY_SECONDS_WILD_TEA_SEED, 3, 6),
            s(ModItems.SIXTY_SECONDS_TOBACCO_SEEDS, 3, 6),
            s(Items.BROWN_MUSHROOM, 3, 6),
            s(Items.RED_MUSHROOM, 3, 6),
            s(Items.WHEAT_SEEDS, 3, 6),
            s(Items.CARROT, 3, 6),
            s(Items.POTATO, 3, 6),
            s(Items.BEETROOT_SEEDS, 3, 6),
            s(Items.PUMPKIN_SEEDS, 3, 6),
            s(Items.MELON_SEEDS, 3, 6),
            s(Items.BAMBOO, 3, 6),
            s(Items.SWEET_BERRIES, 3, 6),
            s(Items.GLOW_BERRIES, 3, 6)
        ));
        // 低概率特殊种子
        seeds.add(s(ModItems.SIXTY_SECONDS_HEMP_SEEDS, 5, 10));
        seeds.add(s(Items.TORCHFLOWER_SEEDS, 8, 16));
        seeds.add(s(Items.PITCHER_POD, 9, 18));

        // ── 按概率生成 ──
        if (rand.nextDouble() < 0.15 && !foods.isEmpty()) {
            FoodEntry fe = foods.get(rand.nextInt(foods.size()));
            items.add(new ShopItem(new ItemStack(fe.item, 1), fe.basePrice + rand.nextInt(3)));
        }
        if (rand.nextDouble() < 0.15 && !waters.isEmpty()) {
            FoodEntry we = waters.get(rand.nextInt(waters.size()));
            items.add(new ShopItem(new ItemStack(we.item, 1), we.basePrice + rand.nextInt(3)));
        }
        if (rand.nextDouble() < 0.20) {
            SeedEntry se = seeds.get(rand.nextInt(seeds.size()));
            items.add(new ShopItem(new ItemStack(se.item, 1), se.basePrice + rand.nextInt(se.maxExtra)));
        }

        // 填充：材料和工具混排
        while (items.size() < count) {
            if (rand.nextBoolean()) {
                MaterialEntry me = materials.get(rand.nextInt(materials.size()));
                int qty = 1 + rand.nextInt(5);
                int price = (me.basePrice + rand.nextInt(me.maxExtra + 1)) * qty;
                items.add(new ShopItem(new ItemStack(me.item, qty), price));
            } else {
                ToolEntry te = tools.get(rand.nextInt(tools.size()));
                int price = te.minPrice + rand.nextInt(te.maxPrice - te.minPrice + 1);
                items.add(new ShopItem(new ItemStack(te.item, 1), price));
            }
        }

        // 打乱顺序
        Collections.shuffle(items, new java.util.Random());
        return items;
    }

    // ═══════════════════════════════════════════════════════════
    // 辅助方法
    // ═══════════════════════════════════════════════════════════

    private static MutableComponent tl(String key) {
        return Component.translatable("message.noellesroles." + key);
    }

    private static Component btn(String key, String cmd, ChatFormatting color) {
        return Component.translatable("message.noellesroles." + key)
            .setStyle(Style.EMPTY.withColor(color)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd)));
    }

    private static void timeout(ServerPlayer player) {
        player.displayClientMessage(tl("hotline.timeout").withStyle(ChatFormatting.RED), false);
        hangup(player);
    }

    private static void hangup(ServerPlayer player) {
        HotlineData data = getData(player.serverLevel());
        if (data != null) data.activeCall = null;
    }

    private static HotlineData getData(ServerLevel level) {
        return HOTLINE_DATA.computeIfAbsent(level, k -> new HotlineData());
    }

    private static String pad6(int n) { return String.format("%06d", n); }

    /** 计算邮箱中的游戏币总数 */
    private static int countCoinsInMailbox(ServerLevel level, int teamId) {
        List<BlockPos> boxes = SixtySecondsNewspaper.getMailboxRegistry(level).get(teamId);
        if (boxes == null) return 0;
        int total = 0;
        for (BlockPos pos : boxes) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof net.exmo.sre.sixtyseconds.content.block_entity.SixtySecondsMailboxBlockEntity mb) {
                for (int i = 0; i < mb.getContainerSize(); i++) {
                    ItemStack s = mb.getItem(i);
                    if (s.is(ModItems.SIXTY_SECONDS_COIN)) total += s.getCount();
                }
            }
        }
        return total;
    }

    private static void removeCoinsFromMailbox(ServerLevel level, int teamId, int amount) {
        List<BlockPos> boxes = SixtySecondsNewspaper.getMailboxRegistry(level).get(teamId);
        if (boxes == null) return;
        int remaining = amount;
        for (BlockPos pos : boxes) {
            if (remaining <= 0) break;
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof net.exmo.sre.sixtyseconds.content.block_entity.SixtySecondsMailboxBlockEntity mb) {
                for (int i = 0; i < mb.getContainerSize() && remaining > 0; i++) {
                    ItemStack s = mb.getItem(i);
                    if (s.is(ModItems.SIXTY_SECONDS_COIN)) {
                        int take = Math.min(remaining, s.getCount());
                        s.shrink(take);
                        remaining -= take;
                        if (s.isEmpty()) mb.setItem(i, ItemStack.EMPTY);
                    }
                }
            }
        }
    }

    private static void scheduleDelivery(ServerLevel level, int teamId, ItemStack stack) {
        PENDING_DELIVERIES.computeIfAbsent(level, k -> new HashMap<>())
            .computeIfAbsent(teamId, k -> new ArrayList<>()).add(stack);
    }

    private static void deliverToTeam(ServerLevel level, int teamId, ItemStack stack) {
        List<BlockPos> boxes = SixtySecondsNewspaper.getMailboxRegistry(level).get(teamId);
        if (boxes == null || boxes.isEmpty()) return;
        BlockEntity be = level.getBlockEntity(boxes.get(0));
        if (!(be instanceof net.exmo.sre.sixtyseconds.content.block_entity.SixtySecondsMailboxBlockEntity mb)) return;
        for (int i = 0; i < mb.getContainerSize(); i++) {
            if (mb.getItem(i).isEmpty()) {
                mb.setItem(i, stack);
                mb.setChanged();
                return;
            }
        }
    }

    public static void reset(ServerLevel level) {
        HOTLINE_DATA.remove(level);
        PENDING_DELIVERIES.remove(level);
    }

    public static List<HotlineEntry> getDailyHotlines(ServerLevel level) {
        HotlineData data = HOTLINE_DATA.get(level);
        if (data == null) return List.of();
        return List.copyOf(data.dailyHotlines);
    }

    public static boolean hasDialed(ServerLevel level, String number) {
        HotlineData data = HOTLINE_DATA.get(level);
        return data != null && data.dialedToday.contains(number);
    }

    // ═══════════════════════════════════════════════════════════
    // 数据结构
    // ═══════════════════════════════════════════════════════════

    public record HotlineEntry(String number, HotlineType type) {}
    public enum HotlineType {
        EXPRESS, SHOP, RESCUE,
        /** 情报热线：听取全区动态播报、各队存活数 */
        INTEL,
        /** 天气预报热线：播报未来24小时天气预测 */
        WEATHER,
        /** 心理辅导热线：接听后理智值+15，每天限一次 */
        COUNSEL,
        /** 雇佣热线：消耗游戏币雇佣NPC守卫 */
        HIRE,
        /** 黑市热线：购买违禁品 */
        BLACK_MARKET,
        /** 回收热线：用废旧物资兑换游戏币 */
        RECYCLE,
        /** 贫困救济热线：小概率出现，次日发放救济金 */
        POVERTY_RELIEF,
        /** 匿名举报热线 */
        REPORT
    }
    public record ShopItem(ItemStack item, int price) {}

    private record MaterialEntry(Item item, int basePrice, int maxExtra) {}
    private record ToolEntry(Item item, int minPrice, int maxPrice) {}
    private record FoodEntry(Item item, int basePrice, int maxExtra) {}
    private record SeedEntry(Item item, int basePrice, int maxExtra) {}

    private record ActiveCall(UUID playerId, HotlineType type, long startTime, int step) {}

    private static class HotlineData {
        final List<HotlineEntry> dailyHotlines = new ArrayList<>();
        final Set<String> dialedToday = new HashSet<>();
        final Set<UUID> playersDialedToday = new HashSet<>();
        ActiveCall activeCall = null;
        List<ShopItem> shopItems = new ArrayList<>();
        Set<Integer> shopPurchased = new HashSet<>();
        int pendingCourierTeam = 0;
        final Map<Integer, List<ShopItem>> pendingShopPurchases = new HashMap<>();
        final Map<Integer, List<ItemStack>> pendingRescueRequests = new HashMap<>();
        /** 已使用心理辅导热线的玩家（每天重置） */
        final Set<UUID> counselUsedToday = new HashSet<>();
        /** 已使用匿名举报热线的玩家（每天重置） */
        final Set<UUID> reportUsedToday = new HashSet<>();
    }

    private static MaterialEntry m(Item item, int base, int extra) {
        return new MaterialEntry(item, base, extra);
    }
    private static ToolEntry t(Item item, int min, int max) {
        return new ToolEntry(item, min, max);
    }
    private static FoodEntry f(Item item, int base, int extra) {
        return new FoodEntry(item, base, extra);
    }
    private static SeedEntry s(Item item, int base, int extra) {
        return new SeedEntry(item, base, extra);
    }
}
