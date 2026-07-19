package net.exmo.sre.sixtyseconds.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.exmo.sre.sixtyseconds.config.SixtySecondsConfig;
import net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * {@code /sre:60s_area ...} 登记末日60秒模式的地图配置（模板区域 / 出生点 / 队伍网格），落盘到
 * 世界存档目录 {@code sixty_seconds_config.json}。每次 set 后自动保存。
 * <p>
 * 用法：
 * <ul>
 *   <li>{@code /sre:60s_area template <residential|shelter> <x1> <y1> <z1> <x2> <y2> <z2>}（两个对角的绝对坐标，顺序随意）</li>
 *   <li>{@code /sre:60s_area spawn <residential|shelter> <x> <y> <z>}（模板内绝对坐标）</li>
 *   <li>{@code /sre:60s_area grid <baseX> <baseY> <baseZ> <spacing>}</li>
 *   <li>{@code /sre:60s_area anchor <x> <y> <z>} / {@code anchor clear}（避难所锚点门，模板内绝对坐标；
 *       配合 {@code /sre:60s shelter_at_door on} 把避难所克隆到各队探索区出口门上）</li>
 *   <li>{@code /sre:60s_area clearbindings [auto]}（清门绑定/锚点：无参全清，auto=只清海岛自动生成的）</li>
 *   <li>{@code /sre:60s_area region add <x1 y1 z1> <x2 y2 z2> <level> [name]} / {@code region here <radius> <level> [name]}
 *       / {@code region list|remove <index>|clear|at}（星级区域覆盖：任意盒设危险等级，优先级高于岛屿；
 *       登记时自动撒物资箱）；{@code region autosupply on|off|count <n>}（自动撒箱开关/基准数量）</li>
 *   <li>{@code /sre:60s_area show}</li>
 * </ul>
 */
public final class SixtySecondsAreaCommand {
    private SixtySecondsAreaCommand() {
    }

    /** 获取玩家瞄准的方块坐标（射线追踪，最大20格），非玩家或未命中时回退到脚下坐标 */
    private static BlockPos getTargetedBlock(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player
                && player.pick(20.0D, 0.0F, false) instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK) {
            return hit.getBlockPos();
        }
        return BlockPos.containing(source.getPosition());
    }

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_TARGET_X = (ctx, builder) -> {
        BlockPos pos = getTargetedBlock(ctx.getSource());
        return builder.suggest(String.valueOf(pos.getX())).buildFuture();
    };
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_TARGET_Y = (ctx, builder) -> {
        BlockPos pos = getTargetedBlock(ctx.getSource());
        return builder.suggest(String.valueOf(pos.getY())).buildFuture();
    };
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_TARGET_Z = (ctx, builder) -> {
        BlockPos pos = getTargetedBlock(ctx.getSource());
        return builder.suggest(String.valueOf(pos.getZ())).buildFuture();
    };

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("sre:60s_area")
                        .requires(source -> source.hasPermission(2))
                        .then(literal("template")
                                .then(argument("kind", StringArgumentType.word())
                                        .then(argument("x1", IntegerArgumentType.integer()).suggests(SUGGEST_TARGET_X)
                                                .then(argument("y1", IntegerArgumentType.integer()).suggests(SUGGEST_TARGET_Y)
                                                        .then(argument("z1", IntegerArgumentType.integer()).suggests(SUGGEST_TARGET_Z)
                                                                .then(argument("x2", IntegerArgumentType.integer()).suggests(SUGGEST_TARGET_X)
                                                                        .then(argument("y2", IntegerArgumentType.integer()).suggests(SUGGEST_TARGET_Y)
                                                                                .then(argument("z2", IntegerArgumentType.integer()).suggests(SUGGEST_TARGET_Z)
                                                                                        .executes(SixtySecondsAreaCommand::setTemplate))))))))
                        )
                        .then(literal("spawn")
                                .then(argument("kind", StringArgumentType.word())
                                        .then(argument("x", IntegerArgumentType.integer()).suggests(SUGGEST_TARGET_X)
                                                .then(argument("y", IntegerArgumentType.integer()).suggests(SUGGEST_TARGET_Y)
                                                        .then(argument("z", IntegerArgumentType.integer()).suggests(SUGGEST_TARGET_Z)
                                                                .executes(SixtySecondsAreaCommand::setSpawn))))))
                        .then(literal("grid")
                                .then(argument("baseX", IntegerArgumentType.integer()).suggests(SUGGEST_TARGET_X)
                                        .then(argument("baseY", IntegerArgumentType.integer()).suggests(SUGGEST_TARGET_Y)
                                                .then(argument("baseZ", IntegerArgumentType.integer()).suggests(SUGGEST_TARGET_Z)
                                                        .then(argument("spacing", IntegerArgumentType.integer(1))
                                                                .executes(SixtySecondsAreaCommand::setGrid))))))
                        // 危险等级：无坐标=设全局基线；带坐标=设包含该点的门绑定危险区
                        .then(literal("level")
                                .then(argument("level", IntegerArgumentType.integer(1,
                                        net.exmo.sre.sixtyseconds.SixtySecondsBalance.AREA_LEVEL_MAX))
                                        .executes(SixtySecondsAreaCommand::setGlobalLevel)
                                        .then(argument("x", IntegerArgumentType.integer()).suggests(SUGGEST_TARGET_X)
                                                .then(argument("y", IntegerArgumentType.integer()).suggests(SUGGEST_TARGET_Y)
                                                        .then(argument("z", IntegerArgumentType.integer()).suggests(SUGGEST_TARGET_Z)
                                                                .executes(SixtySecondsAreaCommand::setBindingLevel))))))
                        // 避难所锚点门：开关 shelter_at_door 打开时，避难所整体平移到「本队出口门 - 锚点门」处
                        .then(literal("anchor")
                                .then(argument("x", IntegerArgumentType.integer()).suggests(SUGGEST_TARGET_X)
                                        .then(argument("y", IntegerArgumentType.integer()).suggests(SUGGEST_TARGET_Y)
                                                .then(argument("z", IntegerArgumentType.integer()).suggests(SUGGEST_TARGET_Z)
                                                        .executes(SixtySecondsAreaCommand::setAnchorDoor))))
                                .then(literal("clear").executes(SixtySecondsAreaCommand::clearAnchorDoor)))
                        // 清理门绑定/锚点：clearbindings=全清；clearbindings auto=只清海岛自动生成的
                        .then(literal("clearbindings")
                                .executes(ctx -> clearBindings(ctx, false))
                                .then(literal("auto").executes(ctx -> clearBindings(ctx, true))))
                        // 星级区域覆盖：任意盒 → 危险等级 0..5（0=安全区），优先级高于岛屿（魔改某片区域星级用）
                        .then(literal("region")
                                .then(literal("add")
                                        .then(argument("x1", IntegerArgumentType.integer()).suggests(SUGGEST_TARGET_X)
                                                .then(argument("y1", IntegerArgumentType.integer()).suggests(SUGGEST_TARGET_Y)
                                                        .then(argument("z1", IntegerArgumentType.integer()).suggests(SUGGEST_TARGET_Z)
                                                                .then(argument("x2", IntegerArgumentType.integer()).suggests(SUGGEST_TARGET_X)
                                                                        .then(argument("y2", IntegerArgumentType.integer()).suggests(SUGGEST_TARGET_Y)
                                                                                .then(argument("z2", IntegerArgumentType.integer()).suggests(SUGGEST_TARGET_Z)
                                                                                        .then(argument("level", IntegerArgumentType.integer(0,
                                                                                                net.exmo.sre.sixtyseconds.SixtySecondsBalance.AREA_LEVEL_MAX))
                                                                                                .executes(ctx -> addRegion(ctx, false))
                                                                                                .then(argument("name", StringArgumentType.greedyString())
                                                                                                        .executes(ctx -> addRegion(ctx, true)))))))))))
                                .then(literal("here")
                                        .then(argument("radius", IntegerArgumentType.integer(0))
                                                .then(argument("level", IntegerArgumentType.integer(0,
                                                        net.exmo.sre.sixtyseconds.SixtySecondsBalance.AREA_LEVEL_MAX))
                                                        .executes(ctx -> addRegionHere(ctx, false))
                                                        .then(argument("name", StringArgumentType.greedyString())
                                                                .executes(ctx -> addRegionHere(ctx, true))))))
                                .then(literal("autosupply")
                                        .then(literal("on").executes(ctx -> setRegionAutoSupply(ctx, true)))
                                        .then(literal("off").executes(ctx -> setRegionAutoSupply(ctx, false)))
                                        .then(literal("count")
                                                .then(argument("count", IntegerArgumentType.integer(0, 512))
                                                        .executes(SixtySecondsAreaCommand::setRegionSupplyCount))))
                                .then(literal("rename")
                                        .then(argument("index", IntegerArgumentType.integer(0))
                                                .then(argument("name", StringArgumentType.greedyString())
                                                        .executes(SixtySecondsAreaCommand::renameRegion))))
                                .then(literal("list").executes(SixtySecondsAreaCommand::listRegions))
                                .then(literal("remove")
                                        .then(argument("index", IntegerArgumentType.integer(0))
                                                .executes(SixtySecondsAreaCommand::removeRegion)))
                                .then(literal("clear").executes(SixtySecondsAreaCommand::clearRegions))
                                .then(literal("at").executes(SixtySecondsAreaCommand::regionAt)))
                        // 房车模式：开关 + 每队刷新点（按登记顺序对应队伍序号）
                        .then(literal("rv")
                                .then(literal("on").executes(ctx -> setRvEnabled(ctx, true)))
                                .then(literal("off").executes(ctx -> setRvEnabled(ctx, false)))
                                .then(literal("add")
                                        .then(argument("x", IntegerArgumentType.integer()).suggests(SUGGEST_TARGET_X)
                                                .then(argument("y", IntegerArgumentType.integer()).suggests(SUGGEST_TARGET_Y)
                                                        .then(argument("z", IntegerArgumentType.integer()).suggests(SUGGEST_TARGET_Z)
                                                                .executes(SixtySecondsAreaCommand::addRvSpawn)))))
                                .then(literal("list").executes(SixtySecondsAreaCommand::listRvSpawns))
                                .then(literal("remove")
                                        .then(argument("index", IntegerArgumentType.integer(0))
                                                .executes(SixtySecondsAreaCommand::removeRvSpawn)))
                                .then(literal("clear").executes(SixtySecondsAreaCommand::clearRvSpawns))
                                .executes(SixtySecondsAreaCommand::listRvSpawns))
                        .then(literal("show").executes(SixtySecondsAreaCommand::show))));
    }

    // ── 房车模式 ─────────────────────────────────────────────────

    private static int setRvEnabled(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        ServerLevel level = ctx.getSource().getLevel();
        SixtySecondsConfig cfg = load(level);
        cfg.rvEnabled = enabled;
        SixtySecondsConfigStore.save(level, cfg);
        ctx.getSource().sendSuccess(() -> Component.translatable(
                enabled ? "message.noellesroles.sixty_seconds.rv_cmd.on"
                        : "message.noellesroles.sixty_seconds.rv_cmd.off").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int addRvSpawn(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        SixtySecondsConfig cfg = load(level);
        if (cfg.rvSpawnPoints == null) {
            cfg.rvSpawnPoints = new java.util.ArrayList<>();
        }
        int x = IntegerArgumentType.getInteger(ctx, "x");
        int y = IntegerArgumentType.getInteger(ctx, "y");
        int z = IntegerArgumentType.getInteger(ctx, "z");
        cfg.rvSpawnPoints.add(new SixtySecondsConfig.Vec(x, y, z));
        SixtySecondsConfigStore.save(level, cfg);
        int teamNo = cfg.rvSpawnPoints.size();
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "message.noellesroles.sixty_seconds.rv_cmd.added", teamNo, x, y, z)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int removeRvSpawn(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        SixtySecondsConfig cfg = load(level);
        int index = IntegerArgumentType.getInteger(ctx, "index");
        if (cfg.rvSpawnPoints == null || index < 0 || index >= cfg.rvSpawnPoints.size()) {
            ctx.getSource().sendFailure(Component.translatable(
                    "message.noellesroles.sixty_seconds.rv_cmd.index_oob", index));
            return 0;
        }
        cfg.rvSpawnPoints.remove(index);
        SixtySecondsConfigStore.save(level, cfg);
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "message.noellesroles.sixty_seconds.rv_cmd.removed", index).withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int clearRvSpawns(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        SixtySecondsConfig cfg = load(level);
        int cleared = cfg.rvSpawnPoints == null ? 0 : cfg.rvSpawnPoints.size();
        if (cfg.rvSpawnPoints != null) {
            cfg.rvSpawnPoints.clear();
        }
        SixtySecondsConfigStore.save(level, cfg);
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "message.noellesroles.sixty_seconds.rv_cmd.cleared", cleared).withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int listRvSpawns(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        SixtySecondsConfig cfg = load(level);
        int count = cfg.rvSpawnPoints == null ? 0 : cfg.rvSpawnPoints.size();
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "message.noellesroles.sixty_seconds.rv_cmd.list_header",
                cfg.rvEnabled ? "ON" : "OFF", count).withStyle(ChatFormatting.GOLD), false);
        if (cfg.rvSpawnPoints != null) {
            for (int i = 0; i < cfg.rvSpawnPoints.size(); i++) {
                SixtySecondsConfig.Vec v = cfg.rvSpawnPoints.get(i);
                int teamNo = i + 1;
                ctx.getSource().sendSuccess(() -> Component.translatable(
                        "message.noellesroles.sixty_seconds.rv_cmd.list_entry", teamNo, v.x, v.y, v.z)
                        .withStyle(ChatFormatting.AQUA), false);
            }
        }
        return 1;
    }

    // ── 星级区域覆盖 ─────────────────────────────────────────────

    private static int addRegionBox(CommandContext<CommandSourceStack> ctx, ServerLevel level,
            int x1, int y1, int z1, int x2, int y2, int z2, int lv, String name) {
        SixtySecondsConfig cfg = load(level);
        if (cfg.areaLevelOverrides == null) {
            cfg.areaLevelOverrides = new java.util.ArrayList<>();
        }
        int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);
        net.minecraft.core.BlockPos min = new net.minecraft.core.BlockPos(minX, minY, minZ);
        net.minecraft.core.BlockPos max = new net.minecraft.core.BlockPos(maxX, maxY, maxZ);
        cfg.areaLevelOverrides.add(new SixtySecondsConfig.LevelRegion(
                new SixtySecondsConfig.Vec(minX, minY, minZ), new SixtySecondsConfig.Vec(maxX, maxY, maxZ), lv, name));
        SixtySecondsConfigStore.save(level, cfg);
        int idx = cfg.areaLevelOverrides.size() - 1;
        String levelLabel = lv == 0 ? "SAFE ZONE" : "Lv." + lv;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[60s] star region #" + idx + (name == null ? "" : " \"" + name + "\"") + " added: ("
                        + minX + "," + minY + "," + minZ + ") ~ (" + maxX + "," + maxY + "," + maxZ + ") " + levelLabel)
                .withStyle(lv == 0 ? ChatFormatting.GREEN : ChatFormatting.GREEN), true);
        // 自动撒物资箱（开关开时）：低级随机 / 上锁高级 / 高级随机，数量按等级缩放。
        // 安全区（0 级）是和平区，不撒物资箱。
        if (lv > 0 && cfg.regionAutoSupplyEnabled) {
            int placed = net.exmo.sre.sixtyseconds.logic.SixtySecondsRegionSupply.spawn(
                    level, min, max, lv, cfg.regionSupplyBoxBaseCount);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "[60s] auto-scattered " + placed + " supply box(es) (level " + lv + ")")
                    .withStyle(ChatFormatting.AQUA), true);
        } else if (lv == 0) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "[60s] safe zone: no supply boxes, no mob spawns, PvP disabled")
                    .withStyle(ChatFormatting.YELLOW), true);
        }
        return 1;
    }

    /** {@code region add <x1 y1 z1> <x2 y2 z2> <level> [name]}：登记一块星级区域覆盖（可带名字）。 */
    private static int addRegion(CommandContext<CommandSourceStack> ctx, boolean named) {
        return addRegionBox(ctx, ctx.getSource().getLevel(),
                IntegerArgumentType.getInteger(ctx, "x1"), IntegerArgumentType.getInteger(ctx, "y1"),
                IntegerArgumentType.getInteger(ctx, "z1"), IntegerArgumentType.getInteger(ctx, "x2"),
                IntegerArgumentType.getInteger(ctx, "y2"), IntegerArgumentType.getInteger(ctx, "z2"),
                IntegerArgumentType.getInteger(ctx, "level"),
                named ? StringArgumentType.getString(ctx, "name").replace('&', '§') : null);
    }

    /** {@code region here <radius> <level> [name]}：以执行者所在位置为中心登记一块 ±radius 的星级区域覆盖。 */
    private static int addRegionHere(CommandContext<CommandSourceStack> ctx, boolean named) {
        net.minecraft.core.BlockPos c = net.minecraft.core.BlockPos.containing(ctx.getSource().getPosition());
        int r = IntegerArgumentType.getInteger(ctx, "radius");
        int lv = IntegerArgumentType.getInteger(ctx, "level");
        return addRegionBox(ctx, ctx.getSource().getLevel(),
                c.getX() - r, c.getY() - r, c.getZ() - r, c.getX() + r, c.getY() + r, c.getZ() + r, lv,
                named ? StringArgumentType.getString(ctx, "name").replace('&', '§') : null);
    }

    /** {@code region autosupply on|off}：切换区域登记时自动撒物资箱。 */
    private static int setRegionAutoSupply(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        ServerLevel level = ctx.getSource().getLevel();
        SixtySecondsConfig cfg = load(level);
        cfg.regionAutoSupplyEnabled = enabled;
        SixtySecondsConfigStore.save(level, cfg);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[60s] region auto-supply " + (enabled ? "ON" : "OFF")).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /** {@code region autosupply count <n>}：设置区域自动撒箱的基准数量（1 级区域箱数）。 */
    private static int setRegionSupplyCount(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        SixtySecondsConfig cfg = load(level);
        cfg.regionSupplyBoxBaseCount = IntegerArgumentType.getInteger(ctx, "count");
        SixtySecondsConfigStore.save(level, cfg);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[60s] region supply base count = " + cfg.regionSupplyBoxBaseCount
                        + " (level N → " + net.exmo.sre.sixtyseconds.logic.SixtySecondsRegionSupply
                                .boxCountFor(1, cfg.regionSupplyBoxBaseCount) + ".."
                        + net.exmo.sre.sixtyseconds.logic.SixtySecondsRegionSupply.boxCountFor(
                                net.exmo.sre.sixtyseconds.SixtySecondsBalance.AREA_LEVEL_MAX,
                                cfg.regionSupplyBoxBaseCount) + ")")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int listRegions(CommandContext<CommandSourceStack> ctx) {
        SixtySecondsConfig cfg = load(ctx.getSource().getLevel());
        java.util.List<SixtySecondsConfig.LevelRegion> regs = cfg.areaLevelOverrides;
        if (regs == null || regs.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("[60s] no star regions")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("[60s] " + regs.size() + " star region(s):")
                .withStyle(ChatFormatting.GOLD), false);
        for (int i = 0; i < regs.size(); i++) {
            SixtySecondsConfig.LevelRegion reg = regs.get(i);
            String box = reg.min == null || reg.max == null ? "?"
                    : "(" + reg.min.x + "," + reg.min.y + "," + reg.min.z + ") ~ ("
                            + reg.max.x + "," + reg.max.y + "," + reg.max.z + ")";
            final int idx = i;
            String label = reg.name == null || reg.name.isEmpty() ? "" : "  \"" + reg.name + "\"";
            String lvLabel = reg.level == 0 ? "SAFE ZONE" : "Lv." + reg.level;
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "  #" + idx + "  " + lvLabel + label + "  " + box).withStyle(ChatFormatting.GRAY), false);
        }
        return regs.size();
    }

    private static int removeRegion(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        SixtySecondsConfig cfg = load(level);
        int idx = IntegerArgumentType.getInteger(ctx, "index");
        if (cfg.areaLevelOverrides == null || idx < 0 || idx >= cfg.areaLevelOverrides.size()) {
            ctx.getSource().sendFailure(Component.literal("[60s] no star region #" + idx)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        cfg.areaLevelOverrides.remove(idx);
        SixtySecondsConfigStore.save(level, cfg);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[60s] removed star region #" + idx + ", " + cfg.areaLevelOverrides.size() + " left")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /** {@code region rename <index> <name>}：重命名指定索引的星级区域（空字符串可清空名字）。 */
    private static int renameRegion(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        SixtySecondsConfig cfg = load(level);
        int idx = IntegerArgumentType.getInteger(ctx, "index");
        if (cfg.areaLevelOverrides == null || idx < 0 || idx >= cfg.areaLevelOverrides.size()) {
            ctx.getSource().sendFailure(Component.literal("[60s] no star region #" + idx)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        String rawName = StringArgumentType.getString(ctx, "name");
        String newName = rawName.replace('&', '§');
        SixtySecondsConfig.LevelRegion reg = cfg.areaLevelOverrides.get(idx);
        String oldLabel = reg.name == null || reg.name.isEmpty() ? "(unnamed)" : "\"" + reg.name + "\"";
        reg.name = newName.isEmpty() ? null : newName;
        SixtySecondsConfigStore.save(level, cfg);
        String newLabel = reg.name == null ? "(unnamed)" : "\"" + reg.name + "\"";
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[60s] renamed star region #" + idx + ": " + oldLabel + " → " + newLabel)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int clearRegions(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        SixtySecondsConfig cfg = load(level);
        int had = cfg.areaLevelOverrides == null ? 0 : cfg.areaLevelOverrides.size();
        cfg.areaLevelOverrides = new java.util.ArrayList<>();
        SixtySecondsConfigStore.save(level, cfg);
        ctx.getSource().sendSuccess(() -> Component.literal("[60s] cleared " + had + " star region(s)")
                .withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    /** {@code region at}：显示执行者所在坐标的最终危险等级（含覆盖/岛屿/门绑定/全局的综合结果）。 */
    private static int regionAt(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(ctx.getSource().getPosition());
        int lv = net.exmo.sre.sixtyseconds.logic.SixtySecondsAreaLevels.levelAt(level, pos);
        String label = lv == 0 ? "SAFE ZONE" : "Lv." + lv;
        ctx.getSource().sendSuccess(() -> Component.literal("[60s] danger level @ ("
                + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ") = " + label)
                .withStyle(lv == 0 ? ChatFormatting.GREEN : ChatFormatting.AQUA), false);
        return lv;
    }

    /**
     * {@code clearbindings [auto]}：清理门绑定/锚点。无参=清掉<b>全部</b>门绑定（管理员手动 + 海岛自动）；
     * {@code auto}=只清海岛一级岛自动生成的（{@code auto=true}）那些。
     */
    private static int clearBindings(CommandContext<CommandSourceStack> ctx, boolean onlyAuto) {
        ServerLevel level = ctx.getSource().getLevel();
        SixtySecondsConfig cfg = load(level);
        if (cfg.searchDoorBindings == null || cfg.searchDoorBindings.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("[60s] no door bindings to clear")
                    .withStyle(ChatFormatting.YELLOW), true);
            return 0;
        }
        int before = cfg.searchDoorBindings.size();
        if (onlyAuto) {
            cfg.searchDoorBindings.removeIf(bd -> bd.auto);
        } else {
            cfg.searchDoorBindings.clear();
        }
        int removed = before - cfg.searchDoorBindings.size();
        SixtySecondsConfigStore.save(level, cfg);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[60s] cleared " + removed + " door binding(s)" + (onlyAuto ? " (auto only)" : "")
                        + ", " + cfg.searchDoorBindings.size() + " left")
                .withStyle(ChatFormatting.GREEN), true);
        return removed;
    }

    /** {@code anchor <x y z>}：登记避难所模板内的锚点门（模板绝对坐标）；须落在 shelter 模板盒内。 */
    private static int setAnchorDoor(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        SixtySecondsConfig cfg = load(level);
        SixtySecondsConfig.Vec vec = new SixtySecondsConfig.Vec(IntegerArgumentType.getInteger(ctx, "x"),
                IntegerArgumentType.getInteger(ctx, "y"), IntegerArgumentType.getInteger(ctx, "z"));
        if (cfg.shelterTemplate == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "[60s] set the shelter template first: /sre:60s_area template shelter <x1 y1 z1> <x2 y2 z2>")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        // 锚点门必须在避难所模板盒内——否则平移量会把整座避难所甩到离谱的地方
        if (!cfg.shelterTemplate.toBox().isInside(vec.toBlockPos())) {
            ctx.getSource().sendFailure(Component.literal("[60s] anchor (" + vec.x + "," + vec.y + "," + vec.z
                    + ") is outside the shelter template box; pick the door block inside the template")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        cfg.shelterAnchorDoor = vec;
        SixtySecondsConfigStore.save(level, cfg);
        // 软校验：锚点该是模板里那扇避难所门。指错了照样能存（模板可能还没搭完），但要提醒——
        // 克隆会把锚点处的方块盖到出口门上，锚点不是门的话，探索区那扇门就被顶掉了，
        // 而 returnDoorPos 仍指着它 → 玩家回不了家
        if (!level.getBlockState(vec.toBlockPos()).is(org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_SHELTER_DOOR)) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "[60s] warning: no sixty_seconds_shelter_door at the anchor — the exit door will be overwritten "
                            + "by whatever block sits there. Point the anchor at the shelter's door block.")
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("[60s] shelter anchor door = ("
                + vec.x + "," + vec.y + "," + vec.z + "); shelter_at_door "
                + (cfg.shelterAtSearchDoorEnabled ? "is ON — shelters will clone onto each team's exit door"
                        : "is OFF — enable with /sre:60s shelter_at_door on"))
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /** {@code anchor clear}：清除锚点门（避难所回退网格克隆）。 */
    private static int clearAnchorDoor(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        SixtySecondsConfig cfg = load(level);
        cfg.shelterAnchorDoor = null;
        SixtySecondsConfigStore.save(level, cfg);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[60s] shelter anchor door cleared; shelters fall back to the team grid")
                .withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static SixtySecondsConfig load(ServerLevel level) {
        return SixtySecondsConfigStore.load(level).orElseGet(SixtySecondsConfig::new);
    }

    /** {@code level <n>}：设全局探索区危险等级（1..5，影响物资箱稀有度与游荡怪强度）。 */
    private static int setGlobalLevel(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        int value = IntegerArgumentType.getInteger(ctx, "level");
        SixtySecondsConfig cfg = load(level);
        cfg.searchZoneLevel = value;
        SixtySecondsConfigStore.save(level, cfg);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[60s] global searchzone danger level = " + value).withStyle(ChatFormatting.GREEN), true);
        return value;
    }

    /** {@code level <n> <x y z>}：设包含该点的门绑定探索区的危险等级（0=继承全局）。 */
    private static int setBindingLevel(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        int value = IntegerArgumentType.getInteger(ctx, "level");
        int x = IntegerArgumentType.getInteger(ctx, "x");
        int y = IntegerArgumentType.getInteger(ctx, "y");
        int z = IntegerArgumentType.getInteger(ctx, "z");
        SixtySecondsConfig cfg = load(level);
        int matched = 0;
        for (SixtySecondsConfig.DoorBinding binding : cfg.searchDoorBindings) {
            if (binding.boxMin == null || binding.boxMax == null) {
                continue;
            }
            if (x >= Math.min(binding.boxMin.x, binding.boxMax.x) && x <= Math.max(binding.boxMin.x, binding.boxMax.x)
                    && y >= Math.min(binding.boxMin.y, binding.boxMax.y)
                    && y <= Math.max(binding.boxMin.y, binding.boxMax.y)
                    && z >= Math.min(binding.boxMin.z, binding.boxMax.z)
                    && z <= Math.max(binding.boxMin.z, binding.boxMax.z)) {
                binding.level = value;
                matched++;
            }
        }
        if (matched == 0) {
            ctx.getSource().sendFailure(Component.literal(
                    "[60s] no door-bound searchzone contains (" + x + "," + y + "," + z
                            + "); use 'level <n>' for the global zone").withStyle(ChatFormatting.RED));
            return 0;
        }
        SixtySecondsConfigStore.save(level, cfg);
        int count = matched;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[60s] danger level " + value + " set on " + count + " bound zone(s)")
                .withStyle(ChatFormatting.GREEN), true);
        return matched;
    }

    private static int setTemplate(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        String kind = StringArgumentType.getString(ctx, "kind").toLowerCase();
        int x1 = IntegerArgumentType.getInteger(ctx, "x1");
        int y1 = IntegerArgumentType.getInteger(ctx, "y1");
        int z1 = IntegerArgumentType.getInteger(ctx, "z1");
        int x2 = IntegerArgumentType.getInteger(ctx, "x2");
        int y2 = IntegerArgumentType.getInteger(ctx, "y2");
        int z2 = IntegerArgumentType.getInteger(ctx, "z2");
        SixtySecondsConfig.Region region = new SixtySecondsConfig.Region(
                new SixtySecondsConfig.Vec(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2)),
                new SixtySecondsConfig.Vec(Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)));
        SixtySecondsConfig cfg = load(level);
        switch (kind) {
            case "residential" -> cfg.residentialTemplate = region;
            case "shelter" -> cfg.shelterTemplate = region;
            default -> {
                ctx.getSource().sendFailure(unknownKind(kind));
                return 0;
            }
        }
        SixtySecondsConfigStore.save(level, cfg);
        var box = region.toBox();
        ctx.getSource().sendSuccess(() -> Component.literal("[60s] template " + kind + " set: ("
                + box.minX() + "," + box.minY() + "," + box.minZ() + ") ~ ("
                + box.maxX() + "," + box.maxY() + "," + box.maxZ() + ")")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setSpawn(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        String kind = StringArgumentType.getString(ctx, "kind").toLowerCase();
        SixtySecondsConfig.Vec vec = new SixtySecondsConfig.Vec(IntegerArgumentType.getInteger(ctx, "x"),
                IntegerArgumentType.getInteger(ctx, "y"), IntegerArgumentType.getInteger(ctx, "z"));
        SixtySecondsConfig cfg = load(level);
        switch (kind) {
            case "residential" -> cfg.residentialSpawn = vec;
            case "shelter" -> cfg.shelterSpawn = vec;
            default -> {
                ctx.getSource().sendFailure(unknownKind(kind));
                return 0;
            }
        }
        SixtySecondsConfigStore.save(level, cfg);
        ctx.getSource().sendSuccess(() -> Component.literal("[60s] spawn " + kind + " set")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setGrid(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        SixtySecondsConfig cfg = load(level);
        cfg.teamBase = new SixtySecondsConfig.Vec(IntegerArgumentType.getInteger(ctx, "baseX"),
                IntegerArgumentType.getInteger(ctx, "baseY"), IntegerArgumentType.getInteger(ctx, "baseZ"));
        cfg.teamGridSpacing = IntegerArgumentType.getInteger(ctx, "spacing");
        SixtySecondsConfigStore.save(level, cfg);
        ctx.getSource().sendSuccess(() -> Component.literal("[60s] grid set").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int show(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        SixtySecondsConfig cfg = load(level);
        String anchor = cfg.shelterAnchorDoor == null ? "unset"
                : cfg.shelterAnchorDoor.x + "," + cfg.shelterAnchorDoor.y + "," + cfg.shelterAnchorDoor.z;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[60s] complete=" + cfg.isComplete() + " grid=" + cfg.teamBase.x + "," + cfg.teamBase.y + ","
                        + cfg.teamBase.z + " spacing=" + cfg.teamGridSpacing
                        + " shelter_at_door=" + cfg.shelterAtSearchDoorEnabled + " anchor=" + anchor
                        + " sea_teleport=" + cfg.seaChartTeleportEnabled
                        + " file=" + SixtySecondsConfigStore.describe(level)), false);
        return 1;
    }

    private static Component unknownKind(String kind) {
        return Component.literal("[60s] unknown kind '" + kind + "' (residential|shelter)")
                .withStyle(ChatFormatting.RED);
    }
}
