package io.wifi.starrailexpress.mail;

import io.wifi.starrailexpress.network.NetworkHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 邮箱方块 – 玩家右键交互时打开邮箱界面。
 */
public class MailboxBlock extends Block {

    public MailboxBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            // 同步最新数据后打开邮箱界面
            MailboxComponent mailbox = MailboxComponent.KEY.get(serverPlayer);
            mailbox.sync();
            NetworkHandler.sendToClientPlayer(OpenMailboxScreenPayload.INSTANCE, serverPlayer);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
