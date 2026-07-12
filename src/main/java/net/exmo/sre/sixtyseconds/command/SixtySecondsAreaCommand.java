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
 *   <li>{@code /sre:60s_area template <residential|shelter|searchzone> <minX> <minY> <minZ> <sizeX> <sizeY> <sizeZ>}</li>
 *   <li>{@code /sre:60s_area spawn <residential|shelter|searchzone> <x> <y> <z>}</li>
 *   <li>{@code /sre:60s_area grid <baseX> <baseY> <baseZ> <spacing>}</li>
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
                                        .then(argument("minX", IntegerArgumentType.integer())
                                                .then(argument("minY", IntegerArgumentType.integer())
                                                        .then(argument("minZ", IntegerArgumentType.integer())
                                                                .then(argument("sizeX", IntegerArgumentType.integer(1))
                                                                        .then(argument("sizeY", IntegerArgumentType.integer(1))
                                                                                .then(argument("sizeZ", IntegerArgumentType.integer(1))
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
                        .then(literal("show").executes(SixtySecondsAreaCommand::show))));
    }

    private static SixtySecondsConfig load(ServerLevel level) {
        return SixtySecondsConfigStore.load(level).orElseGet(SixtySecondsConfig::new);
    }

    private static int setTemplate(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        String kind = StringArgumentType.getString(ctx, "kind").toLowerCase();
        SixtySecondsConfig.Region region = new SixtySecondsConfig.Region(
                new SixtySecondsConfig.Vec(IntegerArgumentType.getInteger(ctx, "minX"),
                        IntegerArgumentType.getInteger(ctx, "minY"), IntegerArgumentType.getInteger(ctx, "minZ")),
                new SixtySecondsConfig.Vec(IntegerArgumentType.getInteger(ctx, "sizeX"),
                        IntegerArgumentType.getInteger(ctx, "sizeY"), IntegerArgumentType.getInteger(ctx, "sizeZ")));
        SixtySecondsConfig cfg = load(level);
        switch (kind) {
            case "residential" -> cfg.residentialTemplate = region;
            case "shelter" -> cfg.shelterTemplate = region;
            case "searchzone" -> cfg.searchZoneTemplate = region;
            default -> {
                ctx.getSource().sendFailure(unknownKind(kind));
                return 0;
            }
        }
        SixtySecondsConfigStore.save(level, cfg);
        ctx.getSource().sendSuccess(() -> Component.literal("[60s] template " + kind + " set")
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
            case "searchzone" -> cfg.searchZoneSpawn = vec;
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
        SixtySecondsConfig cfg = load(ctx.getSource().getLevel());
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[60s] complete=" + cfg.isComplete() + " grid=" + cfg.teamBase.x + "," + cfg.teamBase.y + ","
                        + cfg.teamBase.z + " spacing=" + cfg.teamGridSpacing), false);
        return 1;
    }

    private static Component unknownKind(String kind) {
        return Component.literal("[60s] unknown kind '" + kind + "' (residential|shelter|searchzone)")
                .withStyle(ChatFormatting.RED);
    }
}
