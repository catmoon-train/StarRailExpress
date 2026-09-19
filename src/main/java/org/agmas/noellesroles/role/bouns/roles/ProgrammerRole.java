package org.agmas.noellesroles.role.bouns.roles;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.agmas.noellesroles.init.FunnyItems;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.bouns.BounsRoles;
import org.agmas.noellesroles.utils.RoleUtils;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.EggRole;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import org.agmas.noellesroles.utils.MoneyUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 程序员：彩蛋 · 杀手阵营职业。
 *
 * <p>
 * 本职业的规则全部集中在这个类里，方便后续调试与修改：
 * <ul>
 * <li>商店：{@link #getShopEntries()}（默认刀具条目 + 终端）与价格 {@link #TERMINAL_PRICE}</li>
 * <li>终端的准入校验：{@link #canUseTerminal(Player)} /
 * {@link #isHoldingTerminal(Player)}</li>
 * <li>终端指令的解析与执行：{@link #parseTerminalCommand(String)} /
 * {@link #executeTerminalCommand(ServerPlayer, String)}</li>
 * </ul>
 * 物品（{@link org.agmas.noellesroles.content.item.TerminalItem}）、数据包接收器、
 * 客户端界面都只做薄转发，改数值或改终端白名单只需要动这一个文件。
 *
 * <p>
 * 终端是消耗品：成功执行一条指令后就被销毁（{@link #consumeTerminal(Player)}）。
 * 因为「用没用过」这个状态由物品自己承载，所以本职业不建 RoleData；
 * 以后若要给职业加每人状态/冷却/次数，再补一个 XxxRoleData extends SimpleRoleData 即可。
 */
public class ProgrammerRole extends EggRole {

    /** 终端的购买价格（金币） */
    public static final int TERMINAL_PRICE = 175;

    /**
     * 正确执行一条指令后的冷却时间（秒）。
     * 走原版物品冷却：冷却期间既不能再打开终端界面，也不能立刻用新买的终端，
     * 所以「用掉终端 → 再买一个 → 马上再来一发」这条路也被堵住了。
     */
    public static final int TERMINAL_COOLDOWN_SECONDS = 120;

    /** 指令**输错**时的冷却（秒）：比正常冷却短得多，输错不至于直接报废一局 */
    public static final int TERMINAL_MISTAKE_COOLDOWN_SECONDS = 10;

    /** {@code /kill @r} 命中**自己**的概率（百分比）；其余概率随机清除一名其他存活玩家。 */
    public static final int KILL_RANDOM_SELF_PERCENT = 5;

    /**
     * {@code /tmm:money set <金额>} 相对当前余额的最大增量。
     * <p>
     * 也就是把余额最多设成「当前金币 + 200」；扣除终端售价 150 后一次净赚 25，
     * 所以这条指令能刷钱但刷不快。
     */
    public static final int TERMINAL_MONEY_GAIN_LIMIT = 200;

    /**
     * 终端可生成的物品白名单：指令里的物品ID → 物品。
     * 想增加终端可生成的物品，在这里加一行即可（界面上的提示会自动跟着变）。
     */
    private static final Map<String, Item> TERMINAL_ITEM_POOL = new LinkedHashMap<>();

    static {
        // ---- 杀伤性（原本就有）----
        TERMINAL_ITEM_POOL.put("trainmurdermystery:revolver", TMMItems.REVOLVER);
        TERMINAL_ITEM_POOL.put("trainmurdermystery:knife", TMMItems.KNIFE);
        // ---- 非杀伤性工具：价格参考各自商店定价，与终端（150）同档 ----
        TERMINAL_ITEM_POOL.put("trainmurdermystery:body_bag", TMMItems.BODY_BAG); // 裹尸袋 100
        TERMINAL_ITEM_POOL.put("trainmurdermystery:lockpick", TMMItems.LOCKPICK); // 开锁器 80
        TERMINAL_ITEM_POOL.put("noellesroles:handcuffs", ModItems.HANDCUFFS); // 手铐 150
        TERMINAL_ITEM_POOL.put("noellesroles:master_key_p", ModItems.MASTER_KEY_P); // 乘务员钥匙 100
        TERMINAL_ITEM_POOL.put("noellesroles:radio", ModItems.RADIO); // 对讲机 150
        TERMINAL_ITEM_POOL.put("noellesroles:flash_grenade", ModItems.FLASH_GRENADE); // 闪光弹 125
        TERMINAL_ITEM_POOL.put("noellesroles:smoke_grenade", ModItems.SMOKE_GRENADE); // 烟雾弹 175
        TERMINAL_ITEM_POOL.put("noellesroles:monitoring_terminal", ModItems.MONITORING_TERMINAL); // 远程监控终端 150
        TERMINAL_ITEM_POOL.put("noellesroles:wheelchair", ModItems.WHEELCHAIR); // 轮椅 100
    }

    public ProgrammerRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    // ==================== 商店 ====================

    /**
     * 专属商店：杀手默认刀具条目 + 终端。
     * 终端是消耗品，用掉之后可以再花金币买一个。
     */
    @Override
    public List<ShopEntry> getShopEntries() {
        List<ShopEntry> shop = ShopContent.getDefaultKnifeEntries();
        shop.add(new ShopEntry(FunnyItems.TERMINAL.getDefaultInstance(), TERMINAL_PRICE, ShopEntry.Type.TOOL));
        return shop;
    }

    // ==================== 终端白名单 ====================

    /** 终端可生成的物品ID列表（客户端界面用来提示与点击填入，双端安全） */
    public static List<String> getTerminalItemIds() {
        return List.copyOf(TERMINAL_ITEM_POOL.keySet());
    }

    /** 指令里的物品ID → 物品；容忍带不带命名空间、大小写。不在白名单里返回 null */
    private static Item findTerminalItem(String input) {
        String id = input.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Item> entry : TERMINAL_ITEM_POOL.entrySet()) {
            String fullId = entry.getKey();
            if (fullId.equals(id) || fullId.substring(fullId.indexOf(':') + 1).equals(id)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 终端 {@code /help} 的清单（客户端画进终端日志；服务端不用它）。
     * 指令与物品都从这里取，所以往白名单里加物品时帮助内容会自动更新。
     */
    public static List<Component> getTerminalHelpLines() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("screen.noellesroles.terminal.help.header").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("screen.noellesroles.terminal.help.give_usage")
                .withStyle(ChatFormatting.WHITE));
        for (Map.Entry<String, Item> entry : TERMINAL_ITEM_POOL.entrySet()) {
            lines.add(Component.translatable("screen.noellesroles.terminal.help.item_line", entry.getKey(),
                    entry.getValue().getDefaultInstance().getHoverName()).withStyle(ChatFormatting.GREEN));
        }
        lines.add(Component.translatable("screen.noellesroles.terminal.help.tp_usage")
                .withStyle(ChatFormatting.WHITE));
        lines.add(Component.translatable("screen.noellesroles.terminal.help.tp_player_usage")
                .withStyle(ChatFormatting.WHITE));
        lines.add(Component.translatable("screen.noellesroles.terminal.help.effect_usage")
                .withStyle(ChatFormatting.WHITE));
        lines.add(Component.translatable("screen.noellesroles.terminal.help.kill_usage")
                .withStyle(ChatFormatting.WHITE));
        lines.add(Component.translatable("screen.noellesroles.terminal.help.kill_random_usage")
                .withStyle(ChatFormatting.WHITE));
        lines.add(Component.translatable("screen.noellesroles.terminal.help.blackout_usage")
                .withStyle(ChatFormatting.WHITE));
        lines.add(Component.translatable("screen.noellesroles.terminal.help.monitor_usage")
                .withStyle(ChatFormatting.WHITE));
        lines.add(Component.translatable("screen.noellesroles.terminal.help.money_usage", TERMINAL_MONEY_GAIN_LIMIT)
                .withStyle(ChatFormatting.WHITE));
        lines.add(Component.translatable("screen.noellesroles.terminal.help.help_usage")
                .withStyle(ChatFormatting.WHITE));
        return lines;
    }

    // ==================== 终端指令解析 ====================

    /** 终端指令类型 */
    public enum TerminalCommandType {
        /** 生成物品 */
        GIVE,
        /** 传送回自己的房间 */
        TELEPORT_ROOM,
        /** 传送到随机一名存活玩家身上（位置重合） */
        TELEPORT_PLAYER,
        /** 清除自身的药水效果 */
        EFFECT_CLEAR,
        /** 杀死自己（死因：代码死亡） */
        SUICIDE,
        /** 随机事故：5% 杀自己，95% 杀一名随机存活玩家（含队友） */
        KILL_RANDOM,
        /** 切断全场照明（模拟 /tmm:game blackout） */
        BLACKOUT,
        /** 让所有监控失灵（模拟 /tmm:game monitor_broken） */
        MONITOR_BLACKOUT,
        /** 把自己的金币设成指定值（模拟 /tmm:money set <金额>） */
        MONEY_SET,
        /** 列出所有可用指令（客户端本地处理） */
        HELP
    }

    /** 解析后的终端指令；{@link #errorKey()} 为 null 表示合法。{@link #amount()} 仅供需要数值参数的指令使用 */
    public record TerminalCommand(TerminalCommandType type, Item item, String errorKey, int amount) {
        /** 大多数指令不带数值参数 */
        public TerminalCommand(TerminalCommandType type, Item item, String errorKey) {
            this(type, item, errorKey, 0);
        }

        public boolean isValid() {
            return errorKey == null;
        }
    }

    /**
     * 归一化输入：全角空格/NBSP 换成半角，并去掉首尾空白。
     * 中文输入法很容易在指令前后带出空格，前后多打的空格不应该影响解析。
     */
    public static String normalizeTerminalInput(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace('\u3000', ' ').replace('\u00a0', ' ').trim();
    }

    /**
     * 解析终端指令。纯逻辑、双端可用：客户端用它识别 {@code /help}，服务端用它执行。
     *
     * <p>
     * 支持（前导 / 可省略、大小写不敏感、首尾空格自动忽略）：
     * <ul>
     * <li>{@code /give @s <物品ID>} —— 生成白名单内的物品</li>
     * <li>{@code /tp @s room} —— 传送回自己的房间</li>
     * <li>{@code /tp @s @r} —— 传送到随机一名存活玩家身上（位置重合）</li>
     * <li>{@code /effect clear @s} —— 清除自身全部药水效果</li>
     * <li>{@code /kill @s} —— 杀死自己（死因：代码死亡）</li>
     * <li>{@code /tmm:game blackout} —— 切断全场照明</li>
     * <li>{@code /tmm:game monitor_broken} —— 让所有监控失灵</li>
     * <li>{@code /help} —— 列出所有可用指令（只对程序员开放）</li>
     * </ul>
     * 写法与真实调试指令一致（没有额外的子参数，也不接受多余参数）。
     *
     * @return 解析结果，{@link TerminalCommand#errorKey()} 是给玩家看的错误提示翻译键
     */
    public static TerminalCommand parseTerminalCommand(String raw) {
        String text = normalizeTerminalInput(raw);
        if (text.isEmpty()) {
            return new TerminalCommand(null, null, "message.noellesroles.terminal.error.usage");
        }
        if (text.startsWith("/")) {
            text = text.substring(1).trim();
        }
        String[] args = text.split("\\s+");

        // 0) /help —— 列出所有可用指令（客户端本地处理，不发包、不消耗终端）
        if (args.length == 1 && "help".equalsIgnoreCase(args[0])) {
            return new TerminalCommand(TerminalCommandType.HELP, null, null);
        }

        // 0.5) /effect clear @s —— 清除自身全部药水效果
        if (args.length == 3 && "effect".equalsIgnoreCase(args[0]) && "clear".equalsIgnoreCase(args[1])
                && "@s".equalsIgnoreCase(args[2])) {
            return new TerminalCommand(TerminalCommandType.EFFECT_CLEAR, null, null);
        }

        // 1) 模拟原版调试指令：/tmm:game blackout、/tmm:game monitor_broken（与真实指令完全一致的写法）
        if (args.length >= 2 && "tmm:game".equalsIgnoreCase(args[0])) {
            if (args.length != 2) {
                return new TerminalCommand(null, null, "message.noellesroles.terminal.error.usage");
            }
            return switch (args[1].toLowerCase(Locale.ROOT)) {
                case "blackout" -> new TerminalCommand(TerminalCommandType.BLACKOUT, null, null);
                case "monitor_broken" -> new TerminalCommand(TerminalCommandType.MONITOR_BLACKOUT, null, null);
                default -> new TerminalCommand(null, null, "message.noellesroles.terminal.error.unknown_command");
            };
        }

        // 1.5) /kill —— 自杀（等价于 /kill @s）；/kill @r —— 随机事故：5% 杀自己，95% 杀一名随机存活玩家（含队友）。
        // /kill @r 的参数是 @r 而不是 @s，所以要放在下面那条统一的 @s 校验之前。
        if ("kill".equalsIgnoreCase(args[0])) {
            if (args.length == 1) {
                return new TerminalCommand(TerminalCommandType.SUICIDE, null, null);
            }
            if (args.length == 2 && "@r".equalsIgnoreCase(args[1])) {
                return new TerminalCommand(TerminalCommandType.KILL_RANDOM, null, null);
            }
        }

        // 1.6) /tmm:money set <金额> —— 把自己的金币设成该值，最多比当前多 TERMINAL_MONEY_GAIN_LIMIT。
        // 超过上限时这里仍返回合法指令，由 executeTerminalCommand 拒绝执行并且**不消耗终端**。
        if (args.length == 3 && "tmm:money".equalsIgnoreCase(args[0]) && "set".equalsIgnoreCase(args[1])) {
            int amount;
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                return new TerminalCommand(null, null, "message.noellesroles.terminal.error.usage");
            }
            return new TerminalCommand(TerminalCommandType.MONEY_SET, null, null, amount);
        }

        // 2) 其余指令统一要求 @s 形式
        if (args.length < 2 || !"@s".equalsIgnoreCase(args[1])) {
            return new TerminalCommand(null, null, "message.noellesroles.terminal.error.usage");
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> {
                if (args.length != 3) {
                    return new TerminalCommand(null, null, "message.noellesroles.terminal.error.usage");
                }
                Item item = findTerminalItem(args[2]);
                if (item == null) {
                    return new TerminalCommand(null, null, "message.noellesroles.terminal.error.invalid_item");
                }
                return new TerminalCommand(TerminalCommandType.GIVE, item, null);
            }
            case "tp" -> {
                if (args.length != 3) {
                    return new TerminalCommand(null, null, "message.noellesroles.terminal.error.usage");
                }
                if ("room".equalsIgnoreCase(args[2])) {
                    return new TerminalCommand(TerminalCommandType.TELEPORT_ROOM, null, null);
                }
                if ("@r".equalsIgnoreCase(args[2])) {
                    return new TerminalCommand(TerminalCommandType.TELEPORT_PLAYER, null, null);
                }
                return new TerminalCommand(null, null, "message.noellesroles.terminal.error.usage");
            }
            case "kill" -> {
                if (args.length != 2) {
                    return new TerminalCommand(null, null, "message.noellesroles.terminal.error.usage");
                }
                return new TerminalCommand(TerminalCommandType.SUICIDE, null, null);
            }
            default -> {
                return new TerminalCommand(null, null, "message.noellesroles.terminal.error.unknown_command");
            }
        }
    }

    // ==================== 终端使用 ====================

    /**
     * 该玩家能不能用终端。
     * 不要求是程序员（谁捡到/被塞了终端都能用，非程序员只是看不到 {@code /help}），
     * 但旁观者（含死亡后变旁观的人）不行。
     */
    public static boolean canUseTerminal(Player player) {
        return player != null && !player.isSpectator();
    }

    /** 是不是程序员：终端里的 {@code /help} 只给他看（其他人只能自己背指令） */
    public static boolean isProgrammer(Player player) {
        if (player == null) {
            return false;
        }
        return SREGameWorldComponent.KEY.get(player.level()).isRole(player, BounsRoles.PROGRAMMER);
    }

    /** 有没有正拿着终端（主手或副手） */
    public static boolean isHoldingTerminal(Player player) {
        return player != null && (player.getMainHandItem().is(FunnyItems.TERMINAL)
                || player.getOffhandItem().is(FunnyItems.TERMINAL));
    }

    /** 终端是不是还在冷却里（原版物品冷却，新买到的终端同样受影响） */
    public static boolean isTerminalOnCooldown(Player player) {
        return player != null && player.getCooldowns().isOnCooldown(FunnyItems.TERMINAL);
    }

    /** 终端冷却剩余秒数（向上取整），拿来写提示 */
    public static int getTerminalCooldownSecondsLeft(Player player) {
        if (player == null) {
            return 0;
        }
        var instance = player.getCooldowns().cooldowns.get(FunnyItems.TERMINAL);
        if (instance == null) {
            return 0;
        }
        int ticksLeft = Math.max(0, instance.endTime - player.getCooldowns().tickCount);
        return (ticksLeft + 19) / 20;
    }

    /** 销毁一个手持的终端（终端只能执行一条指令） */
    public static boolean consumeTerminal(Player player) {
        if (player == null) {
            return false;
        }
        if (player.getMainHandItem().is(FunnyItems.TERMINAL)) {
            player.getMainHandItem().shrink(1);
            return true;
        }
        if (player.getOffhandItem().is(FunnyItems.TERMINAL)) {
            player.getOffhandItem().shrink(1);
            return true;
        }
        return false;
    }

    /**
     * 终端「被用掉」：销毁一个终端并进入冷却。
     * 创造模式不计冷却（方便调试/测试）；指令输错用的是很短的冷却。
     *
     * @param mistake 本次是不是「指令输错」
     */
    private static void finishTerminal(ServerPlayer player, boolean mistake) {
        consumeTerminal(player);
        if (player.isCreative()) {
            return;
        }
        player.getCooldowns().addCooldown(FunnyItems.TERMINAL,
                GameConstants.getInTicks(0,
                        mistake ? TERMINAL_MISTAKE_COOLDOWN_SECONDS : TERMINAL_COOLDOWN_SECONDS));
    }

    /**
     * 服务端执行一条终端指令（由 TerminalCommandC2SPacket 的接收器薄转发进来）。
     *
     * <p>
     * 消耗规则：
     * <ul>
     * <li>指令**输错**（格式错误 / 未知指令 / 物品不在白名单）→ 照样销毁终端，只在聊天栏说明原因（冷却只有 10 秒）；</li>
     * <li>指令合法但当前状态执行不了（灯已经关着、监控已经失灵、本局没分配房间）→ **不消耗**，可以改指令重试；</li>
     * <li>{@code /help} 由客户端本地展开，不会进到这里，因此不消耗。</li>
     * </ul>
     * 消耗终端的同时写入物品冷却（正常 2 分钟，输错 10 秒，创造模式不计）。
     *
     * @return true 表示终端已被销毁
     */
    public static boolean executeTerminalCommand(ServerPlayer player, String raw) {
        if (!canUseTerminal(player)) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.terminal.spectator")
                            .withStyle(ChatFormatting.RED),
                    false);
            return false;
        }
        if (!isHoldingTerminal(player)) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.terminal.not_holding").withStyle(ChatFormatting.RED),
                    false);
            return false;
        }
        TerminalCommand command = parseTerminalCommand(raw);
        if (!command.isValid()) {
            // 输错也烧掉终端（/help 不走这里），但只给很短的冷却
            player.displayClientMessage(Component.translatable(command.errorKey()).withStyle(ChatFormatting.RED),
                    false);
            finishTerminal(player, true);
            return true;
        }
        boolean success = switch (command.type()) {
            case GIVE -> {
                ItemStack stack = command.item().getDefaultInstance();
                // 和原版 /give 一样：背包塞不下就掉在脚边
                RoleUtils.insertOrDropItem(player, stack);
                player.displayClientMessage(Component
                        .translatable("message.noellesroles.terminal.give_success", stack.getHoverName())
                        .withStyle(ChatFormatting.GREEN), false);
                yield true;
            }
            case TELEPORT_ROOM -> {
                // 没有分配房间时 teleportBackToRoom 会静默失败（甚至把人变旁观），这里先拦下来，别白扔一个终端
                if (!GameUtils.roomToPlayer.containsKey(player.getUUID())) {
                    player.displayClientMessage(
                            Component.translatable("message.noellesroles.terminal.no_room")
                                    .withStyle(ChatFormatting.RED),
                            false);
                    yield false;
                }
                GameUtils.teleportBackToRoom(player);
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.terminal.tp_success")
                                .withStyle(ChatFormatting.GREEN),
                        false);
                yield true;
            }
            case TELEPORT_PLAYER -> {
                // 随机挑一名其他存活玩家，直接重合到他身上
                List<ServerPlayer> candidates = player.serverLevel().players().stream()
                        .filter(other -> !other.getUUID().equals(player.getUUID()))
                        .filter(GameUtils::isPlayerAliveAndSurvival)
                        .toList();
                if (candidates.isEmpty()) {
                    player.displayClientMessage(
                            Component.translatable("message.noellesroles.terminal.tp_player_none")
                                    .withStyle(ChatFormatting.RED),
                            false);
                    yield false;
                }
                ServerPlayer target = candidates.get(player.getRandom().nextInt(candidates.size()));
                player.stopRiding();
                player.stopSleeping();
                player.teleportTo(target.getX(), target.getY(), target.getZ());
                player.displayClientMessage(Component
                        .translatable("message.noellesroles.terminal.tp_player_success", target.getName())
                        .withStyle(ChatFormatting.GREEN), false);
                yield true;
            }
            case EFFECT_CLEAR -> {
                RoleUtils.removeAllEffects(player);
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.terminal.effect_clear_success")
                                .withStyle(ChatFormatting.GREEN),
                        false);
                yield true;
            }
            case SUICIDE -> {
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.terminal.suicide").withStyle(ChatFormatting.RED),
                        false);
                // 走正常死亡流程（会生成尸体、记录回放），死因用新增的「代码死亡」
                GameUtils.killPlayer(player, true, null, GameConstants.DeathReasons.CODE_DEATH);
                yield true;
            }
            case BLACKOUT -> {
                // 复用商店关灯的同一套逻辑（含全局冷却、音效、回放记录）
                if (!SREPlayerShopComponent.useBlackout(player)) {
                    player.displayClientMessage(
                            Component.translatable("message.noellesroles.terminal.blackout_already")
                                    .withStyle(ChatFormatting.RED),
                            false);
                    yield false;
                }
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.terminal.blackout_success")
                                .withStyle(ChatFormatting.GREEN),
                        false);
                yield true;
            }
            case MONITOR_BLACKOUT -> {
                if (!SREPlayerShopComponent.useMonitorBroken(player,
                        SREConfig.instance().monitorBrokenDuration * 20)) {
                    player.displayClientMessage(
                            Component.translatable("message.noellesroles.terminal.monitor_blackout_already")
                                    .withStyle(ChatFormatting.RED),
                            false);
                    yield false;
                }
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.terminal.monitor_blackout_success")
                                .withStyle(ChatFormatting.GREEN),
                        false);
                yield true;
            }
            case KILL_RANDOM -> {
                // 5% 炸自己，95% 炸一名随机存活玩家（含队友，但随机池里不含自己）
                ServerPlayer victim = player;
                if (player.getRandom().nextInt(100) >= KILL_RANDOM_SELF_PERCENT) {
                    List<ServerPlayer> candidates = player.serverLevel().players().stream()
                            .filter(other -> !other.getUUID().equals(player.getUUID()))
                            .filter(GameUtils::isPlayerAliveAndSurvival)
                            .toList();
                    if (candidates.isEmpty()) {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.terminal.kill_random_none")
                                        .withStyle(ChatFormatting.RED),
                                false);
                        // 没人可炸：不消耗终端（与 /tp @r 找不到人时一致）
                        yield false;
                    }
                    victim = candidates.get(player.getRandom().nextInt(candidates.size()));
                }
                if (victim == player) {
                    player.displayClientMessage(
                            Component.translatable("message.noellesroles.terminal.kill_random_self")
                                    .withStyle(ChatFormatting.RED),
                            false);
                } else {
                    player.displayClientMessage(
                            Component.translatable("message.noellesroles.terminal.kill_random_other",
                                    victim.getName()).withStyle(ChatFormatting.RED),
                            false);
                    victim.displayClientMessage(
                            Component.translatable("message.noellesroles.terminal.kill_random_victim")
                                    .withStyle(ChatFormatting.RED),
                            false);
                }
                // 不记击杀归属：这是终端随机事故（死因沿用「代码死亡」），
                // 否则随机砸到队友会被算成击杀方
                GameUtils.killPlayer(victim, true, null, GameConstants.DeathReasons.CODE_DEATH);
                yield true;
            }
            case MONEY_SET -> {
                // 目标值必须落在 [0, 当前金币 + 上限]：超限只报错、不消耗终端（返回 false 即不结算终端）
                int max = MoneyUtils.getBalance(player) + TERMINAL_MONEY_GAIN_LIMIT;
                if (command.amount() < 0 || command.amount() > max) {
                    player.displayClientMessage(
                            Component.translatable("message.noellesroles.terminal.money_over_limit", max)
                                    .withStyle(ChatFormatting.RED),
                            false);
                    yield false;
                }
                MoneyUtils.setBalance(player, command.amount());
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.terminal.money_success", command.amount())
                                .withStyle(ChatFormatting.GREEN),
                        false);
                yield true;
            }
            case HELP -> {
                // /help 由客户端本地展开成日志，正常不会发到服务端；万一发过来了也不消耗终端
                yield false;
            }
        };
        if (success) {
            recordTerminalCommand(player, raw);
            finishTerminal(player, false);
        }
        return success;
    }

    /**
     * 回放：记录程序员用终端**正确执行**的指令（输错的指令与 {@code /help} 不记录）。
     * <p>
     * 文本写法对齐 {@code GameReplayManager#recordSkillUsed}：玩家显示名 + 白色高亮的指令内容。
     */
    private static void recordTerminalCommand(ServerPlayer player, String raw) {
        if (SRE.REPLAY_MANAGER == null) {
            return;
        }
        String command = normalizeTerminalInput(raw);
        if (command.startsWith("/")) {
            command = command.substring(1).trim();
        }
        SRE.REPLAY_MANAGER.recordCustomEvent(
                Component.translatable("sre.replay.event.terminal_command",
                        GameReplayUtils.getReplayPlayerDisplayText(player, true),
                        Component.literal(command).withStyle(ChatFormatting.WHITE)));
    }

    /**
     * 该玩家能不能取下别人的手铐（巡警队/黑警之外的第三种人：程序员）。
     * 逻辑集中在这里，判定点见 {@code NRInteractionEvents} 的手铐交互回调。
     */
    public static boolean canUncuffOthers(Player player) {
        if (player == null) {
            return false;
        }
        return SREGameWorldComponent.KEY.get(player.level()).isRole(player, BounsRoles.PROGRAMMER);
    }
}
