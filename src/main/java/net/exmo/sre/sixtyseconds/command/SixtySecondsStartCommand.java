package net.exmo.sre.sixtyseconds.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.cca.ParticipationComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** {@code /sre:60s start [minutes]} 启动末日60秒模式（也可 {@code /tmm:start sre:sixty_seconds}）。同时包含 sick/cure 调试指令。 */
public final class SixtySecondsStartCommand {
    private SixtySecondsStartCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("sre:60s")
                        .then(literal("start")
                                .requires(source -> source.hasPermission(SREConfig.instance().startGameRequiredPermission))
                                // sre:60s start force_all_players [minutes] — 强制所有参与中的玩家加入，无视位置
                                .then(literal("force_all_players")
                                        .then(argument("minutes", IntegerArgumentType.integer(1))
                                                .executes(context -> start(context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "minutes"), true)))
                                        .executes(context -> start(context.getSource(), -1, true)))
                                // sre:60s start [minutes] — 常规启动，仅准备区域内的玩家加入
                                .then(argument("minutes", IntegerArgumentType.integer(1))
                                        .executes(context -> start(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "minutes"), false)))
                                .executes(context -> start(context.getSource(), -1, false)))
                        // 赛前组队大厅（对所有玩家开放；游戏进行中不可用）
                        .then(literal("team").executes(context -> openTeamLobby(context.getSource())))
                        // 科技树（对所有玩家开放；仅本模式进行中可用）
                        .then(literal("tech").executes(context -> openTechTree(context.getSource())))
                        // 队伍信息汇总（家门/供电/科技/共享代币/成员状态）
                        .then(literal("info").executes(context -> showInfo(context.getSource())))
                        // 管理指令：跳到指定游戏日 / 跳到日内指定时间（清晨/白天/晚上/睡觉）
                        .then(literal("day")
                                .requires(source -> source.hasPermission(2))
                                .then(argument("day", IntegerArgumentType.integer(1, 7))
                                        .executes(context -> setDay(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "day")))))
                        .then(literal("time")
                                .requires(source -> source.hasPermission(2))
                                .then(literal("morning").executes(c -> setTime(c.getSource(), "morning")))
                                .then(literal("daytime").executes(c -> setTime(c.getSource(), "daytime")))
                                .then(literal("night").executes(c -> setTime(c.getSource(), "night")))
                                .then(literal("sleep").executes(c -> setTime(c.getSource(), "sleep"))))
                        // 管理指令：直接设置玩家的生存状态值 / 状态位
                        // /sre:60s set <player> health|hunger|thirst|sanity|pollution <0..100>
                        // /sre:60s set <player> downed|monster|sick <true|false>
                        // /sre:60s set <player> revive — 救起倒地玩家（等价 downed false）
                        .then(buildSetCommand())
                        // 自我解脱：san 归零变怪倒计时中，玩家可牺牲以换队伍安全（对所有玩家开放）
                        .then(literal("sacrifice").executes(context -> sacrifice(context.getSource())))
                        // 抵达幸存者阵营（触发点为后续设计；此命令供 OP 触发/测试）
                        .then(literal("reach_survivors")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> reachSurvivors(context.getSource())))
                        // 玩家NPC 敲门喊话：/sre:60s ask <文字>（创造模式，先右键敲避难所门）
                        .then(literal("ask")
                                .then(argument("text", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                        .executes(context -> npcAsk(context.getSource(),
                                                com.mojang.brigadier.arguments.StringArgumentType
                                                        .getString(context, "text")))))
                        // 末日日报：点击聊天栏提醒打开当日报纸
                        .then(literal("newspaper")
                                .executes(context -> openNewspaper(context.getSource()))
                                .then(literal("give").executes(context -> giveNewspaper(context.getSource()))))
                        // 热线电话：聊天栏按钮回调（/sre:60s hotline <type> <action> [args]）
                        .then(literal("hotline")
                                .then(literal("express")
                                        .then(literal("send").executes(context -> hotlineExpressSend(context.getSource())))
                                        .then(literal("cancel").executes(context -> hotlineExpressCancel(context.getSource())))
                                        .then(literal("team")
                                                .then(argument("team", IntegerArgumentType.integer(0))
                                                        .executes(context -> hotlineExpressTeam(context.getSource(),
                                                                IntegerArgumentType.getInteger(context, "team"))))))
                                .then(literal("shop")
                                        .then(literal("buy")
                                                .then(argument("index", IntegerArgumentType.integer(0))
                                                        .executes(context -> hotlineShopBuy(context.getSource(),
                                                                IntegerArgumentType.getInteger(context, "index")))))
                                        .then(literal("cancel").executes(context -> hotlineShopCancel(context.getSource()))))
                                .then(literal("rescue")
                                        .then(literal("request").executes(context -> hotlineRescueRequest(context.getSource())))
                                        .then(literal("cancel").executes(context -> hotlineRescueCancel(context.getSource())))))
                        // 每日事件门：玩家点击聊天栏选项（/sre:60s event <token> <option>）+ 管理员强制触发
                        .then(literal("event")
                                .then(literal("force")
                                        .requires(source -> source.hasPermission(2))
                                        .then(argument("id", com.mojang.brigadier.arguments.StringArgumentType.word())
                                                .suggests((context, builder) -> net.minecraft.commands
                                                        .SharedSuggestionProvider.suggest(
                                                                net.exmo.sre.sixtyseconds.logic
                                                                        .SixtySecondsDailyEvents.eventIds(), builder))
                                                .executes(context -> forceEvent(context.getSource(),
                                                        com.mojang.brigadier.arguments.StringArgumentType
                                                                .getString(context, "id")))))
                                .then(argument("token", IntegerArgumentType.integer(1))
                                        .then(argument("option", IntegerArgumentType.integer(1, 2))
                                                .executes(context -> chooseEvent(context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "token"),
                                                        IntegerArgumentType.getInteger(context, "option"))))))
                        // 管理员：晚上是否自动刷新夜袭者（按图配置持久化；默认关。关闭时可用召唤哨手动放怪）
                        .then(literal("assault")
                                .requires(source -> source.hasPermission(2))
                                .then(literal("on").executes(c -> setNightAssault(c.getSource(), true)))
                                .then(literal("off").executes(c -> setNightAssault(c.getSource(), false)))
                                .executes(c -> showNightAssault(c.getSource())))
                        // 管理员：PVE 开关（探索区游荡怪 + 夜晚 Boss；按图配置持久化，默认开）
                        .then(literal("pve")
                                .requires(source -> source.hasPermission(2))
                                .then(literal("on").executes(c -> setPve(c.getSource(), true)))
                                .then(literal("off").executes(c -> setPve(c.getSource(), false)))
                                .executes(c -> showPve(c.getSource())))
                        // 管理员：本局总游戏日数（默认 7，按图持久化）；不带参数=查看当前值
                        .then(literal("days")
                                .requires(source -> source.hasPermission(2))
                                .then(argument("count", IntegerArgumentType.integer(1,
                                        net.exmo.sre.sixtyseconds.logic.SixtySecondsManager.MAX_TOTAL_DAYS))
                                        .executes(c -> setTotalDays(c.getSource(),
                                                IntegerArgumentType.getInteger(c, "count"))))
                                .executes(c -> showTotalDays(c.getSource())))
                        // 管理员：避难所直接生成在探索区出口门上（锚点=避难所锚点门↔出口门；默认开，按图持久化）
                        .then(literal("shelter_at_door")
                                .requires(source -> source.hasPermission(2))
                                .then(literal("on").executes(c -> setShelterAtDoor(c.getSource(), true)))
                                .then(literal("off").executes(c -> setShelterAtDoor(c.getSource(), false)))
                                .executes(c -> showShelterAtDoor(c.getSource())))
                        // 管理员：海图扬帆传送 / 返回住所开关（默认关=玩家自己乘船去岛，按图持久化）
                        .then(literal("sea_teleport")
                                .requires(source -> source.hasPermission(2))
                                .then(literal("on").executes(c -> setSeaTeleport(c.getSource(), true)))
                                .then(literal("off").executes(c -> setSeaTeleport(c.getSource(), false)))
                                .executes(c -> showSeaTeleport(c.getSource())))
                        // 管理员：中途自动入队开关（新玩家自动补入未满四人的队伍；默认开，按图持久化）
                        .then(literal("autojoin")
                                .requires(source -> source.hasPermission(2))
                                .then(literal("on").executes(c -> setAutoJoin(c.getSource(), true)))
                                .then(literal("off").executes(c -> setAutoJoin(c.getSource(), false)))
                                .executes(c -> showAutoJoin(c.getSource())))
                        // 海岛远征：/sre:60s island start|stop（OP，独立于对局的地图机制开关）
                        // map=打开海图（所有玩家）、sail <id>=扬帆（海图点击触发）、home=返回住所
                        .then(literal("island")
                                .then(literal("start")
                                        .requires(source -> source.hasPermission(2))
                                        .then(argument("count", IntegerArgumentType.integer(3, 16))
                                                // start <count> radius <r> —— 只改基准半径，其余用默认
                                                .then(literal("radius")
                                                        .then(argument("radius", IntegerArgumentType.integer(16, 120))
                                                                .executes(c -> islandStart(c.getSource(),
                                                                        IntegerArgumentType.getInteger(c, "count"),
                                                                        Integer.MIN_VALUE, Integer.MIN_VALUE,
                                                                        Integer.MIN_VALUE,
                                                                        IntegerArgumentType.getInteger(c, "radius")))))
                                                .then(argument("centerX", IntegerArgumentType.integer())
                                                        .then(argument("centerZ", IntegerArgumentType.integer())
                                                                .then(argument("seaY", IntegerArgumentType
                                                                        .integer(-60, 300))
                                                                        // start <count> <x> <z> <seaY> [radius]
                                                                        .then(argument("radius", IntegerArgumentType
                                                                                .integer(16, 120))
                                                                                .executes(c -> islandStart(c.getSource(),
                                                                                        IntegerArgumentType.getInteger(c, "count"),
                                                                                        IntegerArgumentType.getInteger(c, "centerX"),
                                                                                        IntegerArgumentType.getInteger(c, "centerZ"),
                                                                                        IntegerArgumentType.getInteger(c, "seaY"),
                                                                                        IntegerArgumentType.getInteger(c, "radius"))))
                                                                        .executes(c -> islandStart(c.getSource(),
                                                                                IntegerArgumentType.getInteger(c, "count"),
                                                                                IntegerArgumentType.getInteger(c, "centerX"),
                                                                                IntegerArgumentType.getInteger(c, "centerZ"),
                                                                                IntegerArgumentType.getInteger(c, "seaY"),
                                                                                Integer.MIN_VALUE)))
                                                                .executes(c -> islandStart(c.getSource(),
                                                                        IntegerArgumentType.getInteger(c, "count"),
                                                                        IntegerArgumentType.getInteger(c, "centerX"),
                                                                        IntegerArgumentType.getInteger(c, "centerZ"),
                                                                        Integer.MIN_VALUE, Integer.MIN_VALUE))))
                                                .executes(c -> islandStart(c.getSource(),
                                                        IntegerArgumentType.getInteger(c, "count"),
                                                        Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE,
                                                        Integer.MIN_VALUE)))
                                        .executes(c -> islandStart(c.getSource(), 9,
                                                Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE,
                                                Integer.MIN_VALUE)))
                                .then(literal("stop")
                                        .requires(source -> source.hasPermission(2))
                                        .executes(c -> islandStop(c.getSource())))
                                .then(literal("delete")
                                        .requires(source -> source.hasPermission(2))
                                        .executes(c -> islandDelete(c.getSource())))
                                .then(literal("map").executes(c -> islandMap(c.getSource())))
                                .then(literal("home").executes(c -> islandHome(c.getSource())))
                                .then(literal("list")
                                        .requires(source -> source.hasPermission(2))
                                        .executes(c -> islandList(c.getSource())))
                                .then(literal("unlock")
                                        .requires(source -> source.hasPermission(2))
                                        .then(argument("id", IntegerArgumentType.integer(0))
                                                .executes(c -> islandUnlock(c.getSource(),
                                                        IntegerArgumentType.getInteger(c, "id")))))
                                .then(literal("sail")
                                        .then(argument("id", IntegerArgumentType.integer(0))
                                                .executes(c -> islandSail(c.getSource(),
                                                        IntegerArgumentType.getInteger(c, "id"))))))
                        // 管理员：立即在准星/自身位置刷一只 Boss（缺省等级=1，可选变体）
                        .then(literal("boss")
                                .requires(source -> source.hasPermission(2))
                                .then(argument("level", IntegerArgumentType.integer(1,
                                        net.exmo.sre.sixtyseconds.SixtySecondsBalance.BOSS_MAX_LEVEL))
                                        .then(argument("variant", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    for (var v : net.exmo.sre.sixtyseconds.entity.SixtySecondsBossEntity.BossVariant.values()) {
                                                        builder.suggest(v.name().toLowerCase());
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(c -> spawnBoss(c.getSource(),
                                                        IntegerArgumentType.getInteger(c, "level"),
                                                        StringArgumentType.getString(c, "variant"))))
                                        .executes(c -> spawnBoss(c.getSource(),
                                                IntegerArgumentType.getInteger(c, "level"))))
                                .executes(c -> spawnBoss(c.getSource(), 1)))
                        // 管理员：开局保底物资开关（按图配置持久化；默认关=全靠准备阶段搜刮）
                        .then(literal("starter")
                                .requires(source -> source.hasPermission(2))
                                .then(literal("on").executes(c -> setStarterSupplies(c.getSource(), true)))
                                .then(literal("off").executes(c -> setStarterSupplies(c.getSource(), false)))
                                .executes(c -> showStarterSupplies(c.getSource())))
                        // 管理员：投放空投（缺省=探索区内随机；带坐标=指定 x z）
                        .then(literal("airdrop")
                                .requires(source -> source.hasPermission(2))
                                .then(argument("x", IntegerArgumentType.integer())
                                        .then(argument("z", IntegerArgumentType.integer())
                                                .executes(context -> airdrop(context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "x"),
                                                        IntegerArgumentType.getInteger(context, "z")))))
                                .executes(context -> airdropRandom(context.getSource())))
                        // 管理员：打开空投物资自定义编辑 GUI（创造模式）
                        .then(literal("airdrop_edit")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> openAirdropEdit(context.getSource())))
                        // 管理员：列出所有队伍的家（住宅/避难所/探索区）坐标，点击一键传送
                        .then(literal("homes")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> listHomes(context.getSource())))
                        // 管理员：强制给予/移除生病状态（给指定玩家施加/解除疾病）
                        .then(literal("sick")
                                .requires(source -> source.hasPermission(2))
                                .then(argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                        .executes(context -> forceSick(context.getSource(),
                                                net.minecraft.commands.arguments.EntityArgument
                                                        .getPlayer(context, "player")))))
                        .then(literal("cure")
                                .requires(source -> source.hasPermission(2))
                                .then(argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                        .executes(context -> forceCure(context.getSource(),
                                                net.minecraft.commands.arguments.EntityArgument
                                                        .getPlayer(context, "player")))))
                        // 掉线备份：手动 保存/恢复/查看（掉线-重连时会自动触发，见 SixtySecondsReconnect）
                        .then(literal("backup")
                                .requires(source -> source.hasPermission(2))
                                .then(literal("save")
                                        .then(argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                                .executes(context -> backupSave(context.getSource(),
                                                        net.minecraft.commands.arguments.EntityArgument
                                                                .getPlayer(context, "player")))))
                                .then(literal("restore")
                                        .then(argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                                .executes(context -> backupRestore(context.getSource(),
                                                        net.minecraft.commands.arguments.EntityArgument
                                                                .getPlayer(context, "player")))))
                                .then(literal("list").executes(context -> backupList(context.getSource()))))));
    }

    /** 玩家NPC 敲门喊话（创造模式；需先右键敲过一扇避难所门）。 */
    private static int npcAsk(CommandSourceStack source, String text) {
        if (!(source.getEntity() instanceof ServerPlayer player) || !SixtySecondsMod.isActive(player.level())) {
            return 0;
        }
        net.exmo.sre.sixtyseconds.logic.SixtySecondsNpcKnock.ask(player, text);
        return 1;
    }

    /** 末日日报：打开当日报纸（对所有玩家开放）。 */
    private static int openNewspaper(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player) || !SixtySecondsMod.isActive(player.level())) {
            return 0;
        }
        net.exmo.sre.sixtyseconds.logic.SixtySecondsNewspaper.open(player);
        return 1;
    }

    /** 测试用：给予模拟报纸（内容中的玩家名统一为 Player） */
    private static int giveNewspaper(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        ServerLevel level = player.serverLevel();
        net.exmo.sre.sixtyseconds.state.SixtySecondsState.Data data =
                net.exmo.sre.sixtyseconds.state.SixtySecondsState.get(level);
        if (!SixtySecondsMod.isActive(level) || data.teams.isEmpty()) {
            // 没有对局运行时也允许测试，构造假报纸
            ItemStack stack = buildTestNewspaper(level, 1);
            if (!player.getInventory().add(stack)) player.drop(stack, false);
            source.sendSuccess(() -> Component.translatable(
                    "message.noellesroles.sixty_seconds.newspaper_given"), true);
            return 1;
        }
        int teamId = net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent.KEY.get(player).teamId;
        net.exmo.sre.sixtyseconds.logic.SixtySecondsNewspaper.collectDrafts(level, data);
        net.exmo.sre.sixtyseconds.logic.SixtySecondsNewspaper.publish(level, data);
        ItemStack stack = new ItemStack(ModItems.NEWSPAPER, 1);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        source.sendSuccess(() -> Component.translatable(
                "message.noellesroles.sixty_seconds.newspaper_given"), true);
        return 1;
    }

    private static ItemStack buildTestNewspaper(ServerLevel level, int day) {
        ItemStack stack = new ItemStack(ModItems.NEWSPAPER, 1);
        // 无需设置 NBT——报纸物品自身不携带内容，右键打开读取当日报纸缓存
        return stack;
    }

    /** 每日事件门：玩家点击聊天栏选项（仅由 ClickEvent 触发）。 */
    private static int chooseEvent(CommandSourceStack source, int token, int option) {
        if (!(source.getEntity() instanceof ServerPlayer player) || !SixtySecondsMod.isActive(player.level())) {
            return 0;
        }
        net.exmo.sre.sixtyseconds.logic.SixtySecondsDailyEvents.choose(player, token, option);
        return 1;
    }

    /** 管理员：给自己所在队强制触发指定每日事件（测试用）。 */
    private static int forceEvent(CommandSourceStack source, String eventId) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only a player can force a daily event"));
            return 0;
        }
        if (!SixtySecondsMod.isActive(player.level())
                || !net.exmo.sre.sixtyseconds.logic.SixtySecondsDailyEvents.force(player, eventId)) {
            source.sendFailure(Component.translatable("message.noellesroles.sixty_seconds.devent.force_fail"));
            return 0;
        }
        return 1;
    }

    private static int airdrop(CommandSourceStack source, int x, int z) {
        if (net.exmo.sre.sixtyseconds.logic.SixtySecondsAirdrop.drop(source.getLevel(), x, z)) {
            return 1;
        }
        source.sendFailure(Component.translatable("message.noellesroles.sixty_seconds.airdrop_no_ground"));
        return 0;
    }

    private static int airdropRandom(CommandSourceStack source) {
        if (net.exmo.sre.sixtyseconds.logic.SixtySecondsAirdrop.dropRandom(source.getLevel())) {
            return 1;
        }
        source.sendFailure(Component.translatable("message.noellesroles.sixty_seconds.airdrop_no_zone"));
        return 0;
    }

    /** 打开空投资源自定义编辑 GUI（创造模式）。 */
    private static int openAirdropEdit(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return 0;
        }
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new net.exmo.sre.sixtyseconds.network.OpenAirdropEditS2CPacket(
                        net.exmo.sre.sixtyseconds.loot.SixtySecondsLootStore.get(player.serverLevel())));
        return 1;
    }

    /** 管理员：列出所有队伍的 住宅/避难所/探索区 出生点坐标；点击坐标一键传送（悬浮显示提示）。 */
    private static int listHomes(CommandSourceStack source) {
        if (!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            source.sendFailure(Component.literal("Only a player can use this"));
            return 0;
        }
        net.exmo.sre.sixtyseconds.state.SixtySecondsState.Data data =
                net.exmo.sre.sixtyseconds.state.SixtySecondsState.get(player.serverLevel());
        if (data.teams.isEmpty()) {
            source.sendFailure(Component.translatable("message.noellesroles.sixty_seconds.homes_empty"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("message.noellesroles.sixty_seconds.homes_header",
                data.teams.size()).withStyle(ChatFormatting.GOLD), false);
        for (net.exmo.sre.sixtyseconds.state.SixtySecondsState.TeamData team : data.teams.values()) {
            // 成员名列表
            StringBuilder members = new StringBuilder();
            for (java.util.UUID uuid : team.members) {
                var member = player.getServer().getPlayerList().getPlayer(uuid);
                if (members.length() > 0) {
                    members.append(", ");
                }
                members.append(member != null ? member.getGameProfile().getName() : "?");
            }
            net.minecraft.network.chat.MutableComponent line = Component.literal("§e#" + (team.teamId + 1) + " §7[")
                    .append(Component.literal(members.toString()).withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("§7] "));
            line.append(tpLink("message.noellesroles.sixty_seconds.homes_residential",
                    team.residentialSpawn, ChatFormatting.GREEN));
            line.append(tpLink("message.noellesroles.sixty_seconds.homes_shelter",
                    team.shelterSpawn, ChatFormatting.AQUA));
            line.append(tpLink("message.noellesroles.sixty_seconds.homes_search",
                    team.searchZoneSpawn, ChatFormatting.LIGHT_PURPLE));
            source.sendSuccess(() -> line, false);
        }
        return data.teams.size();
    }

    /** 一段可点击的传送链接：`名称(x,y,z)`，点击执行 /tp，悬浮显示提示；坐标缺失显示灰色占位。 */
    private static Component tpLink(String nameKey, net.minecraft.core.BlockPos pos, ChatFormatting color) {
        if (pos == null) {
            return Component.literal(" ").append(
                    Component.translatable(nameKey).withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("(-)").withStyle(ChatFormatting.DARK_GRAY));
        }
        String coords = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        return Component.literal(" ").append(Component.translatable(nameKey)
                .append(Component.literal("(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")"))
                .withStyle(style -> style.withColor(color)
                        .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/tp @s " + coords))
                        .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                                net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("message.noellesroles.sixty_seconds.homes_click_tp")))));
    }

    private static int backupSave(CommandSourceStack source, net.minecraft.server.level.ServerPlayer target) {
        if (net.exmo.sre.sixtyseconds.logic.SixtySecondsReconnect.save(target)) {
            source.sendSuccess(() -> Component.translatable(
                    "message.noellesroles.sixty_seconds.backup_saved", target.getGameProfile().getName()), true);
            return 1;
        }
        source.sendFailure(Component.translatable("message.noellesroles.sixty_seconds.backup_no_team"));
        return 0;
    }

    private static int backupRestore(CommandSourceStack source, net.minecraft.server.level.ServerPlayer target) {
        if (net.exmo.sre.sixtyseconds.logic.SixtySecondsReconnect.restore(target)) {
            source.sendSuccess(() -> Component.translatable(
                    "message.noellesroles.sixty_seconds.backup_restored", target.getGameProfile().getName()), true);
            return 1;
        }
        source.sendFailure(Component.translatable("message.noellesroles.sixty_seconds.backup_none"));
        return 0;
    }

    private static int backupList(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("message.noellesroles.sixty_seconds.backup_count",
                net.exmo.sre.sixtyseconds.logic.SixtySecondsReconnect.backupCount()), false);
        return 1;
    }

    private static int showInfo(CommandSourceStack source) {
        if (!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            source.sendFailure(Component.literal("Only a player can view team info"));
            return 0;
        }
        net.exmo.sre.sixtyseconds.logic.SixtySecondsTeamInfo.send(player);
        return 1;
    }

    private static int openTechTree(CommandSourceStack source) {
        if (!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            source.sendFailure(Component.literal("Only a player can open the tech tree"));
            return 0;
        }
        net.exmo.sre.sixtyseconds.logic.SixtySecondsTechTree.open(player);
        return 1;
    }

    private static int setDay(CommandSourceStack source, int day) {
        if (!SixtySecondsMod.isActive(source.getLevel())) {
            source.sendFailure(Component.translatable("message.noellesroles.sixty_seconds.cmd_not_running"));
            return 0;
        }
        if (!net.exmo.sre.sixtyseconds.logic.SixtySecondsManager.forceDay(source.getLevel(), day)) {
            source.sendFailure(Component.translatable("message.noellesroles.sixty_seconds.cmd_not_running"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("message.noellesroles.sixty_seconds.cmd_day_set", day), true);
        return 1;
    }

    private static int setTime(CommandSourceStack source, String timeName) {
        if (!SixtySecondsMod.isActive(source.getLevel())
                || !net.exmo.sre.sixtyseconds.logic.SixtySecondsManager.forceTime(source.getLevel(), timeName)) {
            source.sendFailure(Component.translatable("message.noellesroles.sixty_seconds.cmd_not_running"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("message.noellesroles.sixty_seconds.cmd_time_set", timeName), true);
        return 1;
    }

    private static int openTeamLobby(CommandSourceStack source) {
        if (!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            source.sendFailure(Component.literal("Only a player can open the team lobby"));
            return 0;
        }
        net.exmo.sre.sixtyseconds.logic.SixtySecondsTeamLobby.open(player);
        return 1;
    }

    private static int reachSurvivors(CommandSourceStack source) {
        if (!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
                || !SixtySecondsMod.isActive(player.level())) {
            return 0;
        }
        return net.exmo.sre.sixtyseconds.logic.SixtySecondsWinConditions.reachSurvivorCamp(player) ? 1 : 0;
    }

    /** 数值状态字段（0..100）。 */
    private static final String[] NUMERIC_FIELDS = { "health", "hunger", "thirst", "sanity", "pollution" };
    /** 布尔状态字段。 */
    private static final String[] FLAG_FIELDS = { "downed", "monster", "sick" };

    /**
     * {@code /sre:60s set <player> <field> <value>} 管理指令树：
     * 五个数值状态（0..100）+ 三个状态位（downed/monster/sick）+ revive 快捷救起。
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildSetCommand() {
        var playerNode = argument("player", net.minecraft.commands.arguments.EntityArgument.player());
        for (String field : NUMERIC_FIELDS) {
            playerNode.then(literal(field)
                    .then(argument("value", IntegerArgumentType.integer(0, 100))
                            .executes(context -> setNumericStat(context.getSource(),
                                    net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "player"),
                                    field, IntegerArgumentType.getInteger(context, "value")))));
        }
        for (String field : FLAG_FIELDS) {
            playerNode.then(literal(field)
                    .then(argument("value", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                            .executes(context -> setFlagStat(context.getSource(),
                                    net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "player"),
                                    field, com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "value")))));
        }
        playerNode.then(literal("revive").executes(context -> setFlagStat(context.getSource(),
                net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "player"), "downed", false)));
        return literal("set")
                .requires(source -> source.hasPermission(2))
                .then(playerNode);
    }

    private static int setNumericStat(CommandSourceStack source, ServerPlayer target, String field, int value) {
        if (!SixtySecondsMod.isActive(source.getLevel())) {
            source.sendFailure(Component.translatable("message.noellesroles.sixty_seconds.cmd_not_running"));
            return 0;
        }
        var stats = net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent.KEY.get(target);
        switch (field) {
            case "health" -> stats.health = value;
            case "hunger" -> stats.hunger = value;
            case "thirst" -> stats.thirst = value;
            case "sanity" -> stats.sanity = value;
            case "pollution" -> stats.pollution = value;
            default -> {
                return 0;
            }
        }
        stats.sync();
        // health 设 0 按正常受伤归零流程走（首次倒地 / 当日第二次直接死亡）
        if ("health".equals(field) && value <= 0 && !stats.downed && !stats.monster) {
            net.exmo.sre.sixtyseconds.logic.SixtySecondsHealthSystem.onHealthZero(target, true, null);
        }
        source.sendSuccess(() -> Component.translatable("message.noellesroles.sixty_seconds.cmd_stat_set",
                target.getGameProfile().getName(), field, value), true);
        return 1;
    }

    private static int setFlagStat(CommandSourceStack source, ServerPlayer target, String field, boolean value) {
        if (!SixtySecondsMod.isActive(source.getLevel())) {
            source.sendFailure(Component.translatable("message.noellesroles.sixty_seconds.cmd_not_running"));
            return 0;
        }
        var stats = net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent.KEY.get(target);
        switch (field) {
            case "downed" -> {
                if (value) {
                    net.exmo.sre.sixtyseconds.logic.SixtySecondsHealthSystem.forceDown(target);
                } else if (stats.downed) {
                    net.exmo.sre.sixtyseconds.logic.SixtySecondsHealthSystem.revive(target, stats);
                }
            }
            case "monster" -> {
                stats.monster = value;
                if (!value) {
                    stats.sanZeroTick = 0;
                }
                stats.sync();
            }
            case "sick" -> {
                if (value) {
                    net.exmo.sre.sixtyseconds.logic.SixtySecondsSicknessSystem.makeSick(target);
                } else {
                    net.exmo.sre.sixtyseconds.logic.SixtySecondsSicknessSystem.cure(target);
                }
            }
            default -> {
                return 0;
            }
        }
        source.sendSuccess(() -> Component.translatable("message.noellesroles.sixty_seconds.cmd_stat_set",
                target.getGameProfile().getName(), field, value), true);
        return 1;
    }

    private static int sacrifice(CommandSourceStack source) {
        if (!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            source.sendFailure(Component.literal("Only a player can sacrifice"));
            return 0;
        }
        if (!SixtySecondsMod.isActive(player.level())) {
            return 0;
        }
        return net.exmo.sre.sixtyseconds.logic.SixtySecondsMonsterSystem.sacrifice(player) ? 1 : 0;
    }

    private static int start(CommandSourceStack source, int minutes, boolean forceAll) {
        if (SixtySecondsMod.MODE == null) {
            source.sendFailure(Component.literal("60s mode not initialized"));
            return 0;
        }
        if (SREGameWorldComponent.KEY.get(source.getLevel()).isRunning()) {
            source.sendFailure(Component.translatable("game.start_error.game_running"));
            return 0;
        }

        // force_all_players: 将所有参与中的玩家（未 opt-out）强制纳入就绪列表，
        // 使其无论身处何地都能加入游戏
        if (forceAll) {
            ParticipationComponent participation = ParticipationComponent.KEY.get(source.getLevel());
            List<ServerPlayer> allPlayers = source.getLevel().getServer().getPlayerList().getPlayers();
            List<UUID> forcedReady = allPlayers.stream()
                    .filter(participation::isParticipating)
                    .map(ServerPlayer::getUUID)
                    .toList();
            GameUtils.setForcedReadyPlayers(forcedReady);
        }

        int resolved = minutes >= 0 ? minutes : SixtySecondsMod.MODE.defaultStartTime;
        GameUtils.startGame(source.getLevel(), SixtySecondsMod.MODE, GameConstants.getInTicks(resolved, 0));
        source.sendSuccess(() -> Component.translatable("commands.sre.start",
                SixtySecondsMod.MODE.toString(), resolved).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /** 管理员：切换「晚上自动刷新夜袭者」（按图配置持久化，默认关）。 */
    private static int setNightAssault(CommandSourceStack source, boolean enabled) {
        var level = source.getLevel();
        var config = net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore.current(level)
                .orElseGet(net.exmo.sre.sixtyseconds.config.SixtySecondsConfig::new);
        config.nightAssaultEnabled = enabled;
        net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore.save(level, config);
        source.sendSuccess(() -> Component.translatable(enabled
                ? "message.noellesroles.sixty_seconds.assault_enabled"
                : "message.noellesroles.sixty_seconds.assault_disabled").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /** 管理员：查看「晚上自动刷新夜袭者」当前状态。 */
    private static int showNightAssault(CommandSourceStack source) {
        boolean enabled = net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore.current(source.getLevel())
                .map(config -> config.nightAssaultEnabled).orElse(false);
        source.sendSuccess(() -> Component.translatable(enabled
                ? "message.noellesroles.sixty_seconds.assault_enabled"
                : "message.noellesroles.sixty_seconds.assault_disabled"), false);
        return 1;
    }

    /** 管理员：切换 PVE 开关（探索区游荡怪 + 夜晚 Boss，按图配置持久化，默认开）。 */
    private static int setPve(CommandSourceStack source, boolean enabled) {
        var level = source.getLevel();
        var config = net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore.current(level)
                .orElseGet(net.exmo.sre.sixtyseconds.config.SixtySecondsConfig::new);
        config.pveEnabled = enabled;
        net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore.save(level, config);
        source.sendSuccess(() -> Component.translatable(enabled
                ? "message.noellesroles.sixty_seconds.pve_enabled"
                : "message.noellesroles.sixty_seconds.pve_disabled").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /** 管理员：查看 PVE 开关当前状态。 */
    private static int showPve(CommandSourceStack source) {
        boolean enabled = net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore.current(source.getLevel())
                .map(config -> config.pveEnabled).orElse(true);
        source.sendSuccess(() -> Component.translatable(enabled
                ? "message.noellesroles.sixty_seconds.pve_enabled"
                : "message.noellesroles.sixty_seconds.pve_disabled"), false);
        return 1;
    }

    /**
     * 管理员：设置本局总游戏日数（按图配置持久化，默认 7）。
     * 已开局时立即生效：下发新的 totalDays 给客户端 HUD，并按新值重新判定是否已到最终日。
     */
    private static int setTotalDays(CommandSourceStack source, int days) {
        var level = source.getLevel();
        var config = net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore.current(level)
                .orElseGet(net.exmo.sre.sixtyseconds.config.SixtySecondsConfig::new);
        config.totalDays = days;
        net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore.save(level, config);
        // 局中改：把新总日数补发给在场玩家，HUD 的「第 X/N 天」立刻更新
        net.exmo.sre.sixtyseconds.logic.SixtySecondsManager.resyncTotalDays(level);
        source.sendSuccess(() -> Component.translatable(
                "message.noellesroles.sixty_seconds.days_set", days).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /** 管理员：查看本局总游戏日数。 */
    private static int showTotalDays(CommandSourceStack source) {
        int days = net.exmo.sre.sixtyseconds.logic.SixtySecondsManager.totalDays(source.getLevel());
        source.sendSuccess(() -> Component.translatable(
                "message.noellesroles.sixty_seconds.days_show", days), false);
        return 1;
    }

    /** 管理员：切换中途自动入队开关（新玩家自动补入未满队伍，按图配置持久化，默认开）。 */
    private static int setAutoJoin(CommandSourceStack source, boolean enabled) {
        var level = source.getLevel();
        var config = net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore.current(level)
                .orElseGet(net.exmo.sre.sixtyseconds.config.SixtySecondsConfig::new);
        config.autoJoinEnabled = enabled;
        net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore.save(level, config);
        source.sendSuccess(() -> Component.translatable(enabled
                ? "message.noellesroles.sixty_seconds.autojoin_enabled"
                : "message.noellesroles.sixty_seconds.autojoin_disabled").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /**
     * 管理员：切换「避难所生成在探索区出口门上」（按图配置持久化，默认开）。
     * 下一次建图生效；未登记锚点门时给出提示（建图会回退网格克隆）。
     */
    private static int setShelterAtDoor(CommandSourceStack source, boolean enabled) {
        var level = source.getLevel();
        var config = net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore.current(level)
                .orElseGet(net.exmo.sre.sixtyseconds.config.SixtySecondsConfig::new);
        config.shelterAtSearchDoorEnabled = enabled;
        net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore.save(level, config);
        source.sendSuccess(() -> Component.translatable(enabled
                ? "message.noellesroles.sixty_seconds.shelter_at_door_enabled"
                : "message.noellesroles.sixty_seconds.shelter_at_door_disabled").withStyle(ChatFormatting.GREEN), true);
        if (enabled && config.shelterAnchorDoor == null) {
            source.sendSuccess(() -> Component.translatable(
                    "message.noellesroles.sixty_seconds.shelter_at_door_no_anchor")
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        return 1;
    }

    /** 管理员：查看「避难所生成在探索区出口门上」当前状态。 */
    private static int showShelterAtDoor(CommandSourceStack source) {
        boolean enabled = net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore.current(source.getLevel())
                .map(config -> config.shelterAtSearchDoorEnabled).orElse(true);
        source.sendSuccess(() -> Component.translatable(enabled
                ? "message.noellesroles.sixty_seconds.shelter_at_door_enabled"
                : "message.noellesroles.sixty_seconds.shelter_at_door_disabled"), false);
        return 1;
    }

    /**
     * 管理员：切换「海图扬帆传送 / 返回住所」（按图配置持久化，默认关=玩家自己乘船去岛）。
     * 立即生效；关闭时把在途的扬帆/返航倒计时一并取消，海图重发以更新客户端按钮状态。
     */
    private static int setSeaTeleport(CommandSourceStack source, boolean enabled) {
        var level = source.getLevel();
        var config = net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore.current(level)
                .orElseGet(net.exmo.sre.sixtyseconds.config.SixtySecondsConfig::new);
        config.seaChartTeleportEnabled = enabled;
        net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore.save(level, config);
        net.exmo.sre.sixtyseconds.island.SixtySecondsIslands.onTeleportToggled(level, enabled);
        source.sendSuccess(() -> Component.translatable(enabled
                ? "message.noellesroles.sixty_seconds.sea_teleport_enabled"
                : "message.noellesroles.sixty_seconds.sea_teleport_disabled").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /** 管理员：查看「海图扬帆传送 / 返回住所」当前状态。 */
    private static int showSeaTeleport(CommandSourceStack source) {
        boolean enabled = net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore.current(source.getLevel())
                .map(config -> config.seaChartTeleportEnabled).orElse(false);
        source.sendSuccess(() -> Component.translatable(enabled
                ? "message.noellesroles.sixty_seconds.sea_teleport_enabled"
                : "message.noellesroles.sixty_seconds.sea_teleport_disabled"), false);
        return 1;
    }

    /** 管理员：查看中途自动入队开关当前状态。 */
    private static int showAutoJoin(CommandSourceStack source) {
        boolean enabled = net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore.current(source.getLevel())
                .map(config -> config.autoJoinEnabled).orElse(true);
        source.sendSuccess(() -> Component.translatable(enabled
                ? "message.noellesroles.sixty_seconds.autojoin_enabled"
                : "message.noellesroles.sixty_seconds.autojoin_disabled"), false);
        return 1;
    }

    /** 管理员：在自身位置立即刷指定等级 Boss（本模式进行中）。 */
    private static int spawnBoss(CommandSourceStack source, int bossLevel) {
        return spawnBoss(source, bossLevel, null);
    }

    /** 管理员：在自身位置刷指定等级+变体 Boss。variantName 为 null 或无法识别时默认破坏者。 */
    private static int spawnBoss(CommandSourceStack source, int bossLevel, String variantName) {
        if (!SixtySecondsMod.isActive(source.getLevel())) {
            source.sendFailure(Component.translatable("message.noellesroles.sixty_seconds.cmd_not_running"));
            return 0;
        }
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("player only"));
            return 0;
        }
        var variant = net.exmo.sre.sixtyseconds.entity.SixtySecondsBossEntity.BossVariant.RAVAGER;
        if (variantName != null) {
            try {
                variant = net.exmo.sre.sixtyseconds.entity.SixtySecondsBossEntity.BossVariant.valueOf(variantName.toUpperCase());
            } catch (IllegalArgumentException e) {
                source.sendFailure(Component.literal("未知 Boss 变体: " + variantName
                        + "。可用: ravager, colossus, necromancer, plaguebearer, specter"));
                return 0;
            }
        }
        var boss = net.exmo.sre.sixtyseconds.logic.SixtySecondsPveSystem.spawnBoss(
                source.getLevel(), player.blockPosition().relative(player.getDirection(), 4),
                bossLevel, false, variant);
        return boss != null ? 1 : 0;
    }

    /** 管理员：切换「开局保底物资」（按图配置持久化，默认关）。 */
    private static int setStarterSupplies(CommandSourceStack source, boolean enabled) {
        var level = source.getLevel();
        var config = net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore.current(level)
                .orElseGet(net.exmo.sre.sixtyseconds.config.SixtySecondsConfig::new);
        config.starterSuppliesEnabled = enabled;
        net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore.save(level, config);
        source.sendSuccess(() -> Component.translatable(enabled
                ? "message.noellesroles.sixty_seconds.starter_enabled"
                : "message.noellesroles.sixty_seconds.starter_disabled").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /** 管理员：查看「开局保底物资」当前状态。 */
    private static int showStarterSupplies(CommandSourceStack source) {
        boolean enabled = net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore.current(source.getLevel())
                .map(config -> config.starterSuppliesEnabled).orElse(false);
        source.sendSuccess(() -> Component.translatable(enabled
                ? "message.noellesroles.sixty_seconds.starter_enabled"
                : "message.noellesroles.sixty_seconds.starter_disabled"), false);
        return 1;
    }

    private static final String ISLAND_LANG = net.exmo.sre.sixtyseconds.island.SixtySecondsIsland.LANG;

    /**
     * 管理员：生成海岛群（异步）。centerX/centerZ/seaY/baseRadius 传 {@code Integer.MIN_VALUE} 用默认
     * （半径默认 {@code SixtySecondsIslandGenerator.DEFAULT_BASE_RADIUS}=340；实际半径=基准+等级×6+随机0..10）。
     */
    private static int islandStart(CommandSourceStack source, int count, int centerX, int centerZ, int seaY,
            int baseRadius) {
        var level = source.getLevel();
        // 默认落点：执行者脚下偏移一段（避免压在人头上），或世界固定远点
        int cx = centerX != Integer.MIN_VALUE ? centerX
                : source.getEntity() != null ? (int) source.getPosition().x : -3000;
        int cz = centerZ != Integer.MIN_VALUE ? centerZ
                : source.getEntity() != null ? (int) source.getPosition().z + 600 : 3000;
        int sea = seaY != Integer.MIN_VALUE ? seaY
                : source.getEntity() != null ? (int) source.getPosition().y : level.getSeaLevel();
        int radius = baseRadius != Integer.MIN_VALUE ? baseRadius : 0; // ≤0 = 用默认
        if (!net.exmo.sre.sixtyseconds.island.SixtySecondsIslands.start(level, count, cx, cz, sea, radius)) {
            source.sendFailure(Component.translatable(ISLAND_LANG + "start_fail"));
            return 0;
        }
        int shownRadius = radius > 0 ? radius
                : net.exmo.sre.sixtyseconds.island.SixtySecondsIslandGenerator.DEFAULT_BASE_RADIUS;
        source.sendSuccess(() -> Component.translatable(ISLAND_LANG + "start_ok_radius",
                count, cx, cz, sea, shownRadius).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /** 管理员：关闭海岛模式并回滚地形。 */
    private static int islandStop(CommandSourceStack source) {
        if (!net.exmo.sre.sixtyseconds.island.SixtySecondsIslands.stop(source.getLevel())) {
            source.sendFailure(Component.translatable(ISLAND_LANG + "stop_fail"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(ISLAND_LANG + "stop_ok")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /** 管理员：强制删除所有海岛数据（不清除地形方块），允许重新生成。 */
    private static int islandDelete(CommandSourceStack source) {
        if (!net.exmo.sre.sixtyseconds.island.SixtySecondsIslands.delete(source.getLevel())) {
            source.sendFailure(Component.translatable(ISLAND_LANG + "delete_fail"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(ISLAND_LANG + "delete_ok")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /** 打开海图（服务端下发数据并令客户端弹出界面；聊天栏点击也走这里）。 */
    private static int islandMap(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return 0;
        }
        net.exmo.sre.sixtyseconds.island.SixtySecondsIslands.syncChart(player, true);
        return 1;
    }

    /**
     * 海图「返回住所」：与海图按钮走同一条 {@code requestReturn} 路径——脱战/在登岛点附近/登岛冷却/
     * sea_teleport 开关等校验全部复用，通过后启动划船动画再传送（旧实现直接 returnPlayer，绕过了这些校验）。
     */
    private static int islandHome(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player) || !SixtySecondsMod.isActive(player.level())) {
            return 0;
        }
        net.exmo.sre.sixtyseconds.island.SixtySecondsIslands.requestReturn(player);
        return 1;
    }

    /** 扬帆前往指定岛（海图点击触发；服务端做解锁/位置/冷却校验）。 */
    private static int islandSail(CommandSourceStack source, int islandId) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return 0;
        }
        net.exmo.sre.sixtyseconds.island.SixtySecondsIslands.sail(player, islandId);
        return 1;
    }

    /** 管理员：列出所有岛（id/名字/等级/坐标，点击传送）。 */
    private static int islandList(CommandSourceStack source) {
        var data = net.exmo.sre.sixtyseconds.island.SixtySecondsIslands.get(source.getLevel());
        if (data.save.islands.isEmpty()) {
            source.sendFailure(Component.translatable(ISLAND_LANG + "none"));
            return 0;
        }
        for (var island : data.save.islands) {
            var line = Component.literal("§e#" + island.id + " ")
                    .append(island.name())
                    .append(Component.literal(" §7Lv." + island.level + " "))
                    .append(tpLink(ISLAND_LANG + "list_tp", island.dockPos(), ChatFormatting.AQUA));
            source.sendSuccess(() -> line, false);
        }
        return data.save.islands.size();
    }

    /** 管理员：给自己所在队解锁指定岛（测试情报链路用）。 */
    private static int islandUnlock(CommandSourceStack source, int islandId) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return 0;
        }
        var level = source.getLevel();
        var data = net.exmo.sre.sixtyseconds.island.SixtySecondsIslands.get(level);
        var island = net.exmo.sre.sixtyseconds.island.SixtySecondsIslands.byId(data, islandId);
        if (island == null) {
            source.sendFailure(Component.translatable(ISLAND_LANG + "none"));
            return 0;
        }
        int teamId = net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent.KEY.get(player).teamId;
        if (teamId < 0) {
            source.sendFailure(Component.literal("no team"));
            return 0;
        }
        net.exmo.sre.sixtyseconds.island.SixtySecondsIslands.unlockForTeam(level, teamId, island,
                Component.translatable(ISLAND_LANG + "unlocked_by_intel", island.name(), island.level));
        return 1;
    }

    /** 管理员：强制给予指定玩家生病状态。 */
    private static int forceSick(CommandSourceStack source, ServerPlayer target) {
        net.exmo.sre.sixtyseconds.logic.SixtySecondsSicknessSystem.makeSick(target);
        source.sendSuccess(() -> Component.translatable("message.noellesroles.sixty_seconds.cmd_sick",
                target.getGameProfile().getName()).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /** 管理员：强制治愈指定玩家（清除生病和感染风险）。 */
    private static int forceCure(CommandSourceStack source, ServerPlayer target) {
        net.exmo.sre.sixtyseconds.logic.SixtySecondsSicknessSystem.cure(target);
        source.sendSuccess(() -> Component.translatable("message.noellesroles.sixty_seconds.cmd_cure",
                target.getGameProfile().getName()).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // ═══════════════════════════════════════════════════════════
    // 热线电话命令回调
    // ═══════════════════════════════════════════════════════════

    private static int hotlineExpressSend(CommandSourceStack src) {
        if (!(src.getEntity() instanceof ServerPlayer p) || !SixtySecondsMod.isActive(p.level())) return 0;
        net.exmo.sre.sixtyseconds.logic.SixtySecondsHotlineSystem.handleExpressSend(p);
        return 1;
    }
    private static int hotlineExpressCancel(CommandSourceStack src) {
        if (!(src.getEntity() instanceof ServerPlayer p) || !SixtySecondsMod.isActive(p.level())) return 0;
        net.exmo.sre.sixtyseconds.logic.SixtySecondsHotlineSystem.handleExpressCancel(p);
        return 1;
    }
    private static int hotlineExpressTeam(CommandSourceStack src, int team) {
        if (!(src.getEntity() instanceof ServerPlayer p) || !SixtySecondsMod.isActive(p.level())) return 0;
        net.exmo.sre.sixtyseconds.logic.SixtySecondsHotlineSystem.handleExpressTeam(p, team);
        return 1;
    }
    private static int hotlineShopBuy(CommandSourceStack src, int index) {
        if (!(src.getEntity() instanceof ServerPlayer p) || !SixtySecondsMod.isActive(p.level())) return 0;
        net.exmo.sre.sixtyseconds.logic.SixtySecondsHotlineSystem.handleShopBuy(p, index);
        return 1;
    }
    private static int hotlineShopCancel(CommandSourceStack src) {
        if (!(src.getEntity() instanceof ServerPlayer p) || !SixtySecondsMod.isActive(p.level())) return 0;
        net.exmo.sre.sixtyseconds.logic.SixtySecondsHotlineSystem.handleShopCancel(p);
        return 1;
    }
    private static int hotlineRescueRequest(CommandSourceStack src) {
        if (!(src.getEntity() instanceof ServerPlayer p) || !SixtySecondsMod.isActive(p.level())) return 0;
        net.exmo.sre.sixtyseconds.logic.SixtySecondsHotlineSystem.handleRescueRequest(p);
        return 1;
    }
    private static int hotlineRescueCancel(CommandSourceStack src) {
        if (!(src.getEntity() instanceof ServerPlayer p) || !SixtySecondsMod.isActive(p.level())) return 0;
        net.exmo.sre.sixtyseconds.logic.SixtySecondsHotlineSystem.handleRescueCancel(p);
        return 1;
    }
}
