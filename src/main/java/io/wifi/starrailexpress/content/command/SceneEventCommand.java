/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import org.agmas.noellesroles.content.block.scene.BreakingBridgeBlock;
import org.agmas.noellesroles.content.block.scene.DrippingStalactiteBlock;
import org.agmas.noellesroles.scene.SceneEventManager;

/**
 * 场景方块事件触发指令 /sre:sceneevent。
 * 支持坐标/玩家参数触发各类场景事件，以及破坏任务（sabotage）状态切换。
 * 随方块批次扩展子命令。
 */
public final class SceneEventCommand {
    private SceneEventCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sre:sceneevent")
                .requires(source -> source.hasPermission(2))
                // ─── 破坏任务状态 ───
                .then(Commands.literal("sabotage")
                        .then(Commands.literal("start")
                                .executes(ctx -> startSabotage(ctx, -1))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                        .executes(ctx -> startSabotage(ctx,
                                                IntegerArgumentType.getInteger(ctx, "seconds")))))
                        .then(Commands.literal("stop")
                                .executes(SceneEventCommand::stopSabotage))
                        .then(Commands.literal("status")
                                .executes(SceneEventCommand::sabotageStatus)))
                // ─── 滴水石椎：立即坠落 ───
                .then(Commands.literal("dripstone")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> dripstone(ctx,
                                        BlockPosArgument.getLoadedBlockPos(ctx, "pos")))))
                // ─── 断桥：立即断裂 ───
                .then(Commands.literal("breakbridge")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> breakBridge(ctx,
                                        BlockPosArgument.getLoadedBlockPos(ctx, "pos")))))
                // ─── 滚石：立即召唤 ───
                .then(Commands.literal("rockfall")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> rockfall(ctx,
                                        BlockPosArgument.getLoadedBlockPos(ctx, "pos"))))));
    }

    private static int startSabotage(CommandContext<CommandSourceStack> ctx, int seconds) {
        ServerLevel level = ctx.getSource().getLevel();
        SceneEventManager.startSabotage(level, seconds < 0 ? -1 : seconds * 20);
        String msg = seconds < 0 ? "破坏任务已开启（持续至手动停止）" : "破坏任务已开启，持续 " + seconds + " 秒";
        ctx.getSource().sendSuccess(() -> Component.literal(msg), true);
        return 1;
    }

    private static int stopSabotage(CommandContext<CommandSourceStack> ctx) {
        SceneEventManager.stopSabotage(ctx.getSource().getLevel());
        ctx.getSource().sendSuccess(() -> Component.literal("破坏任务已停止"), true);
        return 1;
    }

    private static int sabotageStatus(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        boolean active = SceneEventManager.isSabotageActive(level);
        long remain = SceneEventManager.sabotageRemaining(level);
        ctx.getSource().sendSuccess(() -> Component.literal(
                active ? ("破坏任务进行中，剩余约 " + (remain / 20) + " 秒") : "当前无破坏任务"), false);
        return 1;
    }

    private static int dripstone(CommandContext<CommandSourceStack> ctx, BlockPos pos) {
        DrippingStalactiteBlock.drop(ctx.getSource().getLevel(), pos);
        ctx.getSource().sendSuccess(() -> Component.literal("已在 " + pos.toShortString() + " 触发滴水石椎坠落"), true);
        return 1;
    }

    private static int rockfall(CommandContext<CommandSourceStack> ctx, BlockPos pos) {
        ServerLevel level = ctx.getSource().getLevel();
        BlockState state = level.getBlockState(pos);
        net.minecraft.core.Direction dir = net.minecraft.core.Direction.NORTH;
        if (state.getBlock() instanceof org.agmas.noellesroles.content.block.scene.RollingStoneTriggerPlate) {
            dir = state.getValue(org.agmas.noellesroles.content.block.scene.RollingStoneTriggerPlate.FACING);
        }
        org.agmas.noellesroles.content.block.scene.RollingStoneTriggerPlate.spawnStone(level, pos, dir);
        final net.minecraft.core.Direction fd = dir;
        ctx.getSource().sendSuccess(
                () -> Component.literal("已在 " + pos.toShortString() + " 朝 " + fd + " 召唤滚石"), true);
        return 1;
    }

    private static int breakBridge(CommandContext<CommandSourceStack> ctx, BlockPos pos) {
        ServerLevel level = ctx.getSource().getLevel();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BreakingBridgeBlock) {
            level.scheduleTick(pos, state.getBlock(), 1);
            ctx.getSource().sendSuccess(() -> Component.literal("已触发 " + pos.toShortString() + " 处断桥"), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal(pos.toShortString() + " 不是断桥方块"));
        return 0;
    }
}
