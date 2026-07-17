package net.exmo.sre.sixtyseconds.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore;
import net.exmo.sre.sixtyseconds.entity.OceanSeaMonsterEntity;
import net.exmo.sre.sixtyseconds.entity.OceanSharkEntity;
import net.exmo.sre.sixtyseconds.logic.OceanCreatureSpawner;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;

/**
 * 海洋生物管理命令：生成/开关海洋生物（鲨鱼/海怪）。
 *
 * <pre>{@code
 * /sre:ocean spawn shark <type>            — 在自己位置生成指定鲨鱼
 * /sre:ocean spawn monster <type>          — 生成指定海怪
 * /sre:ocean toggle on|off                 — 开关海洋生物自然刷新（默认开）
 * /sre:ocean status                        — 查看当前开关状态
 * }</pre>
 */
public final class OceanCreatureCommand {

    private static final String[] SHARK_TYPES = {
            "reef_shark", "tiger_shark", "hammerhead", "great_white", "megalodon"
    };
    private static final String[] MONSTER_TYPES = {
            "kraken", "serpent", "leviathan"
    };

    private OceanCreatureCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var root = Commands.literal("sre:ocean")
                .requires(src -> src.hasPermission(2));

        // /sre:ocean spawn shark <type>
        root.then(Commands.literal("spawn")
                .then(Commands.literal("shark")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (String t : SHARK_TYPES) {
                                        builder.suggest(t);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> spawnShark(ctx, ctx.getSource()))
                        ))
                .then(Commands.literal("monster")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (String t : MONSTER_TYPES) {
                                        builder.suggest(t);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> spawnMonster(ctx, ctx.getSource()))
                        ))
        );

        // /sre:ocean toggle on|off
        root.then(Commands.literal("toggle")
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(OceanCreatureCommand::toggleOceanCreatures)));

        // /sre:ocean status
        root.then(Commands.literal("status")
                .executes(OceanCreatureCommand::showStatus));

        dispatcher.register(root);
        });
    }

    private static int spawnShark(CommandContext<CommandSourceStack> ctx, CommandSourceStack src) {
        String type = StringArgumentType.getString(ctx, "type");
        ServerLevel level = src.getLevel();
        BlockPos pos = src.getEntity() instanceof ServerPlayer player
                ? player.blockPosition() : BlockPos.containing(src.getPosition());

        OceanSharkEntity.Variant variant = switch (type.toLowerCase()) {
            case "reef_shark", "reef" -> OceanSharkEntity.Variant.REEF_SHARK;
            case "tiger_shark", "tiger" -> OceanSharkEntity.Variant.TIGER_SHARK;
            case "hammerhead", "hammer" -> OceanSharkEntity.Variant.HAMMERHEAD;
            case "great_white", "greatwhite", "white" -> OceanSharkEntity.Variant.GREAT_WHITE;
            case "megalodon", "mega" -> OceanSharkEntity.Variant.MEGALODON;
            default -> {
                src.sendFailure(Component.literal("未知鲨鱼类型: " + type
                        + "，可用: reef_shark, tiger_shark, hammerhead, great_white, megalodon"));
                yield null;
            }
        };
        if (variant == null) return 0;

        OceanSharkEntity shark = OceanCreatureSpawner.spawnShark(level, pos,
                level.getRandom(), 1.0);
        if (shark != null) {
            shark.applyVariant(variant);
            src.sendSuccess(() -> Component.translatable("command.noellesroles.ocean.shark_spawned",
                            Component.translatable(variant.nameKey()))
                    .withStyle(ChatFormatting.AQUA), true);
            return 1;
        }
        src.sendFailure(Component.literal("生成失败：无法创建鲨鱼实体"));
        return 0;
    }

    private static int spawnMonster(CommandContext<CommandSourceStack> ctx, CommandSourceStack src) {
        String type = StringArgumentType.getString(ctx, "type");
        ServerLevel level = src.getLevel();
        BlockPos pos = src.getEntity() instanceof ServerPlayer player
                ? player.blockPosition() : BlockPos.containing(src.getPosition());

        OceanSeaMonsterEntity.Variant variant = switch (type.toLowerCase()) {
            case "kraken" -> OceanSeaMonsterEntity.Variant.KRAKEN;
            case "serpent" -> OceanSeaMonsterEntity.Variant.SERPENT;
            case "leviathan" -> OceanSeaMonsterEntity.Variant.LEVIATHAN;
            default -> {
                src.sendFailure(Component.literal("未知海怪类型: " + type
                        + "，可用: kraken, serpent, leviathan"));
                yield null;
            }
        };
        if (variant == null) return 0;

        OceanSeaMonsterEntity monster = OceanCreatureSpawner.spawnSeaMonster(level, pos,
                level.getRandom(), 1.0);
        if (monster != null) {
            monster.applyVariant(variant);
            src.sendSuccess(() -> Component.translatable("command.noellesroles.ocean.monster_spawned",
                            Component.translatable(variant.nameKey()))
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
            return 1;
        }
        src.sendFailure(Component.literal("生成失败：无法创建海怪实体"));
        return 0;
    }

    private static int toggleOceanCreatures(CommandContext<CommandSourceStack> ctx) {
        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
        ServerLevel level = ctx.getSource().getLevel();
        var configOpt = SixtySecondsConfigStore.current(level);
        if (configOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("当前地图无 60s 配置"));
            return 0;
        }
        configOpt.get().oceanCreaturesEnabled = enabled;
        SixtySecondsConfigStore.save(level, configOpt.get());
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "command.noellesroles.ocean.toggle_" + (enabled ? "on" : "off"))
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED), true);
        return 1;
    }

    private static int showStatus(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        var configOpt = SixtySecondsConfigStore.current(level);
        boolean enabled = configOpt.map(c -> c.oceanCreaturesEnabled).orElse(false);
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "command.noellesroles.ocean.status",
                enabled ? Component.translatable("command.noellesroles.ocean.on")
                        .withStyle(ChatFormatting.GREEN)
                        : Component.translatable("command.noellesroles.ocean.off")
                        .withStyle(ChatFormatting.RED))
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }
}
