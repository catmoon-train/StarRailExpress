package io.wifi.starrailexpress.command;

import com.mojang.brigadier.CommandDispatcher;
import io.wifi.starrailexpress.block.SmallDoorBlock;
import io.wifi.starrailexpress.block_entity.SmallDoorBlockEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.NotNull;

public class UpdateDoorsCommand {
    public static void register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tmm:updateDoors").requires(source -> source.hasPermission(2)).executes(context -> {
            CommandSourceStack source = context.getSource();

            BlockPos playerPos = source.getPlayer().blockPosition();
            BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
            for (int x = -250; x <= 250; x++) {
                for (int y = -10; y <= 10; y++) {
                    for (int z = -250; z <= 250; z++) {
                        mutable.set(playerPos.getX() + x, playerPos.getY() + y, playerPos.getZ() + z);
                        ServerLevel world = source.getLevel();
                        BlockState blockState = world.getBlockState(mutable);
                        if (blockState.getBlock() instanceof SmallDoorBlock && blockState.getValue(SmallDoorBlock.HALF).equals(DoubleBlockHalf.LOWER)) {
                            if (world.getBlockEntity(mutable) instanceof SmallDoorBlockEntity entity) {
                                SmallDoorBlock.toggleDoor(blockState, world, entity, mutable);
                            }
                        }
                    }
                }
            }

            source.sendSuccess(
                () -> Component.translatable("commands.sre.updatedoors")
                    .withStyle(style -> style.withColor(0x00FF00)),
                true
            );

            return 1;
        }));
    }
}
