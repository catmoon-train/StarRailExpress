package net.exmo.sre.camera;

import com.google.gson.JsonParseException;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.wifi.starrailexpress.network.PacketTracker;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * {@code /sre:camera} 命令：控制玩家的高级相机轨道。
 *
 * <pre>
 *   /sre:camera clear &lt;targets&gt;
 *   /sre:camera intro &lt;targets&gt; [durationTicks] [distance] [height]
 *   /sre:camera path  &lt;targets&gt; &lt;json&gt;
 * </pre>
 *
 * 详见 {@code docs/advanced-camera.md}。
 */
public final class AdvancedCameraCommand {

    /** 默认开场镜头：时长（tick）。 */
    public static final int DEFAULT_INTRO_DURATION = 100;
    /** 默认开场镜头：相机起点距玩家的水平距离（格）。 */
    public static final double DEFAULT_INTRO_DISTANCE = 65.0;
    /** 默认开场镜头：相机起点距玩家眼睛的高度（格）。 */
    public static final double DEFAULT_INTRO_HEIGHT = 24.0;

    private AdvancedCameraCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sre:camera")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("clear")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(AdvancedCameraCommand::executeClear)))
                .then(Commands.literal("intro")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> executeIntro(ctx, DEFAULT_INTRO_DURATION, DEFAULT_INTRO_DISTANCE,
                                        DEFAULT_INTRO_HEIGHT))
                                .then(Commands.argument("durationTicks", IntegerArgumentType.integer(1, 12000))
                                        .executes(ctx -> executeIntro(ctx,
                                                IntegerArgumentType.getInteger(ctx, "durationTicks"),
                                                DEFAULT_INTRO_DISTANCE, DEFAULT_INTRO_HEIGHT))
                                        .then(Commands.argument("distance", DoubleArgumentType.doubleArg(0.0, 256.0))
                                                .executes(ctx -> executeIntro(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "durationTicks"),
                                                        DoubleArgumentType.getDouble(ctx, "distance"),
                                                        DEFAULT_INTRO_HEIGHT))
                                                .then(Commands.argument("height", DoubleArgumentType.doubleArg(-128.0, 256.0))
                                                        .executes(ctx -> executeIntro(ctx,
                                                                IntegerArgumentType.getInteger(ctx, "durationTicks"),
                                                                DoubleArgumentType.getDouble(ctx, "distance"),
                                                                DoubleArgumentType.getDouble(ctx, "height"))))))))
                .then(Commands.literal("path")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("json", StringArgumentType.greedyString())
                                        .executes(AdvancedCameraCommand::executePath)))));
    }

    private static int executeClear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        AdvancedCameraPayload payload = AdvancedCameraPayload.clearPayload();
        for (ServerPlayer player : targets) {
            PacketTracker.sendToClient(player, payload);
        }
        ctx.getSource().sendSuccess(
                () -> Component.literal("已清除 " + targets.size() + " 名玩家的高级相机轨道。"), true);
        return targets.size();
    }

    private static int executeIntro(CommandContext<CommandSourceStack> ctx, int durationTicks, double distance,
                                    double height) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        for (ServerPlayer player : targets) {
            sendIntro(player, durationTicks, distance, height);
        }
        ctx.getSource().sendSuccess(
                () -> Component.literal("已向 " + targets.size() + " 名玩家播放开场镜头。"), true);
        return targets.size();
    }

    private static int executePath(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        String json = StringArgumentType.getString(ctx, "json");
        // 服务端先校验，避免把非法 JSON 发给客户端。
        AdvancedCameraSequence sequence;
        try {
            sequence = AdvancedCameraSequence.fromJson(json);
        } catch (JsonParseException e) {
            ctx.getSource().sendFailure(Component.literal("轨道 JSON 非法：" + e.getMessage()));
            return 0;
        }
        AdvancedCameraPayload payload = AdvancedCameraPayload.play(sequence.toJson());
        for (ServerPlayer player : targets) {
            PacketTracker.sendToClient(player, payload);
        }
        ctx.getSource().sendSuccess(
                () -> Component.literal("已向 " + targets.size() + " 名玩家播放自定义轨道。"), true);
        return targets.size();
    }

    // ==================== 编程 API ====================

    /** 构建一条"由远及近到玩家视角"的开场轨道。 */
    public static AdvancedCameraSequence buildIntro(ServerPlayer player, int durationTicks, double distance,
                                                    double height) {
        Vec3 eye = player.getEyePosition(1.0f);
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        // 玩家身后的水平方向（朝向向量取反）。
        double yawRad = Math.toRadians(yaw);
        double backX = Math.sin(yawRad);
        double backZ = -Math.cos(yawRad);
        Vec3 farPos = eye.add(backX * distance, height, backZ * distance);

        List<AdvancedCameraNode> nodes = new ArrayList<>();
        // 起点：远处俯视玩家。
        nodes.add(new AdvancedCameraNode(0, 0, farPos, eye, null, null, 0f));
        // 终点：回到玩家眼睛位置，朝玩家当前朝向（lookAt 留空，避免注视自身导致角度退化）。
        nodes.add(new AdvancedCameraNode(durationTicks, 0, eye, null, yaw, pitch, 0f));
        return new AdvancedCameraSequence(nodes, true, true, false);
    }

    /** 向单个玩家发送开场轨道。 */
    public static void sendIntro(ServerPlayer player, int durationTicks, double distance, double height) {
        AdvancedCameraSequence sequence = buildIntro(player, durationTicks, distance, height);
        PacketTracker.sendToClient(player, AdvancedCameraPayload.play(sequence.toJson()));
    }
}
