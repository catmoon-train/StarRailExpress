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
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.ServerTaskInfoClasses;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * 区域整块复制：{@code /sre:clone <from_pos1> <from_pos2> <to_pos1>}。
 *
 * <p>
 * 把两个角点围出的区域整体复制到 {@code to_pos1}（作为新区域的最小角），方块状态与方块实体
 * （含自定义方块记录的 id）都会一起搬过去。
 *
 * <p>
 * 复制不是一次性完成的：按体积切成若干分块丢进 {@link GameUtils#serverTaskQueue}，
 * 每 tick 处理一个分块（同 {@code FullTrainResetTask} 的推进方式），源与目标重叠时改用
 * 整体快照再写，保证语义与原版 {@code /clone} 一致。
 */
public final class SRECloneCommand {

    private SRECloneCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sre:clone")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("from_pos1", BlockPosArgument.blockPos())
                        .then(Commands.argument("from_pos2", BlockPosArgument.blockPos())
                                .then(Commands.argument("to_pos1", BlockPosArgument.blockPos())
                                        .executes(SRECloneCommand::clone)))));
    }

    private static int clone(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos corner1 = BlockPosArgument.getBlockPos(context, "from_pos1");
        BlockPos corner2 = BlockPosArgument.getBlockPos(context, "from_pos2");
        BlockPos destination = BlockPosArgument.getBlockPos(context, "to_pos1");

        BoundingBox sourceBox = BoundingBox.fromCorners(corner1, corner2);
        long volume = (long) sourceBox.getXSpan() * sourceBox.getYSpan() * sourceBox.getZSpan();
        BlockPos offset = new BlockPos(
                destination.getX() - sourceBox.minX(),
                destination.getY() - sourceBox.minY(),
                destination.getZ() - sourceBox.minZ());
        BoundingBox destBox = sourceBox.moved(offset.getX(), offset.getY(), offset.getZ());
        boolean overlapping = destBox.intersects(sourceBox);

        int limit = overlapping
                ? ServerTaskInfoClasses.CloneRegionTask.MAX_VOLUME_OVERLAP
                : ServerTaskInfoClasses.CloneRegionTask.MAX_VOLUME;
        if (volume > limit) {
            source.sendFailure(Component.translatable(
                    overlapping ? "sre.custom_content.clone.error.overlap_too_large"
                            : "sre.custom_content.clone.error.too_large",
                    volume, limit));
            return 0;
        }
        if (volume <= 0L) {
            source.sendFailure(Component.translatable("sre.custom_content.clone.error.empty"));
            return 0;
        }

        GameUtils.serverTaskQueue.add(new ServerTaskInfoClasses.CloneRegionTask(level, sourceBox, offset, source));
        source.sendSuccess(() -> Component.translatable(
                overlapping ? "sre.custom_content.clone.queued_overlap" : "sre.custom_content.clone.queued",
                volume, destination.toShortString()).withStyle(style -> style.withColor(0x55FF55)), true);
        SRE.LOGGER.info("[Clone] Queued {} blocks from {} to {} by {}",
                volume, sourceBox, destination.toShortString(), source.getTextName());
        return (int) Math.min(Integer.MAX_VALUE, volume);
    }
}
