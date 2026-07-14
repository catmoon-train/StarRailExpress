package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.Commands.CommandSelection;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.agmas.noellesroles.content.block.scene.BreakingBridgeBlock;
import org.agmas.noellesroles.content.block_entity.scene.BreakingBridgeBlockEntity;

public final class BreakingAndFakeBlockCommand {

  public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess,
      CommandSelection environment) {

    var t = dispatcher.register(Commands.literal("sre:breaking_bridge")
        .requires(source -> source.hasPermission(2))
        .then(Commands.argument("pos", BlockPosArgument.blockPos())
            .then(Commands.literal("set")
                .then(Commands.literal("broken_stage")
                    .then(Commands.argument("breaking_stage", IntegerArgumentType.integer(0, 9)).executes(ctx -> {
                      final var source = ctx.getSource();
                      final var level = source.getLevel();
                      BlockPos blockPos = BlockPosArgument.getBlockPos(ctx, "pos");
                      BlockEntity entity = level.getBlockEntity(blockPos);
                      if (entity instanceof BreakingBridgeBlockEntity bbbe) {
                        bbbe.breakingStage = IntegerArgumentType.getInteger(ctx, "breaking_stage");
                        bbbe.sync();
                        final int b = bbbe.breakingStage;
                        source.sendSuccess(
                            () -> Component.translatable("block.noellesroles.breaking_bridge.tip",
                                bbbe.getBlockState().getBlock().getName(), b),
                            true);
                        return 1;
                      }
                      return 1;
                    })))
                .then(Commands.literal("time")
                    .then(Commands.argument("breaking_time", IntegerArgumentType.integer(1))
                        .then(Commands.argument("restoring_time", IntegerArgumentType.integer(1)).executes(ctx -> {
                          final var source = ctx.getSource();
                          final var level = source.getLevel();
                          BlockPos blockPos = BlockPosArgument.getBlockPos(ctx, "pos");
                          BlockEntity entity = level.getBlockEntity(blockPos);
                          if (entity instanceof BreakingBridgeBlockEntity bbbe) {
                            bbbe.breakingTime = IntegerArgumentType.getInteger(ctx, "breaking_time");
                            bbbe.restoringTime = IntegerArgumentType.getInteger(ctx, "restoring_time");
                            bbbe.sync();
                            source.sendSuccess(
                                () -> Component.translatable("block.noellesroles.breaking_bridge.info_tool",
                                    bbbe.getBlockState().getBlock().getName(),
                                    bbbe.displayState == null ? Component.literal("Default")
                                        : bbbe.displayState.getBlock().getName(),
                                    bbbe.breakingStage, bbbe.breakingTime, bbbe.restoringTime),
                                true);
                          }
                          return 1;
                        }))))
                .then(Commands.literal("block_state")
                    .then(Commands.argument("block", BlockStateArgument.block(registryAccess)).executes(ctx -> {
                      final var source = ctx.getSource();
                      final var level = source.getLevel();
                      BlockPos blockPos = BlockPosArgument.getBlockPos(ctx, "pos");
                      BlockInput input = BlockStateArgument.getBlock(ctx, "block");
                      BlockState state = input.getState();
                      {
                        BlockEntity entity = level.getBlockEntity(blockPos);
                        if (entity instanceof BreakingBridgeBlockEntity bbbe) {
                          bbbe.setDisplayState(state);
                          source.sendSuccess(() -> Component.translatable("block.noellesroles.breaking_bridge.set_to",
                              state.getBlock().getName()), true);
                          return 1;
                        }
                      }
                      return 0;
                    })))
                .then(Commands.literal("from")
                    .then(Commands.argument("from_pos", BlockPosArgument.blockPos()).executes(ctx -> {
                      final var source = ctx.getSource();
                      final var level = source.getLevel();
                      BlockPos blockPos = BlockPosArgument.getBlockPos(ctx, "pos");
                      BlockPos fromBlockPos = BlockPosArgument.getBlockPos(ctx, "from_pos");
                      BlockState state = level.getBlockState(fromBlockPos);
                      BlockEntity fromEntity = level.getBlockEntity(fromBlockPos);
                      CompoundTag fromEntityTag = null;
                      if (fromEntity instanceof BreakingBridgeBlockEntity bbbe) {
                        if (bbbe.displayState != null) {
                          state = bbbe.displayState;
                          fromEntityTag = bbbe.blockEntityTag;
                          fromEntity = null;
                        }
                      }
                      if (fromEntity != null) {
                        fromEntityTag = fromEntity.saveCustomOnly(level.registryAccess());
                      }
                      final var finalState = state;
                      {
                        BlockEntity entity = level.getBlockEntity(blockPos);
                        if (entity instanceof BreakingBridgeBlockEntity bbbe) {
                          bbbe.blockEntityTag = fromEntityTag;
                          bbbe.setDisplayState(finalState);
                          source.sendSuccess(() -> Component.translatable("block.noellesroles.breaking_bridge.set_to",
                              finalState.getBlock().getName()), true);
                          return 1;
                        }
                      }
                      return 1;
                    }))))

            .then(Commands.literal("info").executes(ctx -> {
              final var source = ctx.getSource();
              final var level = source.getLevel();
              BlockPos blockPos = BlockPosArgument.getBlockPos(ctx, "pos");
              BlockState state = level.getBlockState(blockPos);
              if (state.getBlock() instanceof BreakingBridgeBlock b) {
                BlockEntity entity = level.getBlockEntity(blockPos);
                if (entity instanceof BreakingBridgeBlockEntity bbbe) {
                  source.sendSuccess(() -> Component.translatable("block.noellesroles.breaking_bridge.info_tool",
                      bbbe.getBlockState().getBlock().getName(),
                      bbbe.displayState == null ? b.getName()
                          : bbbe.displayState.getBlock().getName(),
                      bbbe.breakingStage, bbbe.breakingTime, bbbe.restoringTime),
                      true);
                  return 1;
                }
              }
              return 0;
            }))));
    dispatcher.register(Commands.literal("sre:fake_block").redirect(t));
  }

}
