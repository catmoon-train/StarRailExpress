package net.exmo.sre.sixtyseconds.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.exmo.sre.sixtyseconds.config.SixtySecondsConfig;
import net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

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
 *   <li>{@code /sre:60s_area show}</li>
 * </ul>
 */
public final class SixtySecondsAreaCommand {
    private SixtySecondsAreaCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("sre:60s_area")
                        .requires(source -> source.hasPermission(2))
                        .then(literal("template")
                                .then(argument("kind", StringArgumentType.word())
                                        .then(argument("x1", IntegerArgumentType.integer())
                                                .then(argument("y1", IntegerArgumentType.integer())
                                                        .then(argument("z1", IntegerArgumentType.integer())
                                                                .then(argument("x2", IntegerArgumentType.integer())
                                                                        .then(argument("y2", IntegerArgumentType.integer())
                                                                                .then(argument("z2", IntegerArgumentType.integer())
                                                                                        .executes(SixtySecondsAreaCommand::setTemplate))))))))
                        )
                        .then(literal("spawn")
                                .then(argument("kind", StringArgumentType.word())
                                        .then(argument("x", IntegerArgumentType.integer())
                                                .then(argument("y", IntegerArgumentType.integer())
                                                        .then(argument("z", IntegerArgumentType.integer())
                                                                .executes(SixtySecondsAreaCommand::setSpawn))))))
                        .then(literal("grid")
                                .then(argument("baseX", IntegerArgumentType.integer())
                                        .then(argument("baseY", IntegerArgumentType.integer())
                                                .then(argument("baseZ", IntegerArgumentType.integer())
                                                        .then(argument("spacing", IntegerArgumentType.integer(1))
                                                                .executes(SixtySecondsAreaCommand::setGrid))))))
                        // 危险等级：无坐标=设全局基线；带坐标=设包含该点的门绑定危险区
                        .then(literal("level")
                                .then(argument("level", IntegerArgumentType.integer(1,
                                        net.exmo.sre.sixtyseconds.SixtySecondsBalance.AREA_LEVEL_MAX))
                                        .executes(SixtySecondsAreaCommand::setGlobalLevel)
                                        .then(argument("x", IntegerArgumentType.integer())
                                                .then(argument("y", IntegerArgumentType.integer())
                                                        .then(argument("z", IntegerArgumentType.integer())
                                                                .executes(SixtySecondsAreaCommand::setBindingLevel))))))
                        // 避难所锚点门：开关 shelter_at_door 打开时，避难所整体平移到「本队出口门 - 锚点门」处
                        .then(literal("anchor")
                                .then(argument("x", IntegerArgumentType.integer())
                                        .then(argument("y", IntegerArgumentType.integer())
                                                .then(argument("z", IntegerArgumentType.integer())
                                                        .executes(SixtySecondsAreaCommand::setAnchorDoor))))
                                .then(literal("clear").executes(SixtySecondsAreaCommand::clearAnchorDoor)))
                        // 清理门绑定/锚点：clearbindings=全清；clearbindings auto=只清海岛自动生成的
                        .then(literal("clearbindings")
                                .executes(ctx -> clearBindings(ctx, false))
                                .then(literal("auto").executes(ctx -> clearBindings(ctx, true))))
                        .then(literal("show").executes(SixtySecondsAreaCommand::show))));
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
        SixtySecondsConfig.Region region = new SixtySecondsConfig.Region(
                new SixtySecondsConfig.Vec(IntegerArgumentType.getInteger(ctx, "x1"),
                        IntegerArgumentType.getInteger(ctx, "y1"), IntegerArgumentType.getInteger(ctx, "z1")),
                new SixtySecondsConfig.Vec(IntegerArgumentType.getInteger(ctx, "x2"),
                        IntegerArgumentType.getInteger(ctx, "y2"), IntegerArgumentType.getInteger(ctx, "z2")));
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
