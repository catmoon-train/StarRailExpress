package net.exmo.sre.sixtyseconds.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * 直升机撤离指令（独立于 {@code /sre:60s} 命令树），统一管理 开关/状态/手动触发/设置降落点。
 * <p>
 * 注册为 {@code /sre:60s_helicopter}，所有子命令均需 OP 2 级权限。
 * </p>
 */
public final class SixtySecondsHelicopterCommand {
    private SixtySecondsHelicopterCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("sre:60s_helicopter")
                        .requires(source -> source.hasPermission(2))
                        // /sre:60s_helicopter on  — 开启直升机
                        .then(literal("on").executes(c -> setHelicopter(c.getSource(), true)))
                        // /sre:60s_helicopter off — 关闭直升机
                        .then(literal("off").executes(c -> setHelicopter(c.getSource(), false)))
                        // /sre:60s_helicopter arrive — 手动触发直升机抵达
                        .then(literal("arrive").executes(c -> helicopterArrive(c.getSource())))
                        // /sre:60s_helicopter        — 查看状态
                        .executes(c -> showHelicopter(c.getSource()))));
        // /sre:60s_helicopter_set <x> <y> <z> — 设置降落点
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("sre:60s_helicopter_set")
                        .requires(source -> source.hasPermission(2))
                        .then(argument("x", IntegerArgumentType.integer())
                                .then(argument("y", IntegerArgumentType.integer())
                                        .then(argument("z", IntegerArgumentType.integer())
                                                .executes(context -> setHelicopterLanding(
                                                        context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "x"),
                                                        IntegerArgumentType.getInteger(context, "y"),
                                                        IntegerArgumentType.getInteger(context, "z"))))))));
    }

    /** 管理员：切换直升机撤离开关（按图配置持久化，默认开）。 */
    private static int setHelicopter(CommandSourceStack source, boolean enabled) {
        var level = source.getLevel();
        var config = net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore.current(level)
                .orElseGet(net.exmo.sre.sixtyseconds.config.SixtySecondsConfig::new);
        config.helicopterEnabled = enabled;
        net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore.save(level, config);
        source.sendSuccess(() -> Component.translatable(enabled
                ? "message.noellesroles.sixty_seconds.helicopter_enabled"
                : "message.noellesroles.sixty_seconds.helicopter_disabled")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /** 管理员：查看直升机撤离开关当前状态与降落点坐标。 */
    private static int showHelicopter(CommandSourceStack source) {
        var config = net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore.current(source.getLevel())
                .orElse(null);
        boolean enabled = config != null ? config.helicopterEnabled : true;
        String posStr;
        if (config != null && config.helicopterLandingPos != null
                && !(config.helicopterLandingPos.x == 0 && config.helicopterLandingPos.y == 0
                        && config.helicopterLandingPos.z == 0)) {
            posStr = " §7(" + config.helicopterLandingPos.x + ", " + config.helicopterLandingPos.y + ", "
                    + config.helicopterLandingPos.z + ")";
        } else {
            posStr = " §7(未设置 - 使用 /sre:60s_helicopter_set <x> <y> <z>)";
        }
        source.sendSuccess(() -> Component.translatable(enabled
                        ? "message.noellesroles.sixty_seconds.helicopter_enabled"
                        : "message.noellesroles.sixty_seconds.helicopter_disabled")
                .append(Component.literal(posStr)), false);
        return 1;
    }

    /** 管理员：设置直升机降落点坐标。 */
    private static int setHelicopterLanding(CommandSourceStack source, int x, int y, int z) {
        var level = source.getLevel();
        var config = net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore.current(level)
                .orElseGet(net.exmo.sre.sixtyseconds.config.SixtySecondsConfig::new);
        config.helicopterLandingPos = new net.exmo.sre.sixtyseconds.config.SixtySecondsConfig.Vec(x, y, z);
        net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore.save(level, config);
        source.sendSuccess(() -> Component.translatable("message.noellesroles.sixty_seconds.helicopter_set", x, y, z)
                .withStyle(ChatFormatting.GOLD), true);
        return 1;
    }

    /** 管理员：手动触发直升机抵达（立即广播撤离区位置）。 */
    private static int helicopterArrive(CommandSourceStack source) {
        var level = source.getLevel();
        if (!SixtySecondsMod.isActive(level)) {
            source.sendFailure(Component.translatable("message.noellesroles.sixty_seconds.helicopter_not_active"));
            return 0;
        }
        var config = net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore.current(level)
                .orElse(null);
        if (config == null || !config.helicopterEnabled) {
            source.sendFailure(Component.translatable("message.noellesroles.sixty_seconds.helicopter_disabled"));
            return 0;
        }
        if (config.helicopterLandingPos == null
                || config.helicopterLandingPos.toBlockPos().equals(net.minecraft.core.BlockPos.ZERO)) {
            source.sendFailure(Component.translatable("message.noellesroles.sixty_seconds.helicopter_no_landing")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        var data = net.exmo.sre.sixtyseconds.state.SixtySecondsState.get(level);
        // 如果直升机已抵达则先重置再触发（管理员可重复使用）
        if (data.helicopterArrived) {
            source.sendSuccess(() -> Component.translatable("message.noellesroles.sixty_seconds.helicopter_reset")
                    .withStyle(ChatFormatting.YELLOW), true);
        }
        net.exmo.sre.sixtyseconds.logic.SixtySecondsHelicopterEvac.arrive(level, data,
                config.helicopterLandingPos.toBlockPos());
        source.sendSuccess(() -> Component.translatable(
                "message.noellesroles.sixty_seconds.helicopter_arrive_manual",
                config.helicopterLandingPos.x, config.helicopterLandingPos.y, config.helicopterLandingPos.z)
                .withStyle(ChatFormatting.GOLD), true);
        return 1;
    }
}
