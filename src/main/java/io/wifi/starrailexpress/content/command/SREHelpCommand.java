package io.wifi.starrailexpress.content.command;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.CommandNode;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * /sre:help — 读取 assets/sre_commands/lang 下的语言键, 在游戏内列出本 Mod 注册的全部指令。
 * 文案集中在独立命名空间 sre_commands, 键格式为 sre_commands.<path>.name / .desc。
 */
public class SREHelpCommand {

    /** 单条指令: 玩家可输入的字面量 + 语言键路径 (拼出 sre_commands.<keyPath>.name/.desc)。 */
    private record Entry(String command, String keyPath) {
    }

    /** 分类 id -> 该分类下的指令列表。分类标题键为 sre_commands.category.<id>。 */
    private static final Map<String, List<Entry>> CATEGORIES = new LinkedHashMap<>();

    static {
        CATEGORIES.put("core", List.of(
                new Entry("/sre:help", "sre.help"),
                new Entry("/tmm:start", "tmm.start"),
                new Entry("/tmm:stop", "tmm.stop"),
                new Entry("/tmm:config", "tmm.config"),
                new Entry("/tmm:afk", "tmm.afk"),
                new Entry("/tmm:money", "tmm.money"),
                new Entry("/tmm:mood", "tmm.mood"),
                new Entry("/tmm:autoStart", "tmm.autostart"),
                new Entry("/tmm:votemap", "tmm.votemap"),
                new Entry("/tmm:switchmap", "tmm.switchmap"),
                new Entry("/tmm:fourthroom", "tmm.fourthroom"),
                new Entry("/tmm:createpoint", "tmm.createpoint"),
                new Entry("/tmm:togglewaypoints", "tmm.togglewaypoints"),
                new Entry("/tmm:showStats", "tmm.showstats"),
                new Entry("/tmm:showSelectedMapUI", "tmm.showselectedmapui"),
                new Entry("/tmm:netstats", "tmm.netstats"),
                new Entry("/tmm:giveRoomKey", "tmm.giveroomkey"),
                new Entry("/tmm:participate", "tmm.participate"),
                new Entry("/tmm:entity_interact_cmd", "tmm.entity_interact_cmd"),
                new Entry("/tmm:reload", "tmm.reload"),
                new Entry("/tmm:skinsync", "tmm.skinsync"),
                new Entry("/tmm:skins", "tmm.skins"),
                new Entry("/forceTeam", "forceteam"),
                new Entry("/listGameRoles", "listgameroles"),
                new Entry("/stop_when_over", "stop_when_over"),
                new Entry("/sre:narrator", "sre.narrator"),
                new Entry("/sre:custom_replay", "sre.custom_replay"),
                new Entry("/sre:show_replay", "sre.show_replay"),
                new Entry("/sre:replay_screen", "sre.replay_screen"),
                new Entry("/sre:kick", "sre.kick"),
                new Entry("/sre:shield", "sre.shield"),
                new Entry("/sre:stamina", "sre.stamina"),
                new Entry("/sre:inventory", "sre.inventory"),
                new Entry("/sre:invsee", "sre.invsee"),
                new Entry("/sre:monitor", "sre.monitor"),
                new Entry("/sre:vote", "sre.vote"),
                new Entry("/sre:reloadRoleConfig", "sre.reloadroleconfig"),
                new Entry("/sre:area_manager", "sre.area_manager"),
                new Entry("/sre:pass", "sre.pass"),
                new Entry("/sre:roster", "sre.roster"),
                new Entry("/sre:scene", "sre.scene"),
                new Entry("/sre:camera", "sre.camera"),
                new Entry("/sre:subtitle", "sre.subtitle"),
                new Entry("/sre:eggclear", "sre.eggclear"),
                new Entry("/sre:infected", "sre.infected"),
                new Entry("/sre:helium", "sre.helium")));

        CATEGORIES.put("game", List.of(
                new Entry("/tmm:game visual", "tmm.game.visual"),
                new Entry("/tmm:game time", "tmm.game.time"),
                new Entry("/tmm:game penalty", "tmm.game.penalty"),
                new Entry("/tmm:game bounds", "tmm.game.bounds"),
                new Entry("/tmm:game role", "tmm.game.role"),
                new Entry("/tmm:game murder_time", "tmm.game.murder_time"),
                new Entry("/tmm:game tests", "tmm.game.tests"),
                new Entry("/tmm:game tasks", "tmm.game.tasks"),
                new Entry("/tmm:game win", "tmm.game.win"),
                new Entry("/tmm:game reset", "tmm.game.reset"),
                new Entry("/tmm:game scan", "tmm.game.scan"),
                new Entry("/tmm:game blackout", "tmm.game.blackout"),
                new Entry("/tmm:game monitor_broken", "tmm.game.monitor_broken"),
                new Entry("/tmm:game psycho", "tmm.game.psycho"),
                new Entry("/tmm:game body", "tmm.game.body"),
                new Entry("/tmm:game revive", "tmm.game.revive"),
                new Entry("/tmm:game kill", "tmm.game.kill"),
                new Entry("/tmm:game timestop", "tmm.game.timestop")));

        CATEGORIES.put("harpy", List.of(
                new Entry("/changeRole", "changerole"),
                new Entry("/changeModifier", "changemodifier"),
                new Entry("/forceRole", "forcerole"),
                new Entry("/forceModifier", "forcemodifier"),
                new Entry("/setRoleCount", "setrolecount"),
                new Entry("/setRoleWeight", "setroleweight"),
                new Entry("/myRoleWeight", "myroleweight"),
                new Entry("/playerRoleWeight", "playerroleweight"),
                new Entry("/toggleCustomRoleWeights", "togglecustomroleweights"),
                new Entry("/setOccupationRole", "setoccupationrole"),
                new Entry("/setEnabledRole", "setenabledrole"),
                new Entry("/setEnabledModifier", "setenabledmodifier"),
                new Entry("/setCompanionRole", "setcompanionrole"),
                new Entry("/listRoles", "listroles"),
                new Entry("/manageRolesUI", "managerolesui")));

        CATEGORIES.put("noelle", List.of(
                new Entry("/broadcast", "broadcast"),
                new Entry("/noellesroles preset", "noellesroles.preset"),
                new Entry("/noellesroles setmax", "noellesroles.setmax"),
                new Entry("/room", "room"),
                new Entry("/stuck", "stuck"),
                new Entry("/vt_mode", "vt_mode"),
                new Entry("/nr_free_cam", "nr_free_cam"),
                new Entry("/DisplayItem", "displayitem"),
                new Entry("/cooldown", "cooldown"),
                new Entry("/item", "item"),
                new Entry("/goods:add", "goods.add"),
                new Entry("/goods:remove", "goods.remove"),
                new Entry("/goods:list", "goods.list"),
                new Entry("/repairshop", "repairshop"),
                new Entry("/repair start", "repair"),
                new Entry("/repairrole", "repairrole"),
                new Entry("/repairmap", "repairmap"),
                new Entry("/repairpreset", "repairpreset")));

        CATEGORIES.put("misc", List.of(
                new Entry("/nametag:add", "nametag.add"),
                new Entry("/nametag:remove", "nametag.remove"),
                new Entry("/nametag:set", "nametag.set"),
                new Entry("/nametag:get", "nametag.get"),
                new Entry("/nametag:list", "nametag.list"),
                new Entry("/nametag:clear", "nametag.clear"),
                new Entry("/nametag:sync", "nametag.sync"),
                new Entry("/mw:reload", "mw.reload"),
                new Entry("/mw:maxplayers", "mw.maxplayers")));

        CATEGORIES.put("client", List.of(
                new Entry("/sre:client", "sre.client"),
                new Entry("/sreclient:scene", "sreclient.scene")));

        CATEGORIES.put("mixin", List.of(
                new Entry("/kill", "kill")));
    }

    /** 用于判断某条注册指令是否属于本 Mod (按类的包名前缀)。 */
    private static final List<String> OUR_PACKAGES = List.of(
            "io.wifi", "net.exmo", "org.agmas", "pro.fazeclan", "cn.zbx1425");

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sre:help")
                .executes(ctx -> showOverview(ctx.getSource()))
                // 字面量子命令优先于 category 参数匹配, 故 export_missing 不会被当成分类。
                .then(Commands.literal("export_missing")
                        .requires(source -> source.hasPermission(2))
                .executes(ctx -> exportMissing(ctx.getSource(), dispatcher)))
                .then(Commands.argument("category", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(CATEGORIES.keySet(), builder))
                        .executes(ctx -> showCategory(ctx.getSource(),
                                StringArgumentType.getString(ctx, "category")))));
    }

    private static int showOverview(CommandSourceStack source) {
        MutableComponent out = Component.literal("")
                .append(Component.translatable("sre_commands.help.header")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append("\n")
                .append(Component.translatable("sre_commands.help.overview_hint").withStyle(ChatFormatting.GRAY));

        for (Map.Entry<String, List<Entry>> cat : CATEGORIES.entrySet()) {
            String id = cat.getKey();
            MutableComponent title = Component.translatable("sre_commands.category." + id)
                    .withStyle(ChatFormatting.AQUA);
            MutableComponent count = Component.literal(" (")
                    .append(Component.translatable("sre_commands.help.count", cat.getValue().size()))
                    .append(")")
                    .withStyle(ChatFormatting.DARK_GRAY);
            MutableComponent line = Component.literal("\n  > ").withStyle(ChatFormatting.YELLOW)
                    .append(title).append(count);
            line.withStyle(style -> style
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sre:help " + id))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.translatable("sre_commands.category." + id))));
            out.append(line);
        }

        // 仅管理员可见: 导出缺失指令数据的可点击入口。
        if (source.hasPermission(2)) {
            out.append(Component.literal("\n"));
            out.append(Component.translatable("sre_commands.help.export_link").withStyle(style -> style
                    .applyFormat(ChatFormatting.LIGHT_PURPLE)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sre:help export_missing"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.translatable("sre_commands.help.export_link_hover")))));
        }

        source.sendSuccess(() -> out, false);
        return 1;
    }

    private static int showCategory(CommandSourceStack source, String category) {
        List<Entry> entries = CATEGORIES.get(category);
        if (entries == null) {
            source.sendFailure(Component.translatable("sre_commands.help.unknown_category", category));
            return 0;
        }

        MutableComponent back = Component.translatable("sre_commands.help.back").withStyle(style -> style
                .applyFormat(ChatFormatting.DARK_GRAY)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sre:help")));

        MutableComponent out = Component.literal("")
                .append(Component.translatable("sre_commands.category." + category)
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(" ").append(back)
                .append("\n")
                .append(Component.translatable("sre_commands.help.category_hint").withStyle(ChatFormatting.GRAY));

        for (Entry e : entries) {
            String base = "sre_commands." + e.keyPath();
            MutableComponent cmd = Component.literal(e.command()).withStyle(style -> style
                    .applyFormat(ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, e.command() + " "))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.translatable(base + ".desc"))));
            MutableComponent name = Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.translatable(base + ".name").withStyle(ChatFormatting.WHITE));
            out.append(Component.literal("\n  ").append(cmd).append(name));
        }

        source.sendSuccess(() -> out, false);
        return 1;
    }

    private static int exportMissing(CommandSourceStack source, CommandDispatcher<CommandSourceStack> dispatcher) {
        Set<String> registered = new TreeSet<>();
        collectCommandPaths(dispatcher.getRoot(), "", registered);

        Set<String> registeredNorm = new TreeSet<>();
        for (String cmd : registered) {
            registeredNorm.add(cmd.startsWith("/") ? cmd.substring(1) : cmd);
        }

        Set<String> documentedNorm = new TreeSet<>();
        for (List<Entry> entries : CATEGORIES.values()) {
            for (Entry e : entries) {
                String cmd = e.command();
                documentedNorm.add(cmd.startsWith("/") ? cmd.substring(1) : cmd);
            }
        }

        Set<String> missing = new TreeSet<>();
        for (String cmd : registeredNorm) {
            if (!documentedNorm.contains(cmd)) {
                missing.add("/" + cmd);
            }
        }

        Set<String> orphaned = new TreeSet<>();
        for (String cmd : documentedNorm) {
            if (!registeredNorm.contains(cmd)) {
                orphaned.add("/" + cmd);
            }
        }

        MutableComponent out = Component.literal("");

        if (missing.isEmpty() && orphaned.isEmpty()) {
            out.append(Component.literal("✓ All commands are documented!").withStyle(ChatFormatting.GREEN));
            source.sendSuccess(() -> out, false);
            return 1;
        }

        if (!missing.isEmpty()) {
            out.append(Component.literal("=== Registered commands not in help (" + missing.size() + ") ===\n").withStyle(ChatFormatting.YELLOW));
            int count = 0;
            for (String cmd : missing) {
                if (count++ < 30) {
                    out.append(Component.literal("  " + cmd + "\n"));
                }
            }
            if (missing.size() > 30) {
                out.append(Component.literal("  ... and " + (missing.size() - 30) + " more (see file)\n").withStyle(ChatFormatting.GRAY));
            }
        }

        if (!orphaned.isEmpty()) {
            out.append(Component.literal("=== Documented commands not registered (" + orphaned.size() + ") ===\n").withStyle(ChatFormatting.RED));
            int count = 0;
            for (String cmd : orphaned) {
                if (count++ < 30) {
                    out.append(Component.literal("  " + cmd + "\n"));
                }
            }
            if (orphaned.size() > 30) {
                out.append(Component.literal("  ... and " + (orphaned.size() - 30) + " more (see file)\n").withStyle(ChatFormatting.GRAY));
            }
        }

        source.sendSuccess(() -> out, false);

        try {
            Path dir = FabricLoader.getInstance().getGameDir();
            Path file = dir.resolve("sre_missing_commands.txt");

            List<String> lines = new ArrayList<>();
            lines.add("=== Registered commands not in help ===");
            lines.addAll(missing);
            lines.add("");
            lines.add("=== Documented commands not registered ===");
            lines.addAll(orphaned);

            Files.write(file, lines, StandardCharsets.UTF_8);
            source.sendSuccess(() -> Component.literal("Exported to " + file).withStyle(ChatFormatting.GRAY), false);
        } catch (Exception e) {
            source.sendFailure(Component.literal("Failed to write file: " + e.getMessage()));
        }

        return 1;
    }

    private static void collectCommandPaths(CommandNode<?> node, String path, Set<String> result) {
        for (CommandNode<?> child : node.getChildren()) {
            String childPath = path.isEmpty() ? child.getName() : path + " " + child.getName();
            if (child.getCommand() != null) {
                result.add("/" + childPath);
            }
            collectCommandPaths(child, childPath, result);
        }
    }
}
