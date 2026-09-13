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
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
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
 * <li>终端的准入校验：{@link #canUseTerminal(Player)} / {@link #isHoldingTerminal(Player)}</li>
 * <li>终端指令的解析与执行：{@link #parseTerminalCommand(String)} / {@link #executeTerminalCommand(ServerPlayer, String)}</li>
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
    public static final int TERMINAL_PRICE = 150;

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
        lines.add(Component.translatable("screen.noellesroles.terminal.help.blackout_usage")
                .withStyle(ChatFormatting.WHITE));
        lines.add(Component.translatable("screen.noellesroles.terminal.help.monitor_usage")
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
        /** 切断全场照明（模拟 /tmm:game blackout trigger） */
        BLACKOUT,
        /** 让所有监控失灵（模拟 /tmm:game monitor_blackout trigger） */
        MONITOR_BLACKOUT,
        /** 列出所有可用指令（客户端本地处理） */
        HELP
    }

    /** 解析后的终端指令；{@link #errorKey()} 为 null 表示合法 */
    public record TerminalCommand(TerminalCommandType type, Item item, String errorKey) {
        public boolean isValid() {
            return errorKey == null;
        }
    }

    /**
     * 解析终端指令。纯逻辑、双端可用：客户端拿它做本地预校验（不合法的指令根本不发包，
     * 终端也不会被白白消耗），服务端拿它执行。
     *
     * <p>
     * 支持（前导 / 可省略、大小写不敏感）：
     * <ul>
     * <li>{@code /give @s <物品ID>} —— 生成白名单内的物品</li>
     * <li>{@code /tp @s room} —— 传送回自己的房间</li>
     * <li>{@code /tmm:game blackout trigger} —— 切断全场照明</li>
     * <li>{@code /tmm:game monitor_blackout trigger} —— 让所有监控失灵（也接受 {@code monitor_broken}）</li>
     * </ul>
     * 结尾的 {@code trigger} 可写可不写（原版调试指令本身没有这个子参数，这里只是让输入更像一条指令）。
     *
     * @return 解析结果，{@link TerminalCommand#errorKey()} 是给玩家看的错误提示翻译键
     */
    public static TerminalCommand parseTerminalCommand(String raw) {
        if (raw == null) {
            return new TerminalCommand(null, null, "message.noellesroles.terminal.error.usage");
        }
        String text = raw.trim();
        if (text.startsWith("/")) {
            text = text.substring(1).trim();
        }
        String[] args = text.split("\\s+");

        // 0) /help —— 列出所有可用指令（客户端本地处理，不发包、不消耗终端）
        if (args.length == 1 && "help".equalsIgnoreCase(args[0])) {
            return new TerminalCommand(TerminalCommandType.HELP, null, null);
        }

        // 1) 模拟原版调试指令：/tmm:game <子命令> [trigger]
        if (args.length >= 2 && "tmm:game".equalsIgnoreCase(args[0])) {
            if (args.length > 2 && !"trigger".equalsIgnoreCase(args[args.length - 1])) {
                return new TerminalCommand(null, null, "message.noellesroles.terminal.error.usage");
            }
            return switch (args[1].toLowerCase(Locale.ROOT)) {
                case "blackout" -> new TerminalCommand(TerminalCommandType.BLACKOUT, null, null);
                case "monitor_blackout", "monitor_broken" ->
                    new TerminalCommand(TerminalCommandType.MONITOR_BLACKOUT, null, null);
                default -> new TerminalCommand(null, null, "message.noellesroles.terminal.error.unknown_command");
            };
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
                if (args.length != 3 || !"room".equalsIgnoreCase(args[2])) {
                    return new TerminalCommand(null, null, "message.noellesroles.terminal.error.usage");
                }
                return new TerminalCommand(TerminalCommandType.TELEPORT_ROOM, null, null);
            }
            default -> {
                return new TerminalCommand(null, null, "message.noellesroles.terminal.error.unknown_command");
            }
        }
    }

    // ==================== 终端使用 ====================

    /**
     * 该玩家是不是「能使用终端的程序员」：必须是本职业且存活。
     * 终端物品用它决定能不能开界面，指令执行前也用它再校验一次。
     */
    public static boolean canUseTerminal(Player player) {
        if (player == null || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }
        return SREGameWorldComponent.KEY.get(player.level()).isRole(player, BounsRoles.PROGRAMMER);
    }

    /** 有没有正拿着终端（主手或副手） */
    public static boolean isHoldingTerminal(Player player) {
        return player != null && (player.getMainHandItem().is(FunnyItems.TERMINAL)
                || player.getOffhandItem().is(FunnyItems.TERMINAL));
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
     * 服务端执行一条终端指令（由 TerminalCommandC2SPacket 的接收器薄转发进来）。
     * 校验 → 执行 → 成功则销毁终端。
     *
     * @return true 表示指令执行成功（此时终端已被销毁）
     */
    public static boolean executeTerminalCommand(ServerPlayer player, String raw) {
        if (!canUseTerminal(player)) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.terminal.not_programmer")
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
            player.displayClientMessage(Component.translatable(command.errorKey()).withStyle(ChatFormatting.RED), false);
            return false;
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
            case HELP -> {
                // /help 由客户端本地展开成日志，正常不会发到服务端；万一发过来了也不消耗终端
                yield false;
            }
        };
        if (success) {
            consumeTerminal(player);
        }
        return success;
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
